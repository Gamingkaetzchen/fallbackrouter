package de.darksmp;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.slf4j.Logger;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.Component;

@Plugin(id = "fallbackrouter", name = "FallbackRouter", version = "1.5")
public class FallbackRouter {

    private final ProxyServer server;
    private final Messages messages;
    private final Logger logger;
    private final Map<String, List<String>> fallbackMap;

    @Inject
    public FallbackRouter(ProxyServer server, @DataDirectory Path dataDirectory, Logger logger) throws Exception {
        this.server = server;
        this.logger = logger;

        // 🔁 Sprachdateien aus JAR extrahieren (falls sie noch nicht vorhanden sind)
        new LanguageFileExporter(dataDirectory).exportIfMissing();

        // ⚙️ Config und Sprache laden
        this.fallbackMap = FallbackRouterConfig.load(dataDirectory);
        this.messages = new Messages(FallbackRouterConfig.language);

        logger.info("[FallbackRouter] Loaded with language: " + FallbackRouterConfig.language);
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
        List<String> fallbackTargets = fallbackMap.getOrDefault(fromServer, List.of());
        server.getScheduler().buildTask(this, () -> {
            attemptConnection(player, fallbackTargets.iterator());
        }).delay(2, TimeUnit.SECONDS).schedule();
    }

    private void attemptConnection(Player player, Iterator<String> targets) {
        if (!targets.hasNext()) {
            player.disconnect(Component.text(messages.get("fallback.all_offline", Map.of())));
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
