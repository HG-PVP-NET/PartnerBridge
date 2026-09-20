package net.hgpvp.partner.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.hgpvp.partner.routing.LobbyServerSelector;
import net.hgpvp.partner.session.PartnerSession;
import net.hgpvp.partner.session.PartnerSessionManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

public final class LobbyCommand implements SimpleCommand {

    private final PartnerSessionManager sessionManager;
    private final LobbyServerSelector lobbySelector;
    private final Logger logger;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public LobbyCommand(PartnerSessionManager sessionManager, LobbyServerSelector lobbySelector, Logger logger) {
        this.sessionManager = sessionManager;
        this.lobbySelector = lobbySelector;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(mm.deserialize("<red>Cette commande est réservée aux joueurs.</red>"));
            return;
        }

        // 1. Partner player: transfer back to origin network
        Optional<PartnerSession> sessionOpt = sessionManager.getSession(player.getUniqueId());
        if (sessionOpt.isPresent()) {
            PartnerSession session = sessionOpt.get();
            player.sendMessage(mm.deserialize("<gold>Retour vers le serveur partenaire <yellow>" + session.originNetwork() + "</yellow>...</gold>"));
            try {
                player.transferToHost(session.returnSocketAddress());
            } catch (Exception ex) {
                player.sendMessage(mm.deserialize("<red>Échec du transfert retour : " + ex.getMessage() + "</red>"));
            }
            return;
        }

        // 2. Normal player: route to first available non-full lobby
        lobbySelector.findBestLobbyServer().thenAccept(targetLobby -> {
            if (targetLobby == null) {
                player.sendMessage(mm.deserialize("<red>Aucun serveur Lobby n'est actuellement disponible.</red>"));
                return;
            }

            Optional<ServerConnection> current = player.getCurrentServer();
            if (current.isPresent() && current.get().getServer().equals(targetLobby)) {
                player.sendMessage(mm.deserialize("<gold>Tu es déjà connecté à ce lobby (<yellow>" + targetLobby.getServerInfo().getName() + "</yellow>).</gold>"));
                return;
            }

            player.sendMessage(mm.deserialize("<green>Téléportation vers le lobby <yellow>" + targetLobby.getServerInfo().getName() + "</yellow>...</green>"));
            player.createConnectionRequest(targetLobby).connect();
        });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }
}
