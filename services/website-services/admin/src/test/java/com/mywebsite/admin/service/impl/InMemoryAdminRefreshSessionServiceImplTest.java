package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.admin.dto.RotateRefreshDto;
import com.mywebsite.auth.token.RefreshTokenGenerator;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryAdminRefreshSessionServiceImplTest {

    @Test
    void rotateThenReplayRevokesSession() {
        InMemoryAdminRefreshSessionServiceImpl store = new InMemoryAdminRefreshSessionServiceImpl();
        Instant expireAt = Instant.now().plusSeconds(3600);
        String r1 = RefreshTokenGenerator.generateOpaque();
        store.bindNewSession(9L, "u1", "sid-a", r1, expireAt);

        RotateRefreshDto first = store.rotate(r1);
        assertThat(first).isInstanceOf(RotateRefreshDto.Rotated.class);
        String r2 = ((RotateRefreshDto.Rotated) first).newRefreshPlain();

        RotateRefreshDto replay = store.rotate(r1);
        assertThat(replay).isInstanceOf(RotateRefreshDto.ReplayRevoked.class);

        RotateRefreshDto third = store.rotate(r2);
        assertThat(third).isInstanceOf(RotateRefreshDto.Invalid.class);
    }

    @Test
    void logoutInvalidatesRefresh() {
        InMemoryAdminRefreshSessionServiceImpl store = new InMemoryAdminRefreshSessionServiceImpl();
        Instant expireAt = Instant.now().plusSeconds(3600);
        String r = RefreshTokenGenerator.generateOpaque();
        store.bindNewSession(1L, "u", "sid-b", r, expireAt);

        store.revokeByRefreshCookie(java.util.Optional.of(r));

        assertThat(store.rotate(r)).isInstanceOf(RotateRefreshDto.Invalid.class);
    }
}
