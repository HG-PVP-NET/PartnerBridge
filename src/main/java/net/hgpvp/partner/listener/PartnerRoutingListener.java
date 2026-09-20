package net.hgpvp.partner.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.hgpvp.partner.routing.HgServerSelector;
import net.hgpvp.partner.session.PartnerSession;
import net.hgpvp.partner.session.PartnerSessionManager;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.Optional;

public final class PartnerRoutingListener {

    private final PartnerSessionManager sessionManager;
    private final HgServerSelector hgSelector;
    private final Logger logger;

    public PartnerRoutingListener(PartnerSessionManager sessionManager, HgServerSelector hgSelector, Logger logger) {
        this.sessionManager = sessionManager;
        this.hgSelector = hgSelector;
        this.logger = logger;
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        Optional<PartnerSession> sessionOpt = sessionManager.getSession(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }

        PartnerSession session = sessionOpt.get();
        RegisteredServer targetServer = event.getResult().getServer().orElse(event.getOriginalServer());
        String targetName = targetServer.getServerInfo().getName();

        if (isLobbyServer(targetName)) {
            // Player is already on an HG server and typed /hub or is redirected to lobby: transfer back to partner
            if (player.getCurrentServer().isPresent() && !isLobbyServer(player.getCurrentServer().get().getServer().getServerInfo().getName())) {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                transferPlayerBack(player, session, "commande hub/lobby");
            }
        }
    }

    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        Player player = event.getPlayer();
        Optional<PartnerSession> sessionOpt = sessionManager.getSession(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }

        PartnerSession session = sessionOpt.get();
        if (logger != null) {
            logger.info("Joueur partenaire {} expulsé/fin de partie sur {}, re-transfert vers {}:{}",
                    player.getUsername(), event.getServer().getServerInfo().getName(), session.returnHost(), session.returnPort());
        }

        // Prevent Velocity from redirecting the player to lobby
        event.setResult(KickedFromServerEvent.DisconnectPlayer.create(Component.empty()));
        transferPlayerBack(player, session, "fin de partie/expulsion");
    }

    private void transferPlayerBack(Player player, PartnerSession session, String reason) {
        try {
            if (logger != null) {
                logger.info("Transfert retour du joueur partenaire {} vers {} ({}:{}) [Raison: {}]",
                        player.getUsername(), session.originNetwork(), session.returnHost(), session.returnPort(), reason);
            }
            InetSocketAddress address = session.returnSocketAddress();
            player.transferToHost(address);
        } catch (Exception ex) {
            if (logger != null) {
                logger.error("Échec du transfert retour pour {}: {}", player.getUsername(), ex.getMessage(), ex);
            }
        }
    }

    private boolean isLobbyServer(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.startsWith("lobby") || lower.equals("hub") || lower.equals("limbo");
    }
}
