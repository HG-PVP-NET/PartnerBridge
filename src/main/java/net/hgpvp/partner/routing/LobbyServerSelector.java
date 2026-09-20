package net.hgpvp.partner.routing;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LobbyServerSelector {

    private static final Pattern SERVER_ID_PATTERN = Pattern.compile("\\d+");
    private final ProxyServer proxy;
    private final List<String> candidateServerNames;
    private final String fallbackServerName;
    private final int fullThreshold;
    private final Logger logger;
    private final Map<String, LobbyStatus> statusCache = new ConcurrentHashMap<>();

    public LobbyServerSelector(ProxyServer proxy, List<String> candidateServerNames, String fallbackServerName,
                               int fullThreshold, Logger logger) {
        this.proxy = proxy;
        this.candidateServerNames = candidateServerNames != null && !candidateServerNames.isEmpty()
                ? new ArrayList<>(candidateServerNames)
                : List.of("lobby0", "lobby1", "lobby2", "lobby3", "lobby4");
        this.fallbackServerName = fallbackServerName != null && !fallbackServerName.isBlank()
                ? fallbackServerName.trim()
                : "lobby0";
        this.fullThreshold = fullThreshold > 0 ? fullThreshold : 50;
        this.logger = logger;

        // Sort candidates by numeric ID ascending (lobby0 < lobby1 < lobby2 ...)
        this.candidateServerNames.sort(Comparator.comparingInt(this::extractNumericId));
    }

    public CompletableFuture<RegisteredServer> findBestLobbyServer() {
        if (proxy == null) {
            return CompletableFuture.completedFuture(null);
        }

        List<RegisteredServer> availableServers = new ArrayList<>();
        for (String name : candidateServerNames) {
            proxy.getServer(name).ifPresent(availableServers::add);
        }

        if (availableServers.isEmpty()) {
            return CompletableFuture.completedFuture(proxy.getServer(fallbackServerName).orElse(null));
        }

        // Ping all candidate lobby servers in parallel
        List<CompletableFuture<LobbyStatus>> pingFutures = availableServers.stream()
                .map(this::pingServer)
                .toList();

        return CompletableFuture.allOf(pingFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<LobbyStatus> results = pingFutures.stream()
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

                    // 1. Find the FIRST available lobby that is online and NOT full (sorted by ID ascending)
                    for (LobbyStatus status : results) {
                        if (status.online() && !status.isFull(fullThreshold)) {
                            if (logger != null) {
                                logger.debug("Premier lobby disponible et non-plein sélectionné: {} (joueurs: {}/{})",
                                        status.server().getServerInfo().getName(), status.players(), status.maxPlayers());
                            }
                            return status.server();
                        }
                    }

                    // 2. If all online lobbies are full, pick the online lobby with the fewest players
                    LobbyStatus leastPopulated = null;
                    for (LobbyStatus status : results) {
                        if (status.online()) {
                            if (leastPopulated == null || status.players() < leastPopulated.players()) {
                                leastPopulated = status;
                            }
                        }
                    }

                    if (leastPopulated != null) {
                        if (logger != null) {
                            logger.warn("Tous les lobbies sont pleins (>= {} joueurs). Sélection du lobby le moins rempli: {} ({}/{} joueurs)",
                                    fullThreshold, leastPopulated.server().getServerInfo().getName(),
                                    leastPopulated.players(), leastPopulated.maxPlayers());
                        }
                        return leastPopulated.server();
                    }

                    // 3. Fallback to configured default lobby
                    if (logger != null) {
                        logger.warn("Aucun lobby joignable, utilisation du fallback: {}", fallbackServerName);
                    }
                    return proxy.getServer(fallbackServerName).orElse(availableServers.get(0));
                });
    }

    private CompletableFuture<LobbyStatus> pingServer(RegisteredServer server) {
        String name = server.getServerInfo().getName();
        PingOptions options = PingOptions.builder().timeout(Duration.ofMillis(800)).build();

        return server.ping(options)
                .thenApply(ping -> {
                    int players = ping.getPlayers().map(ServerPing.Players::getOnline).orElse(0);
                    int max = ping.getPlayers().map(ServerPing.Players::getMax).orElse(500);
                    LobbyStatus status = new LobbyStatus(server, true, players, max);
                    statusCache.put(name, status);
                    return status;
                })
                .exceptionally(ex -> {
                    LobbyStatus status = new LobbyStatus(server, false, 0, 0);
                    statusCache.put(name, status);
                    return status;
                });
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

    public List<String> getCandidateServerNames() {
        return Collections.unmodifiableList(candidateServerNames);
    }

    public int getFullThreshold() {
        return fullThreshold;
    }

    public record LobbyStatus(
            RegisteredServer server,
            boolean online,
            int players,
            int maxPlayers
    ) {
        public boolean isFull(int threshold) {
            return players >= threshold || (maxPlayers > 0 && players >= maxPlayers);
        }
    }
}
