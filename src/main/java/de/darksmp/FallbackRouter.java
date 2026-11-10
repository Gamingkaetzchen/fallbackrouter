package de.darksmp;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.inject.Inject;

import org.slf4j.Logger;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.Component;

@Plugin(id = "fallbackrouter", name = "FallbackRouter", version = "1.2.3")
public class FallbackRouter {

    private final ProxyServer server;
    private final Path dataDirectory;
    private volatile Settings settings;

    private record Settings(FallbackRules rules, Messages messages) {}
    private final Logger logger;

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
        settings = new Settings(loaded.rules(), messages);
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

    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        String reason = event.getServerKickReason().toString().toLowerCase();
        if (!reason.contains("server closed") && !reason.contains("disconnected") && !reason.contains("timed out")) {
            return;
        }

        tryFallback(event.getPlayer(), event.getServer().getServerInfo().getName());
    }

    private void tryFallback(Player player, String fromServer) {
        if (!player.isActive()) return;
        List<String> fallbackTargets = settings.rules().getFallbacks(fromServer);
        server.getScheduler().buildTask(this, () -> {
            attemptConnection(player, fallbackTargets.iterator());
        }).delay(2, TimeUnit.SECONDS).schedule();
    }

    private void attemptConnection(Player player, Iterator<String> targets) {
        if (!targets.hasNext()) {
            player.disconnect(Component.text(settings.messages().get("fallback.all_offline", Map.of())));
            return;
        }

        String targetName = targets.next();
        server.getServer(targetName).ifPresentOrElse(server -> {
            server.ping().whenComplete((ping, error) -> {
                if (error != null) {
                    attemptConnection(player, targets);
                } else if (player.isActive()) {
                    player.createConnectionRequest(server).connect().whenComplete((result, err) -> {
                        if (!result.isSuccessful()) {
                            attemptConnection(player, targets);
                        } else {
                            logger.info("[FallbackRouter] " + player.getUsername() + " -> " + targetName);
                        }
                    });
                }
            });
        }, () -> attemptConnection(player, targets));
    }
}
