package net.hgpvp.partner;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.hgpvp.partner.command.LobbyCommand;
import net.hgpvp.partner.command.PartnerCommand;
import net.hgpvp.partner.listener.PartnerCookieListener;
import net.hgpvp.partner.listener.PartnerRoutingListener;
import net.hgpvp.partner.routing.HgServerSelector;
import net.hgpvp.partner.routing.LobbyServerSelector;
import net.hgpvp.partner.session.PartnerSessionManager;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

@Plugin(
        id = "partnerbridge",
        name = "PartnerBridge",
        version = "1.0.0",
        description = "Minecraft 1.20.5+ partner network cookie & transfer gateway and dynamic lobby load balancer for HG-PvP",
        authors = {"HG-PvP"}
)
public final class PartnerBridgePlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private PartnerSessionManager sessionManager;
    private HgServerSelector hgSelector;
    private LobbyServerSelector lobbySelector;

    private String publicReturnHost = "play.hg-pvp.net";
    private int publicReturnPort = 25565;
    private String cmfrHost = "play.craftmybox.fr";
    private int cmfrPort = 25565;
    private List<String> hgServers = List.of("hg0", "hg1", "hg2", "hg3", "hg4");
    private String hgFallback = "hg0";
    private List<String> lobbyServers = List.of("lobby0", "lobby1", "lobby2", "lobby3", "lobby4");
    private String lobbyFallback = "lobby0";
    private int lobbyFullThreshold = 50;

    @Inject
    public PartnerBridgePlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig();

        this.sessionManager = new PartnerSessionManager(logger, java.util.Set.of(
                publicReturnHost.toLowerCase(),
                "play.hg-pvp.net",
                "hg-pvp.net",
                "beta.hg-pvp.net",
                "crack.hg-pvp.net",
                "localhost",
                "127.0.0.1"
        ));
        this.hgSelector = new HgServerSelector(proxy, hgServers, hgFallback, logger);
        this.lobbySelector = new LobbyServerSelector(proxy, lobbyServers, lobbyFallback, lobbyFullThreshold, logger);

        EventManager eventManager = proxy.getEventManager();
        eventManager.register(this, new PartnerCookieListener(proxy, sessionManager, hgSelector, logger));
        eventManager.register(this, new PartnerRoutingListener(sessionManager, hgSelector, lobbySelector, logger));

        CommandManager commandManager = proxy.getCommandManager();

        // Partner outbound commands
        PartnerCommand partnerCmd = new PartnerCommand(
                sessionManager,
                publicReturnHost,
                publicReturnPort,
                cmfrHost,
                cmfrPort,
                logger
        );
        commandManager.register(commandManager.metaBuilder("cmfr").plugin(this).build(), partnerCmd);
        commandManager.register(commandManager.metaBuilder("partner").plugin(this).build(), partnerCmd);
        commandManager.register(commandManager.metaBuilder("partenaire").plugin(this).build(), partnerCmd);

        // Unified lobby / hub commands
        LobbyCommand lobbyCmd = new LobbyCommand(sessionManager, lobbySelector, logger);
        commandManager.register(commandManager.metaBuilder("lobby").plugin(this).build(), lobbyCmd);
        commandManager.register(commandManager.metaBuilder("hub").plugin(this).build(), lobbyCmd);
        commandManager.register(commandManager.metaBuilder("l").plugin(this).build(), lobbyCmd);

        logger.info("PartnerBridge initialisé avec succès ! (Lobbies: {}, Seuil Plein: {} joueurs, Serveurs HG: {})",
                lobbyServers, lobbyFullThreshold, hgServers);
    }

    private void loadConfig() {
        File configDir = dataDirectory.toFile();
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        File configFile = new File(configDir, "config.properties");
        Properties properties = new Properties();

        if (configFile.exists()) {
            try (FileInputStream in = new FileInputStream(configFile)) {
                properties.load(in);
            } catch (IOException e) {
                logger.warn("Impossible de lire config.properties pour PartnerBridge: {}", e.getMessage());
            }
        }

        // Environment variables have highest priority
        String envReturn = System.getenv().getOrDefault("SPRINGYWIRE_RETURN_HOST",
                properties.getProperty("return-host", "play.hg-pvp.net:25565"));

        if (envReturn.contains(":")) {
            String[] parts = envReturn.split(":", 2);
            this.publicReturnHost = parts[0].trim();
            try {
                this.publicReturnPort = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {}
        } else {
            this.publicReturnHost = envReturn.trim();
            this.publicReturnPort = 25565;
        }

        this.cmfrHost = System.getenv().getOrDefault("CMFR_HOST",
                properties.getProperty("cmfr-host", "play.craftmybox.fr"));

        try {
            this.cmfrPort = Integer.parseInt(System.getenv().getOrDefault("CMFR_PORT",
                    properties.getProperty("cmfr-port", "25565")));
        } catch (NumberFormatException ignored) {}

        String serversStr = System.getenv().getOrDefault("HG_SERVERS",
                properties.getProperty("hg-servers", "hg0,hg1,hg2,hg3,hg4"));
        this.hgServers = Arrays.stream(serversStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        this.hgFallback = System.getenv().getOrDefault("HG_FALLBACK",
                properties.getProperty("hg-fallback", "hg0")).trim();

        String lobbiesStr = System.getenv().getOrDefault("LOBBY_SERVERS",
                properties.getProperty("lobby-servers", "lobby0,lobby1,lobby2,lobby3,lobby4"));
        this.lobbyServers = Arrays.stream(lobbiesStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        this.lobbyFallback = System.getenv().getOrDefault("LOBBY_FALLBACK",
                properties.getProperty("lobby-fallback", "lobby0")).trim();

        try {
            this.lobbyFullThreshold = Integer.parseInt(System.getenv().getOrDefault("LOBBY_FULL_THRESHOLD",
                    properties.getProperty("lobby-full-threshold", "50")));
        } catch (NumberFormatException ignored) {}

        // Save default configuration file if absent
        if (!configFile.exists()) {
            properties.setProperty("return-host", publicReturnHost + ":" + publicReturnPort);
            properties.setProperty("cmfr-host", cmfrHost);
            properties.setProperty("cmfr-port", String.valueOf(cmfrPort));
            properties.setProperty("hg-servers", String.join(",", hgServers));
            properties.setProperty("hg-fallback", hgFallback);
            properties.setProperty("lobby-servers", String.join(",", lobbyServers));
            properties.setProperty("lobby-fallback", lobbyFallback);
            properties.setProperty("lobby-full-threshold", String.valueOf(lobbyFullThreshold));
            try (FileOutputStream out = new FileOutputStream(configFile)) {
                properties.store(out, "PartnerBridge Configuration");
            } catch (IOException e) {
                logger.warn("Impossible de sauvegarder le config.properties par défaut: {}", e.getMessage());
            }
        }
    }
}
