package net.hgpvp.partner.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PartnerSessionManagerTest {

    private PartnerSessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new PartnerSessionManager(null);
    }

    @Test
    void testParseJsonPayloadWithReturn() {
        String json = "{\"return\":\"play.craftmybox.fr:25565\",\"game\":\"hungergames\",\"network\":\"CMFR\"}";
        Optional<PartnerSession> session = manager.parseCookiePayload(json.getBytes(StandardCharsets.UTF_8));

        assertThat(session).isPresent();
        assertThat(session.get().returnHost()).isEqualTo("play.craftmybox.fr");
        assertThat(session.get().returnPort()).isEqualTo(25565);
        assertThat(session.get().originNetwork()).isEqualTo("CMFR");
        assertThat(session.get().targetGame()).isEqualTo("hungergames");
    }

    @Test
    void testParseJsonPayloadWithHostAndPort() {
        String json = "{\"host\":\"cmfr.craftmybox.fr\",\"port\":25566,\"sender\":\"Springywire\"}";
        Optional<PartnerSession> session = manager.parseCookiePayload(json.getBytes(StandardCharsets.UTF_8));

        assertThat(session).isPresent();
        assertThat(session.get().returnHost()).isEqualTo("cmfr.craftmybox.fr");
        assertThat(session.get().returnPort()).isEqualTo(25566);
        assertThat(session.get().originNetwork()).isEqualTo("Springywire");
    }

    @Test
    void testParsePlainTextPayload() {
        String plain = "return: play.craftmybox.fr:25565";
        Optional<PartnerSession> session = manager.parseCookiePayload(plain.getBytes(StandardCharsets.UTF_8));

        assertThat(session).isPresent();
        assertThat(session.get().returnHost()).isEqualTo("play.craftmybox.fr");
        assertThat(session.get().returnPort()).isEqualTo(25565);
    }

    @Test
    void testParseHostPortWithGameSuffix() {
        String input = "play.craftmybox.fr:25565;pillars-of-fortune";
        Optional<PartnerSession> session = manager.parseCookiePayload(input.getBytes(StandardCharsets.UTF_8));

        assertThat(session).isPresent();
        assertThat(session.get().returnHost()).isEqualTo("play.craftmybox.fr");
        assertThat(session.get().returnPort()).isEqualTo(25565);
        assertThat(session.get().targetGame()).isEqualTo("pillars-of-fortune");
    }

    @Test
    void testParseEmptyOrNull() {
        assertThat(manager.parseCookiePayload(null)).isEmpty();
        assertThat(manager.parseCookiePayload(new byte[0])).isEmpty();
        assertThat(manager.parseCookiePayload("   ".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    void testSessionLifecycle() {
        UUID uuid = UUID.randomUUID();
        PartnerSession session = new PartnerSession("play.craftmybox.fr", 25565, "CMFR", "hg");

        manager.registerSession(uuid, session);
        assertThat(manager.hasSession(uuid)).isTrue();
        assertThat(manager.getSession(uuid)).contains(session);

        Optional<PartnerSession> removed = manager.removeSession(uuid);
        assertThat(removed).contains(session);
        assertThat(manager.hasSession(uuid)).isFalse();
    }

    @Test
    void testCreateCookiePayload() {
        byte[] payload = manager.createCookiePayload("play.hg-pvp.net", 25565, "pillars-of-fortune", "HG-PvP");
        Optional<PartnerSession> parsed = manager.parseCookiePayload(payload);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().returnHost()).isEqualTo("play.hg-pvp.net");
        assertThat(parsed.get().returnPort()).isEqualTo(25565);
        assertThat(parsed.get().targetGame()).isEqualTo("pillars-of-fortune");
        assertThat(parsed.get().originNetwork()).isEqualTo("HG-PvP");
    }
}
