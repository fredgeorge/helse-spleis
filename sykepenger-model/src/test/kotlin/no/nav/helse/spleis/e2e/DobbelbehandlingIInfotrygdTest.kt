package no.nav.helse.spleis.e2e

import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Utbetalingshistorikk
import no.nav.helse.hendelser.Utbetalingshistorikk.Infotrygdperiode.RefusjonTilArbeidsgiver
import no.nav.helse.hendelser.til
import no.nav.helse.person.TilstandType.*
import no.nav.helse.testhelpers.*
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DobbelbehandlingIInfotrygdTest : AbstractEndToEndTest() {

    @Test
    fun `avdekker overlapp dobbelbehandlinger i Infotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100.prosent))
        håndterPåminnelse(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP)
        håndterUtbetalingshistorikk(
            1.vedtaksperiode,
            RefusjonTilArbeidsgiver(3.januar, 26.januar, 1000.daglig,  100.prosent,  ORGNUMMER)
        )

        håndterSykmelding(Sykmeldingsperiode(3.februar, 26.februar, 100.prosent))
        håndterPåminnelse(2.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP)
        håndterUtbetalingshistorikk(
            2.vedtaksperiode,
            RefusjonTilArbeidsgiver(26.februar, 26.mars, 1000.daglig,  100.prosent,  ORGNUMMER)
        )

        håndterSykmelding(Sykmeldingsperiode(1.mai, 30.mai, 100.prosent))
        håndterPåminnelse(3.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP)
        håndterUtbetalingshistorikk(3.vedtaksperiode, RefusjonTilArbeidsgiver(1.april, 1.mai, 1000.daglig,  100.prosent,  ORGNUMMER))

        assertForkastetPeriodeTilstander(1.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(3.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
    }

    @Test
    fun `utbetalt i Infotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(9.november(2020), 4.desember(2020), 100.prosent))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(9.november(2020), 4.desember(2020), 100.prosent))
        håndterUtbetalingshistorikk(1.vedtaksperiode,
            RefusjonTilArbeidsgiver(5.desember(2020), 23.desember(2020), 1000.daglig, 100.prosent, ORGNUMMER),
            RefusjonTilArbeidsgiver(1.januar(2021), 3.januar(2021), 1000.daglig, 100.prosent, ORGNUMMER),
            Utbetalingshistorikk.Infotrygdperiode.Ferie(24.desember(2020), 31.desember(2020)),
            Utbetalingshistorikk.Infotrygdperiode.Utbetaling(29.oktober(2020), 4.desember(2020), 1000.daglig, 100.prosent, "456789123"),
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(29.oktober(2020), 1000.daglig, ORGNUMMER, true)
            )
        )
        assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
    }

    @Test
    fun `utbetalt i infotrygd mens vi venter på inntektsmelding - oppdaget i AVVENTER_HISTORIKK`() {
        håndterSykmelding(Sykmeldingsperiode(9.november(2020), 4.desember(2020), 100.prosent))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(9.november(2020), 4.desember(2020), 100.prosent))
        håndterUtbetalingshistorikk(1.vedtaksperiode)
        håndterInntektsmelding(listOf(9.november(2020) til 24.november(2020)))
        håndterVilkårsgrunnlag(1.vedtaksperiode)

        håndterYtelser(1.vedtaksperiode,
            Utbetalingshistorikk.Infotrygdperiode.Utbetaling(1.desember(2020), 4.desember(2020), 1000.daglig, 100.prosent, "456789123"),
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(1.desember(2020), 1000.daglig, "456789123", true)
            )
        )
        assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
    }

    @Test
    fun `utbetalt i infotrygd mens vi venter på inntektsmelding - oppdaget ved påminnelse`() {
        håndterSykmelding(Sykmeldingsperiode(9.november(2020), 4.desember(2020), 100.prosent))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(9.november(2020), 4.desember(2020), 100.prosent))
        håndterUtbetalingshistorikk(1.vedtaksperiode)
        håndterPåminnelse(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP)
        håndterUtbetalingshistorikk(1.vedtaksperiode,
            Utbetalingshistorikk.Infotrygdperiode.Utbetaling(1.desember(2020), 4.desember(2020), 1000.daglig, 100.prosent, "456789123"),
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(1.desember(2020), 1000.daglig, "456789123", true)
            )
        )
        assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
    }
}
