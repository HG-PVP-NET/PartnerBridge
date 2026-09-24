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

    public void startStatusChecker(Object plugin, int intervalSeconds) {
        if (proxy == null || plugin == null) return;
        int interval = intervalSeconds > 0 ? intervalSeconds : 5;
        refreshAllStatuses();
        proxy.getScheduler().buildTask(plugin, this::refreshAllStatuses)
                .repeat(Duration.ofSeconds(interval))
                .schedule();
    }

    public void refreshAllStatuses() {
        if (proxy == null) return;
        for (String name : candidateServerNames) {
            proxy.getServer(name).ifPresent(server -> {
                PingOptions options = PingOptions.builder().timeout(Duration.ofMillis(600)).build();
                server.ping(options).whenComplete((ping, throwable) -> {
                    if (throwable == null && ping != null) {
                        int players = ping.getPlayers().map(ServerPing.Players::getOnline).orElse(0);
                        int max = ping.getPlayers().map(ServerPing.Players::getMax).orElse(500);
                        statusCache.put(name, new LobbyStatus(server, true, players, max));
                    } else {
                        statusCache.put(name, new LobbyStatus(server, false, 0, 0));
                    }
                });
            });
        }
    }

    public RegisteredServer findBestLobbyServerInstant() {
        if (proxy == null) {
            return null;
        }

        List<RegisteredServer> availableServers = new ArrayList<>();
        for (String name : candidateServerNames) {
            proxy.getServer(name).ifPresent(availableServers::add);
        }

        if (availableServers.isEmpty()) {
            return proxy.getServer(fallbackServerName).orElse(null);
        }

        // 1. Find the FIRST available lobby that is cached as online and NOT full (sorted by ID ascending)
        for (String name : candidateServerNames) {
            LobbyStatus status = statusCache.get(name);
            if (status != null && status.online() && !status.isFull(fullThreshold)) {
                if (logger != null) {
                    logger.debug("Lobby non-plein sélectionné: {} (joueurs: {}/{})",
                            status.server().getServerInfo().getName(), status.players(), status.maxPlayers());
                }
                return status.server();
            }
        }

        // 2. If all online lobbies are full, pick the online lobby with the fewest players
        LobbyStatus leastPopulated = null;
        for (String name : candidateServerNames) {
            LobbyStatus status = statusCache.get(name);
            if (status != null && status.online()) {
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
        return proxy.getServer(fallbackServerName).orElse(availableServers.get(0));
    }

    public CompletableFuture<RegisteredServer> findBestLobbyServer() {
        return CompletableFuture.completedFuture(findBestLobbyServerInstant());
    }

    public List<LobbyStatus> getLobbyStatuses() {
        List<LobbyStatus> list = new ArrayList<>();
        if (proxy == null) return list;

        for (String name : candidateServerNames) {
            Optional<RegisteredServer> srvOpt = proxy.getServer(name);
            if (srvOpt.isPresent()) {
                RegisteredServer server = srvOpt.get();
                LobbyStatus status = statusCache.get(name);
                if (status != null) {
                    list.add(status);
                } else {
                    list.add(new LobbyStatus(server, false, 0, 0));
                }
            }
        }
        return list;
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
