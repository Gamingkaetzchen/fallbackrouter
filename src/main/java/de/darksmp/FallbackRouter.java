package de.darksmp;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.inject.Inject;

import org.slf4j.Logger;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.Component;

@Plugin(id = "fallbackrouter", name = "FallbackRouter", version = "1.2.5")
public class FallbackRouter {

    private final ProxyServer server;
    private final Path dataDirectory;
    private volatile Settings settings;

    private record Settings(FallbackRules rules, Messages messages, KickReasonPolicy kickReasons) {}
    private final Logger logger;
    private final Map<UUID, FallbackAttempt> fallbackAttempts = new ConcurrentHashMap<>();

    private record FallbackAttempt(List<String> remaining, Set<String> tried,
                                   String pendingServer, Component exhaustedMessage) {}

    // Guice 7 ignores javax.inject.Inject; use Guice's annotation without
    // replacing Velocity-provided dependencies with an empty constructor.
    @Inject
    public FallbackRouter(ProxyServer server, @DataDirectory Path dataDirectory, Logger logger) throws Exception {
        this.server = server;
        this.logger = logger;

        this.dataDirectory = dataDirectory;
        new LanguageFileExporter(dataDirectory).exportIfMissing();
        reloadConfiguration();
    }

    private synchronized void reloadConfiguration() throws Exception {
        FallbackRouterConfig.LoadedConfig loaded = FallbackRouterConfig.load(dataDirectory);
        Messages messages = new Messages(dataDirectory, loaded.language());
        // Publish once, only after all parsing and message loading succeed. Readers
        // must never observe new routes paired with the previous language settings.
        settings = new Settings(loaded.rules(), messages, loaded.kickReasons());
        logger.info("[FallbackRouter] " + messages.get("status.loaded", Map.of("language", loaded.language())));
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        var manager = server.getCommandManager();
        manager.register(manager.metaBuilder("fallbackrouter").plugin(this).build(), new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                if (!hasPermission(invocation)) {
                    invocation.source().sendMessage(Component.text(settings.messages().getRaw("command.no_permission")));
                    return;
                }
                if (invocation.arguments().length != 1 || !invocation.arguments()[0].equalsIgnoreCase("reload")) {
                    invocation.source().sendMessage(Component.text(settings.messages().getRaw("command.usage")));
                    return;
                }
                try {
                    reloadConfiguration();
                    invocation.source().sendMessage(Component.text(settings.messages().getRaw("command.reloaded")));
                } catch (Exception e) {
                    String message = settings.messages().getRaw("command.reload_failed");
                    logger.error("[FallbackRouter] " + message, e);
                    invocation.source().sendMessage(Component.text(message));
                }
            }

            @Override
            public boolean hasPermission(Invocation invocation) {
                return invocation.source() instanceof ConsoleCommandSource
                        || invocation.source().hasPermission("fallbackrouter.reload");
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                if (!hasPermission(invocation)) return List.of();
                String[] arguments = invocation.arguments();
                return arguments.length == 0 || (arguments.length == 1
                        && "reload".startsWith(arguments[0].toLowerCase(java.util.Locale.ROOT)))
                        ? List.of("reload") : List.of();
            }
        });
    }

    @Subscribe(order = PostOrder.LAST)
    public void onKickedFromServer(KickedFromServerEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (!player.isActive()) {
            fallbackAttempts.remove(playerId);
            return;
        }
        String source = event.getServer().getServerInfo().getName();
        Settings snapshot = settings;
        fallbackAttempts.compute(playerId, (id, previous) -> {
            FallbackAttempt attempt = previous;
            boolean continuing = attempt != null && source.equalsIgnoreCase(attempt.pendingServer());
            if (!continuing && event.kickedDuringServerConnect() && player.getCurrentServer().isPresent()) return null;
            if ((!continuing || snapshot.kickReasons().configured())
                    && !snapshot.kickReasons().allows(event.getServerKickReason())) {
                // Returning unchanged here could leave Velocity's own redirect active.
                // Enforce a configured punishment exclusion before any recovery send.
                if (snapshot.kickReasons().configured()) {
                    event.setResult(KickedFromServerEvent.DisconnectPlayer.create(event.getServerKickReason()
                            .orElseGet(() -> Component.text(snapshot.messages().getRaw("fallback.no_reason")))));
                }
                return null;
            }
            if (!continuing) {
                List<String> targets = snapshot.rules().getFallbacks(source);
                if (targets.isEmpty()) return null; // Preserve Velocity's result for unhandled sources.
                attempt = new FallbackAttempt(List.copyOf(targets), Set.of(source.toLowerCase(Locale.ROOT)),
                        source, Component.text(snapshot.messages().get("fallback.all_offline", Map.of())));
            }
            return redirectNext(event, attempt);
        });
    }

    // A failed destination belongs to the original source's chain. Starting a
    // new chain for that destination can loop between catch-all queue servers.
    private FallbackAttempt redirectNext(KickedFromServerEvent event, FallbackAttempt attempt) {
        Set<String> tried = new HashSet<>(attempt.tried());
        for (int index = 0; index < attempt.remaining().size(); index++) {
            String targetName = attempt.remaining().get(index);
            var target = server.getServer(targetName);
            if (target.isEmpty()) continue;
            String registeredName = target.get().getServerInfo().getName();
            if (!tried.add(registeredName.toLowerCase(Locale.ROOT))) continue;

            // Velocity consumes this result before making any default fallback connection.
            // If the connection fails, its next kick event continues this same source's chain.
            event.setResult(KickedFromServerEvent.RedirectPlayer.create(target.get(), Component.empty()));
            logger.info("[FallbackRouter] " + settings.messages().get("fallback.redirecting",
                    Map.of("player", event.getPlayer().getUsername(), "target", registeredName)));
            return new FallbackAttempt(List.copyOf(attempt.remaining().subList(index + 1, attempt.remaining().size())),
                    Set.copyOf(tried), registeredName, attempt.exhaustedMessage());
        }
        event.setResult(KickedFromServerEvent.DisconnectPlayer.create(attempt.exhaustedMessage()));
        return null;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        fallbackAttempts.remove(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        fallbackAttempts.remove(event.getPlayer().getUniqueId());
    }
}
