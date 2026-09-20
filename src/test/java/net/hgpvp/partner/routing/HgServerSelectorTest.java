package net.hgpvp.partner.routing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HgServerSelectorTest {

    @Test
    void testExtractNumericId() {
        HgServerSelector selector = new HgServerSelector(null, List.of("hg0", "hg1", "hg2"), "hg0", null);

        assertThat(selector.extractNumericId("hg0")).isEqualTo(0);
        assertThat(selector.extractNumericId("hg1")).isEqualTo(1);
        assertThat(selector.extractNumericId("hg-2")).isEqualTo(2);
        assertThat(selector.extractNumericId("hg10")).isEqualTo(10);
        assertThat(selector.extractNumericId("lobby")).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void testIsGameInProgress() {
        assertThat(HgServerSelector.isGameInProgress("§4Partie en cours.")).isTrue();
        assertThat(HgServerSelector.isGameInProgress("Game in progress.")).isTrue();
        assertThat(HgServerSelector.isGameInProgress("En cours")).isTrue();
        assertThat(HgServerSelector.isGameInProgress("Serveur en cours de reboot")).isTrue();
        assertThat(HgServerSelector.isGameInProgress("Terminé")).isTrue();

        assertThat(HgServerSelector.isGameInProgress("§e45 secondes")).isFalse();
        assertThat(HgServerSelector.isGameInProgress("§f1 minute")).isFalse();
        assertThat(HgServerSelector.isGameInProgress("Game starting in 30 seconds")).isFalse();
        assertThat(HgServerSelector.isGameInProgress("En attente de joueurs")).isFalse();
        assertThat(HgServerSelector.isGameInProgress("")).isFalse();
        assertThat(HgServerSelector.isGameInProgress(null)).isFalse();
    }
}
