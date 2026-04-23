package br.com.energybillai.data

import br.com.energybillai.data.remote.HistoricalConsumptionDto
import br.com.energybillai.data.remote.ReviewedBillDataDto
import br.com.energybillai.data.remote.TokenResponseDto
import br.com.energybillai.data.remote.UserDto
import br.com.energybillai.data.remote.toDomainSession
import br.com.energybillai.data.remote.toDto
import br.com.energybillai.domain.model.ReviewedBillData
import br.com.energybillai.domain.model.HistoricalConsumption
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RemoteMappersTest {

    @Test
    fun tokenResponseMapsToDomainSession() {
        val dto = TokenResponseDto(
            accessToken = "access",
            refreshToken = "refresh",
            tokenType = "bearer",
            expiresInSeconds = 3600,
            refreshExpiresInSeconds = 7200,
            user = UserDto(
                id = "user-1",
                name = "Renato",
                email = "renato@example.com",
                createdAt = "2026-04-13T12:00:00Z",
            ),
        )

        val session = dto.toDomainSession(issuedAtEpochSeconds = 1234L)

        assertThat(session.user.id).isEqualTo("user-1")
        assertThat(session.tokens.issuedAtEpochSeconds).isEqualTo(1234L)
    }

    @Test
    fun reviewedBillDataMapsToRequestDto() {
        val reviewed = ReviewedBillData(
            concessionaria = "CPFL",
            mesReferencia = "2026-04",
            consumoKwh = 252.0,
            diasFaturados = 29,
            valorTotal = 190.45,
            historicoConsumo = listOf(
                HistoricalConsumption(
                    referenceMonth = "2026-03",
                    consumptionKwh = 336.0,
                    billedDays = 32,
                ),
            ),
        )

        val dto = reviewed.toDto()

        assertThat(dto.concessionaria).isEqualTo("CPFL")
        assertThat(dto.historicoConsumo).containsExactly(
            HistoricalConsumptionDto(
                mesReferencia = "2026-03",
                consumoKwh = 336.0,
                diasFaturados = 32,
            ),
        )
    }
}
