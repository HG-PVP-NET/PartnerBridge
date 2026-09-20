package net.hgpvp.partner.routing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LobbyServerSelectorTest {

    @Test
    void testExtractNumericId() {
        LobbyServerSelector selector = new LobbyServerSelector(null, List.of("lobby0", "lobby1", "lobby2"), "lobby0", 50, null);

        assertThat(selector.extractNumericId("lobby0")).isEqualTo(0);
        assertThat(selector.extractNumericId("lobby1")).isEqualTo(1);
        assertThat(selector.extractNumericId("lobby-2")).isEqualTo(2);
        assertThat(selector.extractNumericId("lobby10")).isEqualTo(10);
        assertThat(selector.extractNumericId("hub")).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void testLobbyStatusIsFull() {
        LobbyServerSelector.LobbyStatus nonFull = new LobbyServerSelector.LobbyStatus(null, true, 20, 100);
        assertThat(nonFull.isFull(50)).isFalse();

        LobbyServerSelector.LobbyStatus fullByThreshold = new LobbyServerSelector.LobbyStatus(null, true, 50, 100);
        assertThat(fullByThreshold.isFull(50)).isTrue();

        LobbyServerSelector.LobbyStatus fullByMax = new LobbyServerSelector.LobbyStatus(null, true, 30, 30);
        assertThat(fullByMax.isFull(50)).isTrue();

        LobbyServerSelector.LobbyStatus overThreshold = new LobbyServerSelector.LobbyStatus(null, true, 60, 100);
        assertThat(overThreshold.isFull(50)).isTrue();
    }
}
