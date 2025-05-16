package de.darksmp;

import java.util.Iterator;
import java.util.Optional;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import net.kyori.adventure.text.Component;

public class FallbackRouterUtil {

    public static void attemptConnection(Player player, Iterator<String> targets, ProxyServer server, FallbackLogger logger) {
        if (!targets.hasNext()) {
            logger.warn("Keine erreichbaren Fallback-Server. Spieler wird getrennt.");
            player.disconnect(Component.text("All fallback servers are offline."));
            return;
        }

        String targetName = targets.next();
        Optional<RegisteredServer> serverOpt = server.getServer(targetName);

        if (serverOpt.isEmpty()) {
            logger.warn("Server \"" + targetName + "\" existiert nicht. Versuche nächsten Fallback.");
            attemptConnection(player, targets, server, logger);
            return;
        }

        RegisteredServer target = serverOpt.get();
        target.ping().whenComplete((ping, error) -> {
            if (error != null) {
                logger.warn("Ping zu \"" + targetName + "\" fehlgeschlagen: " + error.getMessage());
                attemptConnection(player, targets, server, logger);
            } else if (player.isActive()) {
                player.createConnectionRequest(target).connect().whenComplete((result, err) -> {
                    if (!result.isSuccessful()) {
                        logger.warn("Verbindung zu \"" + targetName + "\" fehlgeschlagen: " + (err != null ? err.getMessage() : "unbekannt"));
                        attemptConnection(player, targets, server, logger);
                    } else {
                        logger.info("Spieler \"" + player.getUsername() + "\" erfolgreich verbunden mit \"" + targetName + "\".");
                    }
                });
            }
        });
    }
}
