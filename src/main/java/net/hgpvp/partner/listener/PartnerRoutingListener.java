package net.hgpvp.partner.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.hgpvp.partner.routing.HgServerSelector;
import net.hgpvp.partner.routing.LobbyServerSelector;
import net.hgpvp.partner.session.PartnerSession;
import net.hgpvp.partner.session.PartnerSessionManager;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.Optional;

public final class PartnerRoutingListener {

    private final PartnerSessionManager sessionManager;
    private final HgServerSelector hgSelector;
    private final LobbyServerSelector lobbySelector;
    private final Logger logger;

    public PartnerRoutingListener(PartnerSessionManager sessionManager, HgServerSelector hgSelector,
                                  LobbyServerSelector lobbySelector, Logger logger) {
        this.sessionManager = sessionManager;
        this.hgSelector = hgSelector;
        this.lobbySelector = lobbySelector;
        this.logger = logger;
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();
        Optional<PartnerSession> sessionOpt = sessionManager.getSession(player.getUniqueId());

        if (sessionOpt.isPresent()) {
            // Partner player -> route to first non-started HG server with lowest ID
            try {
                RegisteredServer bestHg = hgSelector.findBestHgServer().join();
                if (bestHg != null) {
                    event.setInitialServer(bestHg);
                    if (logger != null) {
                        logger.info("Joueur partenaire {} routé à la connexion vers {}", player.getUsername(), bestHg.getServerInfo().getName());
                    }
                    return;
                }
            } catch (Exception ex) {
                if (logger != null) {
                    logger.error("Erreur sélection HG initial pour partenaire {}: {}", player.getUsername(), ex.getMessage());
                }
            }
        }

        // Normal player -> route to first available non-full lobby (lobby0, lobby1, etc.)
        try {
            RegisteredServer bestLobby = lobbySelector.findBestLobbyServer().join();
            if (bestLobby != null) {
                event.setInitialServer(bestLobby);
                if (logger != null) {
                    logger.debug("Joueur {} routé vers le premier lobby disponible non-plein: {}",
                            player.getUsername(), bestLobby.getServerInfo().getName());
                }
            }
        } catch (Exception ex) {
            if (logger != null) {
                logger.error("Erreur sélection lobby initial pour {}: {}", player.getUsername(), ex.getMessage());
            }
        }
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        Optional<PartnerSession> sessionOpt = sessionManager.getSession(player.getUniqueId());

        if (sessionOpt.isPresent()) {
            PartnerSession session = sessionOpt.get();
            RegisteredServer targetServer = event.getResult().getServer().orElse(event.getOriginalServer());
            String targetName = targetServer.getServerInfo().getName();

            if (isLobbyServer(targetName)) {
                // Partner player typed /hub or was redirected to lobby -> transfer back to partner
                if (player.getCurrentServer().isPresent() && !isLobbyServer(player.getCurrentServer().get().getServer().getServerInfo().getName())) {
                    event.setResult(ServerPreConnectEvent.ServerResult.denied());
                    transferPlayerBack(player, session, "commande hub/lobby");
                }
            }
        }
    }

    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        Player player = event.getPlayer();
        Optional<PartnerSession> sessionOpt = sessionManager.getSession(player.getUniqueId());

        if (sessionOpt.isPresent()) {
            PartnerSession session = sessionOpt.get();
            if (logger != null) {
                logger.info("Joueur partenaire {} expulsé/fin de partie sur {}, re-transfert vers {}:{}",
                        player.getUsername(), event.getServer().getServerInfo().getName(), session.returnHost(), session.returnPort());
            }
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(Component.empty()));
            transferPlayerBack(player, session, "fin de partie/expulsion");
            return;
        }

        // Normal player kicked from game -> fallback to best non-full lobby
        try {
            RegisteredServer bestLobby = lobbySelector.findBestLobbyServer().join();
            if (bestLobby != null) {
                event.setResult(KickedFromServerEvent.RedirectPlayer.create(bestLobby));
            }
        } catch (Exception ignored) {}
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        sessionManager.removeSession(event.getPlayer().getUniqueId());
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
