package net.hgpvp.partner.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.hgpvp.partner.routing.LobbyServerSelector;
import net.hgpvp.partner.session.PartnerSession;
import net.hgpvp.partner.session.PartnerSessionManager;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

public final class LobbyCommand implements SimpleCommand {

    private final ProxyServer proxy;
    private final PartnerSessionManager sessionManager;
    private final LobbyServerSelector lobbySelector;
    private final Logger logger;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public LobbyCommand(ProxyServer proxy, PartnerSessionManager sessionManager, LobbyServerSelector lobbySelector, Logger logger) {
        this.proxy = proxy;
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

        String[] args = invocation.arguments();

        // 1. /lobby list
        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            sendLobbyList(player);
            return;
        }

        // 2. /lobby <serverName> (e.g. /lobby lobby0)
        if (args.length > 0 && proxy != null) {
            String targetName = args[0].toLowerCase();
            Optional<RegisteredServer> targetServerOpt = proxy.getServer(targetName);
            if (targetServerOpt.isPresent()) {
                RegisteredServer target = targetServerOpt.get();
                Optional<ServerConnection> current = player.getCurrentServer();
                if (current.isPresent() && current.get().getServer().equals(target)) {
                    player.sendMessage(mm.deserialize("<gold>Tu es déjà connecté à ce lobby (<yellow>" + target.getServerInfo().getName() + "</yellow>).</gold>"));
                    return;
                }
                player.sendMessage(mm.deserialize("<green>Téléportation vers le lobby <yellow>" + target.getServerInfo().getName() + "</yellow>...</green>"));
                player.createConnectionRequest(target).connect();
                return;
            }
        }

        // 3. Partner player: transfer back to origin network
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

        // 4. Normal player: instant route to first available non-full lobby from cache (0ms delay)
        RegisteredServer targetLobby = lobbySelector.findBestLobbyServerInstant();
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
    }

    private void sendLobbyList(Player player) {
        player.sendMessage(mm.deserialize("<color:#24ff91>Lobbies :</color>"));
        List<LobbyServerSelector.LobbyStatus> statuses = lobbySelector.getLobbyStatuses();
        int threshold = lobbySelector.getFullThreshold();

        for (LobbyServerSelector.LobbyStatus status : statuses) {
            String name = status.server().getServerInfo().getName();
            int current = status.players();
            int max = threshold;

            String message;
            if (!status.online()) {
                message = "<red><hover:show_text:'<red>Serveur hors ligne</red>'><click:run_command:'/server " + name + "'>[" + name + "]</click></hover></red>";
            } else if (status.isFull(threshold)) {
                message = "<gold><hover:show_text:'<gold>Serveur full<newline>" + current + "/" + max + " Players</gold>'><click:run_command:'/server " + name + "'>[" + name + "]</click></hover></gold>";
            } else {
                message = "<green><hover:show_text:'<green>Serveur en ligne<newline>" + current + "/" + max + " Players</green>'><click:run_command:'/server " + name + "'>[" + name + "]</click></hover></green>";
            }
            player.sendMessage(mm.deserialize(message));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return List.of("list");
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> suggestions = new java.util.ArrayList<>();
            if ("list".startsWith(prefix)) {
                suggestions.add("list");
            }
            for (String lobby : lobbySelector.getCandidateServerNames()) {
                if (lobby.toLowerCase().startsWith(prefix)) {
                    suggestions.add(lobby);
                }
            }
            return suggestions;
        }
        return List.of();
    }
}
