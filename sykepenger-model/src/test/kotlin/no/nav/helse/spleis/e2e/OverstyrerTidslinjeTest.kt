package no.nav.helse.spleis.e2e

import no.nav.helse.hendelser.*
import no.nav.helse.person.TilstandType
import no.nav.helse.testhelpers.januar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class OverstyrerTidslinjeTest : AbstractEndToEndTest() {

    @Test
    fun `overstyrer sykedag på slutten av perioden`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(2.januar, 18.januar)), førsteFraværsdag = 2.januar)
        håndterSøknadMedValidering(0, Søknad.Søknadsperiode.Sykdom(3.januar, 26.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterOverstyring(listOf(manuellSykedag(2.januar), manuellArbeidsgiverdag(24.januar), manuellFeriedag(25.januar)))
        håndterUtbetalingsgodkjenning(0, true)

        assertEquals("SSSSHH SSSSSHH SSSSSHH SSUFS", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString())
    }

    @Test
    fun `vedtaksperiode rebehandler informasjon etter endring fra saksbehandler`() {
        håndterSykmelding(Sykmeldingsperiode(2.januar, 25.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(2.januar, 17.januar)), førsteFraværsdag = 2.januar)
        håndterSøknadMedValidering(0, Søknad.Søknadsperiode.Sykdom(2.januar, 25.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterOverstyring(listOf(manuellArbeidsgiverdag(18.januar)))

        assertNotEquals(TilstandType.AVVENTER_GODKJENNING, inspektør.sisteTilstand(0))

        håndterYtelser(0)   // No history
        håndterSimulering(0)

        assertTilstander(
            0,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_SØKNAD_FERDIG_GAP,
            TilstandType.AVVENTER_VILKÅRSPRØVING_GAP,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING
        )
        assertEquals(19.januar, inspektør.utbetalinger.last().utbetalingstidslinje().førsteSykepengedag())
    }

    @Test
    fun `grad over grensen endres på enkeltdag`() {
        håndterSykmelding(Sykmeldingsperiode(2.januar, 25.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(2.januar, 17.januar)), førsteFraværsdag = 2.januar)
        håndterSøknadMedValidering(0, Søknad.Søknadsperiode.Sykdom(2.januar, 25.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)
        håndterSimulering(0)
        håndterOverstyring(listOf(manuellSykedag(22.januar, 30)))

        assertNotEquals(TilstandType.AVVENTER_GODKJENNING, inspektør.sisteTilstand(0))

        håndterYtelser(0)
        håndterSimulering(0)

        assertEquals(3, inspektør.utbetalinger.last().arbeidsgiverOppdrag().size)
        assertEquals(21.januar, inspektør.utbetalinger.last().arbeidsgiverOppdrag()[0].tom)
        assertEquals(30.0, inspektør.utbetalinger.last().arbeidsgiverOppdrag()[1].grad)
        assertEquals(23.januar, inspektør.utbetalinger.last().arbeidsgiverOppdrag()[2].fom)
    }

    @Test
    fun `grad under grensen blir ikke utbetalt`() {
        håndterSykmelding(Sykmeldingsperiode(2.januar, 25.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(2.januar, 17.januar)), førsteFraværsdag = 2.januar)
        håndterSøknadMedValidering(0, Søknad.Søknadsperiode.Sykdom(2.januar, 25.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterOverstyring(listOf(manuellSykedag(22.januar, 0)))

        assertNotEquals(TilstandType.AVVENTER_GODKJENNING, inspektør.sisteTilstand(0))

        håndterYtelser(0)   // No history
        håndterSimulering(0)

        assertEquals(2, inspektør.utbetalinger.last().arbeidsgiverOppdrag().size)
        assertEquals(21.januar, inspektør.utbetalinger.last().arbeidsgiverOppdrag()[0].tom)
        assertEquals(23.januar, inspektør.utbetalinger.last().arbeidsgiverOppdrag()[1].fom)
    }

    @Test
    fun `feriedag i midten av en periode blir ikke utbetalt`() {
        håndterSykmelding(Sykmeldingsperiode(2.januar, 25.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(2.januar, 17.januar)), førsteFraværsdag = 2.januar)
        håndterSøknadMedValidering(0, Søknad.Søknadsperiode.Sykdom(2.januar, 25.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)
        håndterSimulering(0)
        håndterOverstyring(listOf(manuellFeriedag(22.januar)))

        assertNotEquals(TilstandType.AVVENTER_GODKJENNING, inspektør.sisteTilstand(0))

        håndterYtelser(0)
        håndterSimulering(0)

        assertEquals(2, inspektør.utbetalinger.last().arbeidsgiverOppdrag().size)
        assertEquals(21.januar, inspektør.utbetalinger.last().arbeidsgiverOppdrag()[0].tom)
        assertEquals(23.januar, inspektør.utbetalinger.last().arbeidsgiverOppdrag()[1].fom)
    }

    private fun manuellFeriedag(dato: LocalDate) = ManuellOverskrivingDag(dato, Dagtype.Feriedag)
    private fun manuellSykedag(dato: LocalDate, grad: Int = 100) = ManuellOverskrivingDag(dato, Dagtype.Sykedag, grad)
    private fun manuellArbeidsgiverdag(dato: LocalDate) = ManuellOverskrivingDag(dato, Dagtype.Egenmeldingsdag)
}