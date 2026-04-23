package br.com.energybillai.domain

import br.com.energybillai.domain.model.AuthTokens
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AuthTokensTest {

    @Test
    fun accessTokenIsValidWhenExpirationIsInTheFuture() {
        val tokens = AuthTokens(
            accessToken = "access",
            refreshToken = "refresh",
            tokenType = "bearer",
            expiresInSeconds = 3600,
            refreshExpiresInSeconds = 7200,
            issuedAtEpochSeconds = 1_000,
        )

        assertThat(tokens.isAccessTokenValid(nowEpochSeconds = 1_100)).isTrue()
    }

    @Test
    fun refreshIsNotAllowedWhenRefreshWindowExpired() {
        val tokens = AuthTokens(
            accessToken = "access",
            refreshToken = "refresh",
            tokenType = "bearer",
            expiresInSeconds = 300,
            refreshExpiresInSeconds = 600,
            issuedAtEpochSeconds = 1_000,
        )

        assertThat(tokens.canRefresh(nowEpochSeconds = 1_700)).isFalse()
    }
}
