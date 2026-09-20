package net.hgpvp.partner.session;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Objects;

public final class PartnerSession {

    private final String returnHost;
    private final int returnPort;
    private final String originNetwork;
    private final String targetGame;
    private final Instant joinedAt;

    public PartnerSession(String returnHost, int returnPort, String originNetwork, String targetGame) {
        this.returnHost = Objects.requireNonNull(returnHost, "returnHost").trim();
        this.returnPort = returnPort > 0 ? returnPort : 25565;
        this.originNetwork = originNetwork != null && !originNetwork.isBlank() ? originNetwork.trim() : "Partner";
        this.targetGame = targetGame != null && !targetGame.isBlank() ? targetGame.trim() : "hungergames";
        this.joinedAt = Instant.now();
    }

    public String returnHost() {
        return returnHost;
    }

    public int returnPort() {
        return returnPort;
    }

    public String originNetwork() {
        return originNetwork;
    }

    public String targetGame() {
        return targetGame;
    }

    public Instant joinedAt() {
        return joinedAt;
    }

    public InetSocketAddress returnSocketAddress() {
        return new InetSocketAddress(returnHost, returnPort);
    }

    @Override
    public String toString() {
        return "PartnerSession{" +
                "returnHost='" + returnHost + '\'' +
                ", returnPort=" + returnPort +
                ", originNetwork='" + originNetwork + '\'' +
                ", targetGame='" + targetGame + '\'' +
                ", joinedAt=" + joinedAt +
                '}';
    }
}
