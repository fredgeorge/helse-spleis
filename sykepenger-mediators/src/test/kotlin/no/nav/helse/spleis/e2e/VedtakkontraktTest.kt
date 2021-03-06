package no.nav.helse.spleis.e2e

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.spleis.meldinger.model.SimuleringMessage
import no.nav.helse.testhelpers.januar
import no.nav.inntektsmeldingkontrakt.Periode
import no.nav.syfo.kafka.felles.FravarDTO
import no.nav.syfo.kafka.felles.FravarstypeDTO
import no.nav.syfo.kafka.felles.SoknadsperiodeDTO
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

internal class VedtakkontraktTest : AbstractEndToEndMediatorTest() {

    @Test
    fun `vedtak med utbetaling`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(0, listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100)))
        sendInntektsmelding(0, listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(0)
        sendUtbetaling()
        assertVedtakMedUtbetaling()
    }

    @Test
    fun `vedtak uten utbetaling`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            0,
            listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100)),
            ferie = listOf(FravarDTO(19.januar, 26.januar, FravarstypeDTO.FERIE))
        )
        sendInntektsmelding(0, listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        assertVedtakUtenUtbetaling()
    }

    private fun assertVedtakMedUtbetaling() {
        testRapid.inspektør.siste("vedtak_fattet").also { melding ->
            assertVedtak(melding)
            assertTrue(melding.path("utbetalingId").asText().isNotEmpty())
        }
    }

    private fun assertVedtakUtenUtbetaling() {
        testRapid.inspektør.siste("vedtak_fattet").also { melding ->
            assertVedtak(melding)
            assertTrue(melding.path("utbetalingId").let { it.isMissingNode || it.isNull })
        }
    }

    private fun assertVedtak(melding: JsonNode) {
        assertTrue(melding.path("vedtaksperiodeId").asText().isNotEmpty())
        assertTrue(melding.path("hendelser").isArray)
        assertDato(melding.path("fom").asText())
        assertDato(melding.path("tom").asText())
        assertDato(melding.path("skjæringstidspunkt").asText())
        assertTall(melding, "sykepengegrunnlag")
        assertTall(melding, "inntekt")
    }

    private fun assertDato(tekst: String) {
        assertTrue(tekst.isNotEmpty())
        assertDoesNotThrow { LocalDate.parse(tekst) }
    }

    private fun assertTall(melding: JsonNode, key: String) {
        assertTrue(melding.path(key).isDouble)
        assertTrue(melding.path(key).doubleValue() >= 0)
    }

    private fun assert(tekst: String) {
        assertTrue(tekst.isNotEmpty())
        assertDoesNotThrow { LocalDateTime.parse(tekst) }
    }
}


