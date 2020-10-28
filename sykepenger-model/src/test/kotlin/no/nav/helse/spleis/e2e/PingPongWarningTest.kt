package no.nav.helse.spleis.e2e

import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.hendelser.Utbetalingshistorikk
import no.nav.helse.juli
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.testhelpers.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PingPongWarningTest : AbstractEndToEndTest() {

    @Test
    fun `Warning ved ping-pong hvis historikken er nyere enn seks måneder`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 17.januar, 100))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.januar, 16.januar)))
        håndterSøknad(Sykdom(1.januar, 17.januar, gradFraSykmelding = 100), sendtTilNav = 18.februar)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)

        håndterSykmelding(Sykmeldingsperiode(10.februar, 28.februar, 100))
        håndterSøknad(
            Sykdom(10.februar, 28.februar, gradFraSykmelding = 100),
            sendtTilNav = 1.mars,
            harAndreInntektskilder = true
        )

        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100))
        håndterSøknad(Sykdom(1.mars, 31.mars, gradFraSykmelding = 100), sendtTilNav = 1.april)
        val historikk = Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(1.februar, 28.februar, 1337, 100, ORGNUMMER)
        håndterYtelser(3.vedtaksperiode, historikk)
        håndterYtelser(3.vedtaksperiode, historikk)
        håndterSimulering(3.vedtaksperiode)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_GODKJENNING)

        assertTrue(inspektør.personLogg.warn().toString().contains("Perioden forlenger en behandling i Infotrygd, og har historikk fra ny løsning: Undersøk at antall dager igjen er beregnet riktig."))
    }

    @Test
    fun `Ikke warning ved ping-pong hvor forkastet periode ligger mer enn seks måneder tilbake i tid`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 17.januar, 100))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.januar, 16.januar)))
        håndterSøknad(Sykdom(1.januar, 17.januar, gradFraSykmelding = 100), sendtTilNav = 18.februar)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)

        håndterSykmelding(Sykmeldingsperiode(1.juli, 17.juli, 100))
        håndterSøknad(
            Sykdom(1.juli, 17.juli, gradFraSykmelding = 100),
            sendtTilNav = 18.juli,
            harAndreInntektskilder = true
        )

        håndterSykmelding(Sykmeldingsperiode(18.juli, 31.juli, 100))
        håndterSøknad(Sykdom(18.juli, 31.juli, gradFraSykmelding = 100), sendtTilNav = 1.august)
        val historikk = Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(1.juli, 17.juli, 1337, 100, ORGNUMMER)
        håndterYtelser(3.vedtaksperiode, historikk)
        håndterYtelser(3.vedtaksperiode, historikk)
        håndterSimulering(3.vedtaksperiode)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_GODKJENNING)

        assertTrue(inspektør.personLogg.warn().isEmpty())
    }

    @Test
    fun `Ikke warning for ping-pong hvis vi ikke har utbetalt periode i ny løsning`() {
        håndterSykmelding(Sykmeldingsperiode(1.juli, 17.juli, 100))
        håndterSøknad(
            Sykdom(1.juli, 17.juli, gradFraSykmelding = 100),
            sendtTilNav = 18.juli,
            harAndreInntektskilder = true
        )

        håndterSykmelding(Sykmeldingsperiode(18.juli, 31.juli, 100))
        håndterSøknad(Sykdom(18.juli, 31.juli, gradFraSykmelding = 100), sendtTilNav = 1.august)
        val historikk = Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(1.juli, 17.juli, 1337, 100, ORGNUMMER)
        håndterYtelser(2.vedtaksperiode, historikk)
        håndterYtelser(2.vedtaksperiode, historikk)
        håndterSimulering(2.vedtaksperiode)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_GODKJENNING)

        assertTrue(inspektør.personLogg.warn().isEmpty())
    }

}