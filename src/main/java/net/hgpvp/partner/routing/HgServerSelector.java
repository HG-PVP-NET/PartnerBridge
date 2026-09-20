package net.hgpvp.partner.routing;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HgServerSelector {

    private static final Pattern SERVER_ID_PATTERN = Pattern.compile("\\d+");
    private final ProxyServer proxy;
    private final List<String> candidateServerNames;
    private final String fallbackServerName;
    private final Logger logger;

    public HgServerSelector(ProxyServer proxy, List<String> candidateServerNames, String fallbackServerName, Logger logger) {
        this.proxy = proxy;
        this.candidateServerNames = candidateServerNames != null && !candidateServerNames.isEmpty()
                ? new ArrayList<>(candidateServerNames)
                : List.of("hg0", "hg1", "hg2", "hg3", "hg4");
        this.fallbackServerName = fallbackServerName != null && !fallbackServerName.isBlank()
                ? fallbackServerName.trim()
                : "hg0";
        this.logger = logger;

        // Sort candidates by numeric index ascending (hg0 < hg1 < hg2 ...)
        this.candidateServerNames.sort(Comparator.comparingInt(this::extractNumericId));
    }

    public CompletableFuture<RegisteredServer> findBestHgServer() {
        List<RegisteredServer> availableServers = new ArrayList<>();
        for (String name : candidateServerNames) {
            proxy.getServer(name).ifPresent(availableServers::add);
        }

        if (availableServers.isEmpty()) {
            return CompletableFuture.completedFuture(proxy.getServer(fallbackServerName).orElse(null));
        }

        // Ping all candidate servers in parallel
        List<CompletableFuture<ServerCandidateStatus>> pingFutures = availableServers.stream()
                .map(this::pingServer)
                .toList();

        return CompletableFuture.allOf(pingFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<ServerCandidateStatus> results = pingFutures.stream()
                            .map(f -> {
                                try {
                                    return f.join();
                                } catch (Exception ignored) {
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .sorted(Comparator.comparingInt(s -> extractNumericId(s.server().getServerInfo().getName())))
                            .toList();

                    // Find first online server that has NOT started yet
                    for (ServerCandidateStatus status : results) {
                        if (status.online() && !status.gameInProgress()) {
                            if (logger != null) {
                                logger.info("Serveur HG éligible sélectionné pour partenaire: {} (motd: '{}', joueurs: {}/{})",
                                        status.server().getServerInfo().getName(), status.motd(), status.players(), status.maxPlayers());
                            }
                            return status.server();
                        }
                    }

                    // Fallback if all active servers are in progress: lowest ID online server, or default fallback
                    for (ServerCandidateStatus status : results) {
                        if (status.online()) {
                            if (logger != null) {
                                logger.info("Tous les serveurs HG sont en cours, fallback sur le serveur actif: {}",
                                        status.server().getServerInfo().getName());
                            }
                            return status.server();
                        }
                    }

                    if (logger != null) {
                        logger.warn("Aucun serveur HG joignable, utilisation du fallback configuré: {}", fallbackServerName);
                    }
                    return proxy.getServer(fallbackServerName).orElse(availableServers.get(0));
                });
    }

    private CompletableFuture<ServerCandidateStatus> pingServer(RegisteredServer server) {
        String name = server.getServerInfo().getName();
        PingOptions options = PingOptions.builder().timeout(Duration.ofMillis(1200)).build();

        return server.ping(options)
                .thenApply(ping -> {
                    String motd = extractMotdText(ping);
                    boolean inProgress = isGameInProgress(motd);
                    int players = ping.getPlayers().map(ServerPing.Players::getOnline).orElse(0);
                    int max = ping.getPlayers().map(ServerPing.Players::getMax).orElse(0);
                    return new ServerCandidateStatus(server, true, inProgress, players, max, motd);
                })
                .exceptionally(ex -> new ServerCandidateStatus(server, false, true, 0, 0, "OFFLINE"));
    }

    public static boolean isGameInProgress(String motd) {
        if (motd == null || motd.isBlank()) return false;
        String lower = motd.toLowerCase(Locale.ROOT);
        return lower.contains("partie en cours")
                || lower.contains("game in progress")
                || lower.contains("en cours")
                || lower.contains("terminé")
                || lower.contains("reboot");
    }

    private String extractMotdText(ServerPing ping) {
        if (ping == null || ping.getDescriptionComponent() == null) return "";
        try {
            return PlainTextComponentSerializer.plainText().serialize(ping.getDescriptionComponent());
        } catch (Exception e) {
            return ping.getDescriptionComponent().toString();
        }
    }

    public int extractNumericId(String serverName) {
        if (serverName == null) return Integer.MAX_VALUE;
        Matcher matcher = SERVER_ID_PATTERN.matcher(serverName);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException ignored) {}
        }
        return Integer.MAX_VALUE;
    }

    public record ServerCandidateStatus(
            RegisteredServer server,
            boolean online,
            boolean gameInProgress,
            int players,
            int maxPlayers,
            String motd
    ) {}
}
