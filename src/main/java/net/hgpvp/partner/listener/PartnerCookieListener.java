package net.hgpvp.partner.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.CookieReceiveEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.hgpvp.partner.routing.HgServerSelector;
import net.hgpvp.partner.session.PartnerSession;
import net.hgpvp.partner.session.PartnerSessionManager;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PartnerCookieListener {

    public static final List<Key> PARTNER_COOKIE_KEYS = List.of(
            Key.key("springywire", "partner_ticket"),
            Key.key("springywire", "ticket"),
            Key.key("springywire", "partner"),
            Key.key("springywire", "data"),
            Key.key("springywire", "transfer"),
            Key.key("springywire", "return"),
            Key.key("partner", "data"),
            Key.key("partner", "return"),
            Key.key("partner", "transfer"),
            Key.key("partner", "ticket"),
            Key.key("cmfr", "data"),
            Key.key("cmfr", "partner"),
            Key.key("hgpvp", "partner"),
            Key.key("hgpvp", "transfer"),
            Key.key("hgpvp", "data")
    );

    private final ProxyServer proxy;
    private final PartnerSessionManager sessionManager;
    private final HgServerSelector hgSelector;
    private final Logger logger;
    private final Set<UUID> requestedPlayers = ConcurrentHashMap.newKeySet();

    public PartnerCookieListener(ProxyServer proxy, PartnerSessionManager sessionManager, HgServerSelector hgSelector, Logger logger) {
        this.proxy = proxy;
        this.sessionManager = sessionManager;
        this.hgSelector = hgSelector;
        this.logger = logger;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        requestCookies(event.getPlayer());
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        requestCookies(player);
    }

    private void requestCookies(Player player) {
        if (player.getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
            return;
        }

        if (!requestedPlayers.add(player.getUniqueId())) {
            return;
        }

        for (Key key : PARTNER_COOKIE_KEYS) {
            try {
                player.requestCookie(key);
            } catch (Exception ex) {
                if (logger != null) {
                    logger.debug("Erreur lors de la requête de cookie {} pour {}: {}", key, player.getUsername(), ex.getMessage());
                }
            }
        }
    }

    @Subscribe
    public void onCookieReceive(CookieReceiveEvent event) {
        Player player = event.getPlayer();
        byte[] data = event.getOriginalData();
        if (data == null || data.length == 0) {
            return;
        }

        Optional<PartnerSession> parsed = sessionManager.parseCookiePayload(data);
        if (parsed.isEmpty()) {
            return;
        }

        PartnerSession session = parsed.get();
        sessionManager.registerSession(player.getUniqueId(), session);

        if (logger != null) {
            logger.info("Joueur partenaire {} reçu depuis {} ({}:{}) pour le jeu {}",
                    player.getUsername(), session.originNetwork(), session.returnHost(), session.returnPort(), session.targetGame());
        }

        // Check if player is on a lobby or not yet on an HG server
        Optional<RegisteredServer> currentServer = player.getCurrentServer().map(com.velocitypowered.api.proxy.ServerConnection::getServer);
        boolean needsHgRedirect = currentServer.isEmpty() || isLobbyServer(currentServer.get().getServerInfo().getName());

        if (needsHgRedirect) {
            hgSelector.findBestHgServer().thenAccept(bestHg -> {
                if (bestHg != null) {
                    if (logger != null) {
                        logger.info("Routage du joueur partenaire {} vers {}", player.getUsername(), bestHg.getServerInfo().getName());
                    }
                    player.createConnectionRequest(bestHg).connect();
                }
            });
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        requestedPlayers.remove(event.getPlayer().getUniqueId());
        sessionManager.removeSession(event.getPlayer().getUniqueId());
    }

    private boolean isLobbyServer(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.startsWith("lobby") || lower.equals("hub") || lower.equals("limbo");
    }
}
