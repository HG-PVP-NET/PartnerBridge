package net.hgpvp.partner.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import net.hgpvp.partner.listener.PartnerCookieListener;
import net.hgpvp.partner.session.PartnerSessionManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.List;

public final class PartnerCommand implements SimpleCommand {

    private static final List<String> CMFR_GAMES = List.of(
            "pillars-of-fortune",
            "sheepwars",
            "skywars",
            "uhc-mineshaft",
            "cache-cache",
            "dimensions",
            "totem",
            "ploufwars",
            "build-battle",
            "fightclub"
    );

    private final PartnerSessionManager sessionManager;
    private final String publicReturnHost;
    private final int publicReturnPort;
    private final String cmfrHost;
    private final int cmfrPort;
    private final Logger logger;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public PartnerCommand(PartnerSessionManager sessionManager, String publicReturnHost, int publicReturnPort,
                          String cmfrHost, int cmfrPort, Logger logger) {
        this.sessionManager = sessionManager;
        this.publicReturnHost = publicReturnHost != null && !publicReturnHost.isBlank() ? publicReturnHost : "play.hg-pvp.net";
        this.publicReturnPort = publicReturnPort > 0 ? publicReturnPort : 25565;
        this.cmfrHost = cmfrHost != null && !cmfrHost.isBlank() ? cmfrHost : "cmfr.skoice.net";
        this.cmfrPort = cmfrPort > 0 ? cmfrPort : 25565;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(mm.deserialize("<red>Cette commande est réservée aux joueurs.</red>"));
            return;
        }

        if (player.getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
            player.sendMessage(mm.deserialize("<red>Le transfert partenaire nécessite Minecraft 1.20.5 ou supérieur.</red>"));
            return;
        }

        String[] args = invocation.arguments();
        String game = (args.length > 0 && !args[0].isBlank()) ? args[0].toLowerCase() : "pillars-of-fortune";

        player.sendMessage(mm.deserialize("<gold>Transfert vers le réseau partenaire <yellow>CMFR</yellow> pour <aqua>" + game + "</aqua>...</gold>"));

        byte[] payload = sessionManager.createCookiePayload(publicReturnHost, publicReturnPort, game, "HG-PvP");

        // Store cookie across all common partner keys
        for (Key key : PartnerCookieListener.PARTNER_COOKIE_KEYS) {
            try {
                player.storeCookie(key, payload);
            } catch (Exception ignored) {}
        }

        String targetHost = this.cmfrHost;
        int targetPort = this.cmfrPort;
        if (targetHost != null && targetHost.contains(":")) {
            String[] parts = targetHost.split(":", 2);
            targetHost = parts[0].trim();
            try {
                targetPort = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {}
        }

        try {
            if (logger != null) {
                logger.info("Transfert sortant du joueur {} vers CMFR ({}:{}) pour {}",
                        player.getUsername(), targetHost, targetPort, game);
            }
            player.transferToHost(new InetSocketAddress(targetHost, targetPort));
        } catch (Exception ex) {
            player.sendMessage(mm.deserialize("<red>Échec du transfert vers le serveur partenaire : " + ex.getMessage() + "</red>"));
            if (logger != null) {
                logger.error("Erreur transfert sortant pour {}: {}", player.getUsername(), ex.getMessage(), ex);
            }
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase() : "";
            return CMFR_GAMES.stream().filter(g -> g.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
