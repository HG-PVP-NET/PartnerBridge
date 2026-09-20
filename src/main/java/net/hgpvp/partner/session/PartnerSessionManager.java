package net.hgpvp.partner.session;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PartnerSessionManager {

    private final Map<UUID, PartnerSession> sessions = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private final Logger logger;
    private final Set<String> localHosts;

    public PartnerSessionManager(Logger logger) {
        this(logger, Set.of("play.hg-pvp.net", "hg-pvp.net", "beta.hg-pvp.net", "crack.hg-pvp.net", "localhost", "127.0.0.1"));
    }

    public PartnerSessionManager(Logger logger, Set<String> localHosts) {
        this.logger = logger;
        this.localHosts = localHosts != null ? localHosts : Set.of();
    }

    public void registerSession(UUID uuid, PartnerSession session) {
        if (isLocalHost(session.returnHost())) {
            if (logger != null) {
                logger.debug("Cookie de retour local détecté ({}) pour {}, joueur considéré comme de retour sur HG-PvP.",
                        session.returnHost(), uuid);
            }
            return;
        }

        sessions.put(uuid, session);
        if (logger != null) {
            logger.info("Session partenaire enregistrée pour {}: {} (retour: {}:{})",
                    uuid, session.originNetwork(), session.returnHost(), session.returnPort());
        }
    }

    public boolean isLocalHost(String host) {
        if (host == null || host.isBlank()) return true;
        String clean = host.trim().toLowerCase();
        for (String local : localHosts) {
            if (clean.equalsIgnoreCase(local) || clean.endsWith("." + local)) {
                return true;
            }
        }
        return false;
    }

    public Optional<PartnerSession> getSession(UUID uuid) {
        return Optional.ofNullable(sessions.get(uuid));
    }

    public Optional<PartnerSession> removeSession(UUID uuid) {
        PartnerSession removed = sessions.remove(uuid);
        if (removed != null && logger != null) {
            logger.info("Session partenaire terminée pour {}", uuid);
        }
        return Optional.ofNullable(removed);
    }

    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public Optional<PartnerSession> parseCookiePayload(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return Optional.empty();
        }

        String raw = new String(payload, StandardCharsets.UTF_8).trim();
        if (raw.isEmpty()) {
            return Optional.empty();
        }

        // 1. Try parsing JSON format
        if (raw.startsWith("{") && raw.endsWith("}")) {
            try {
                JsonObject obj = gson.fromJson(raw, JsonObject.class);
                String returnStr = null;
                if (obj.has("return")) {
                    returnStr = obj.get("return").getAsString();
                } else if (obj.has("returnHost")) {
                    String host = obj.get("returnHost").getAsString();
                    int port = obj.has("returnPort") ? obj.get("returnPort").getAsInt() : 25565;
                    returnStr = host + ":" + port;
                } else if (obj.has("host")) {
                    String host = obj.get("host").getAsString();
                    int port = obj.has("port") ? obj.get("port").getAsInt() : 25565;
                    returnStr = host + ":" + port;
                }

                if (returnStr != null && !returnStr.isBlank()) {
                    String game = obj.has("game") ? obj.get("game").getAsString() : "hungergames";
                    String network = obj.has("network") ? obj.get("network").getAsString()
                            : (obj.has("sender") ? obj.get("sender").getAsString() : "Partner");

                    String[] parts = parseHostPort(returnStr);
                    return Optional.of(new PartnerSession(parts[0], Integer.parseInt(parts[1]), network, game));
                }
            } catch (Exception ex) {
                if (logger != null) {
                    logger.debug("Tentative de parsing JSON de cookie échouée: {}", ex.getMessage());
                }
            }
        }

        // 2. Try parsing plain string: "return: host:port" or "host:port" or "host:port;game"
        try {
            String sanitized = raw;
            if (sanitized.toLowerCase().startsWith("return:")) {
                sanitized = sanitized.substring("return:".length()).trim();
            }

            String targetGame = "hungergames";
            String network = "Partner";

            if (sanitized.contains(";")) {
                String[] segments = sanitized.split(";", 2);
                sanitized = segments[0].trim();
                if (segments.length > 1 && !segments[1].isBlank()) {
                    targetGame = segments[1].trim();
                }
            }

            String[] hostPort = parseHostPort(sanitized);
            if (hostPort != null) {
                return Optional.of(new PartnerSession(hostPort[0], Integer.parseInt(hostPort[1]), network, targetGame));
            }
        } catch (Exception ex) {
            if (logger != null) {
                logger.debug("Tentative de parsing texte de cookie échouée: {}", ex.getMessage());
            }
        }

        return Optional.empty();
    }

    public byte[] createCookiePayload(String returnHost, int returnPort, String targetGame, String networkName) {
        JsonObject json = new JsonObject();
        json.addProperty("return", returnHost + ":" + returnPort);
        json.addProperty("game", targetGame != null ? targetGame : "hungergames");
        json.addProperty("network", networkName != null ? networkName : "HG-PvP");
        json.addProperty("returnEnabled", true);
        return gson.toJson(json).getBytes(StandardCharsets.UTF_8);
    }

    private String[] parseHostPort(String input) {
        if (input == null || input.isBlank()) return null;
        String clean = input.trim();
        if (clean.startsWith("[") && clean.contains("]")) {
            // IPv6
            int closeBracket = clean.indexOf(']');
            String host = clean.substring(1, closeBracket);
            int port = 25565;
            if (clean.length() > closeBracket + 1 && clean.charAt(closeBracket + 1) == ':') {
                port = Integer.parseInt(clean.substring(closeBracket + 2));
            }
            return new String[]{host, String.valueOf(port)};
        }

        if (clean.contains(":")) {
            String[] parts = clean.split(":", 2);
            int port = 25565;
            try {
                port = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {}
            return new String[]{parts[0].trim(), String.valueOf(port)};
        }

        return new String[]{clean, "25565"};
    }
}
