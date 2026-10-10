package net.hgpvp.partner.listener;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import net.hgpvp.partner.routing.HgServerSelector;
import net.hgpvp.partner.session.PartnerSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class PartnerCookieListenerTest {

    private Player player;
    private PartnerCookieListener listener;

    @BeforeEach
    void setUp() {
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getProtocolVersion()).thenReturn(ProtocolVersion.MINECRAFT_1_21);

        listener = new PartnerCookieListener(
                null,
                new PartnerSessionManager(null),
                new HgServerSelector(null, java.util.List.of("hg0"), "hg0", null),
                null
        );
    }

    @Test
    void requestsCookiesOnlyOnceAfterBackendConnectionCompletes() {
        listener.requestCookies(player);
        listener.requestCookies(player);

        for (var key : PartnerCookieListener.PARTNER_COOKIE_KEYS) {
            verify(player, times(1)).requestCookie(key);
        }
    }

    @Test
    void cookieScanIsRegisteredOnlyAfterBackendConnection() {
        var subscribedEventTypes = Arrays.stream(PartnerCookieListener.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(com.velocitypowered.api.event.Subscribe.class))
                .filter(method -> method.getParameterCount() == 1)
                .map(method -> method.getParameterTypes()[0])
                .toList();

        assertThat(subscribedEventTypes)
                .contains(ServerPostConnectEvent.class)
                .doesNotContain(PostLoginEvent.class, ServerPreConnectEvent.class);
    }

    @Test
    void partnerCookiesAreMarkedHandledToPreventBackendKicks() {
        com.velocitypowered.api.event.player.CookieReceiveEvent event =
                new com.velocitypowered.api.event.player.CookieReceiveEvent(
                        player,
                        PartnerCookieListener.PARTNER_COOKIE_KEYS.get(0),
                        new byte[0]
                );

        listener.onCookieReceive(event);

        assertThat(event.getResult().isAllowed()).isFalse();
    }

    @Test
    void unknownCookiesAreAlsoMarkedHandledToProtectPaperBackend() {
        net.kyori.adventure.key.Key unknownKey = net.kyori.adventure.key.Key.key("custom", "unknown_cookie");
        com.velocitypowered.api.event.player.CookieReceiveEvent event =
                new com.velocitypowered.api.event.player.CookieReceiveEvent(
                        player,
                        unknownKey,
                        new byte[0]
                );

        listener.onCookieReceive(event);

        assertThat(event.getResult().isAllowed()).isFalse();
    }

    @Test
    void ignoresBedrockAndFloodgatePlayers() {
        Player bedrockPlayer = mock(Player.class);
        when(bedrockPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(bedrockPlayer.getProtocolVersion()).thenReturn(ProtocolVersion.MINECRAFT_1_21);
        when(bedrockPlayer.getUsername()).thenReturn(".BedrockPlayer");

        listener.requestCookies(bedrockPlayer);

        for (var key : PartnerCookieListener.PARTNER_COOKIE_KEYS) {
            verify(bedrockPlayer, times(0)).requestCookie(key);
        }
    }
}
