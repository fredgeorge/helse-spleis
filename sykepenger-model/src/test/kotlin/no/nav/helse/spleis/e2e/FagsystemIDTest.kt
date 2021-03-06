package no.nav.helse.spleis.e2e

import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.UtbetalingHendelse.Oppdragstatus
import no.nav.helse.hendelser.Utbetalingshistorikk
import no.nav.helse.hendelser.Utbetalingshistorikk.Infotrygdperiode.RefusjonTilArbeidsgiver
import no.nav.helse.hendelser.til
import no.nav.helse.testhelpers.*
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class FagsystemIDTest : AbstractEndToEndTest() {

    /*
       starter i IT.
       samme arbeidsgiverperiode.
       [infotrygd][spleis][infotrygd][spleis]
                     ^ ny fagsystemID   ^ ny fagsystemID
    */
    @Test
    fun `får ny fagsystemId når perioden er innom infotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(30.mai, 23.juni, 100.prosent))
        håndterSøknad(Sykdom(30.mai, 23.juni, 100.prosent))
        val historie1 = listOf(
            RefusjonTilArbeidsgiver(19.mai, 29.mai, 1000.daglig,  100.prosent,  ORGNUMMER)
        )
        håndterUtbetalingshistorikk(1.vedtaksperiode, *historie1.toTypedArray())
        håndterYtelser(
            1.vedtaksperiode, *historie1.toTypedArray(),
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(
                    19.mai(2018),
                    1000.daglig,
                    ORGNUMMER,
                    true
                )
            )
        )
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        val historie2 = historie1 + listOf(
            RefusjonTilArbeidsgiver(24.juni, 12.juli, 1000.daglig,  100.prosent,  ORGNUMMER),
        )
        håndterSykmelding(Sykmeldingsperiode(13.juli, 31.juli, 100.prosent))
        håndterSøknad(Sykdom(13.juli, 31.juli, 100.prosent))
        håndterUtbetalingshistorikk(2.vedtaksperiode, *historie2.toTypedArray())
        håndterYtelser(
            2.vedtaksperiode, *historie2.toTypedArray(),
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(
                    24.juni(2018),
                    1000.daglig,
                    ORGNUMMER,
                    true
                )
            )
        )
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt(2.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        assertEquals(2, inspektør.utbetalinger.size)
        val første = inspektør.utbetalinger.first().arbeidsgiverOppdrag()
        val siste = inspektør.utbetalinger.last().arbeidsgiverOppdrag()
        assertNotEquals(første.fagsystemId(), siste.fagsystemId())
        første.linjerUtenOpphør().also { linjer ->
            assertEquals(1, linjer.size)
            assertEquals(30.mai, linjer.first().fom)
            assertEquals(23.juni, linjer.first().tom)
        }
        siste.linjerUtenOpphør().also { linjer ->
            assertEquals(1, linjer.size)
            assertEquals(13.juli, linjer.last().fom)
            assertEquals(31.juli, linjer.last().tom)
        }
    }

    /*
       starter i IT.
       samme arbeidsgiverperiode.
       [infotrygd][spleis][infotrygd][      spleis     ][spleis]
                     ^ ny fagsystemID   ^ ny fagsystemID   ^ samme fagsystemID
    */
    @Test
    fun `bruker samme fagsystemID når forrige er spleisperiode`() {
        håndterSykmelding(Sykmeldingsperiode(30.mai, 23.juni, 100.prosent))
        håndterSøknad(Sykdom(30.mai, 23.juni, 100.prosent))
        val historie1 = listOf(
            RefusjonTilArbeidsgiver(19.mai, 29.mai, 1000.daglig,  100.prosent,  ORGNUMMER)
        )
        håndterUtbetalingshistorikk(1.vedtaksperiode, *historie1.toTypedArray())
        håndterYtelser(
            1.vedtaksperiode, *historie1.toTypedArray(),
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(
                    19.mai(2018),
                    1000.daglig,
                    ORGNUMMER,
                    true
                )
            )
        )
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        val historie2 = historie1 + listOf(
            RefusjonTilArbeidsgiver(24.juni, 12.juli, 1000.daglig,  100.prosent,  ORGNUMMER),
        )

        håndterSykmelding(Sykmeldingsperiode(13.juli, 31.juli, 100.prosent))
        håndterSøknad(Sykdom(13.juli, 31.juli, 100.prosent))
        håndterUtbetalingshistorikk(2.vedtaksperiode, *historie2.toTypedArray())
        håndterYtelser(
            2.vedtaksperiode, *historie2.toTypedArray(),
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(
                    24.juni(2018),
                    1000.daglig,
                    ORGNUMMER,
                    true
                ),
                Utbetalingshistorikk.Inntektsopplysning(
                    19.mai(2018),
                    1000.daglig,
                    ORGNUMMER,
                    true
                )
            )
        )
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt(2.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Sykmeldingsperiode(1.august, 31.august, 100.prosent))
        håndterSøknad(Sykdom(1.august, 31.august, 100.prosent))
        håndterYtelser(
            3.vedtaksperiode, *historie2.toTypedArray(),
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(
                    24.juni(2018),
                    1000.daglig,
                    ORGNUMMER,
                    true
                ),
                Utbetalingshistorikk.Inntektsopplysning(
                    19.mai(2018),
                    1000.daglig,
                    ORGNUMMER,
                    true
                )
            )
        )
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode, true)
        håndterUtbetalt(3.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        assertEquals(3, inspektør.utbetalinger.size)
        val første = inspektør.utbetalinger.first().arbeidsgiverOppdrag()
        val andre = inspektør.utbetalinger[1].arbeidsgiverOppdrag()
        val siste = inspektør.utbetalinger.last().arbeidsgiverOppdrag()
        assertNotEquals(første.fagsystemId(), siste.fagsystemId())
        assertEquals(andre.fagsystemId(), siste.fagsystemId())
        siste.linjerUtenOpphør().also { linjer ->
            assertEquals(1, linjer.size)
            assertEquals(13.juli, linjer.last().fom)
            assertEquals(31.august, linjer.last().tom)
        }
    }

    /*
       starter i IT.
       ikke samme arbeidsgiverperiode.
       [infotrygd][spleis][infotrygd]   [infotrygd][spleis]
                    ^ ny fagsystemID                  ^ ny fagsystemID
    */
    @Test
    fun `bruker ny fagsystemID når det er gap i Infortrygd i mellomtiden`() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        val historie1 = listOf(
            RefusjonTilArbeidsgiver(1.januar, 31.januar, 1000.daglig,  100.prosent,  ORGNUMMER)
        )
        håndterUtbetalingshistorikk(1.vedtaksperiode, *historie1.toTypedArray())
        håndterYtelser(
            1.vedtaksperiode, *historie1.toTypedArray(),
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(
                    1.januar(2018),
                    1000.daglig,
                    ORGNUMMER,
                    true
                )
            )
        )
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, Oppdragstatus.AKSEPTERT)
        val historie2 = historie1 + listOf(
            // [ nok gap til ny arbeidsgiverperiode ]
            RefusjonTilArbeidsgiver(5.april, 30.april, 1000.daglig,  100.prosent,  ORGNUMMER)
        )
        håndterSykmelding(Sykmeldingsperiode(1.mai, 31.mai, 100.prosent))
        håndterSøknad(Sykdom(1.mai, 31.mai, 100.prosent))
        håndterUtbetalingshistorikk(2.vedtaksperiode, *historie2.toTypedArray())
        håndterYtelser(
            2.vedtaksperiode, *historie2.toTypedArray(),
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(
                    1.januar(2018),
                    1000.daglig,
                    ORGNUMMER,
                    true
                ),
                Utbetalingshistorikk.Inntektsopplysning(
                    5.april(2018),
                    1000.daglig,
                    ORGNUMMER,
                    true
                )
            )
        )
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt(2.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        assertEquals(2, inspektør.utbetalinger.size)
        val første = inspektør.utbetalinger.first().arbeidsgiverOppdrag()
        val siste = inspektør.utbetalinger.last().arbeidsgiverOppdrag()
        assertNotEquals(første.fagsystemId(), siste.fagsystemId())
        første.linjerUtenOpphør().also { linjer ->
            assertEquals(1, linjer.size)
            assertEquals(1.februar, linjer.first().fom)
            assertEquals(28.februar, linjer.first().tom)
        }
        siste.linjerUtenOpphør().also { linjer ->
            assertEquals(1, linjer.size)
            assertEquals(1.mai, linjer.last().fom)
            assertEquals(31.mai, linjer.last().tom)
        }
    }

    /*
       starter i IT.
       ikke samme arbeidsgiverperiode.
       [infotrygd][spleis][infotrygd]                         [infotrygd][spleis]
                    ^ ny fagsystemID   ^-egentlig ny AGP her               ^ ny fagsystemID
    */
    @Test
    fun `kort infotrygdperiode etter ny arbeidsgiverperiode`() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        val historie1 = listOf(
            RefusjonTilArbeidsgiver(1.januar, 31.januar, 1000.daglig,  100.prosent,  ORGNUMMER)
        )
        håndterUtbetalingshistorikk(1.vedtaksperiode, *historie1.toTypedArray())
        håndterYtelser(
            1.vedtaksperiode, *historie1.toTypedArray(),
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(
                    1.januar(2018),
                    1000.daglig,
                    ORGNUMMER,
                    true
                )
            )
        )
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, Oppdragstatus.AKSEPTERT)
        val historie2 = historie1 + listOf(
            // [ nok gap til ny arbeidsgiverperiode ]
            RefusjonTilArbeidsgiver(
                5.april, 10.april, 1000.daglig,
                100.prosent,
                ORGNUMMER
            )
        )
        håndterSykmelding(Sykmeldingsperiode(11.april, 30.april, 100.prosent))
        håndterSøknad(Sykdom(11.april, 30.april, 100.prosent))
        håndterUtbetalingshistorikk(2.vedtaksperiode, *historie2.toTypedArray())
        håndterYtelser(
            2.vedtaksperiode, *historie2.toTypedArray(),
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(
                    5.april(2018),
                    1000.daglig,
                    ORGNUMMER,
                    true
                ),
                Utbetalingshistorikk.Inntektsopplysning(
                    1.januar(2018),
                    1000.daglig,
                    ORGNUMMER,
                    true
                )
            )
        )
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt(2.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        val siste = inspektør.utbetalinger.last().arbeidsgiverOppdrag()
        siste.linjerUtenOpphør().also { linjer ->
            assertEquals(1, linjer.size)
            assertEquals(11.april, linjer.last().fom)
            assertEquals(30.april, linjer.last().tom)
        }
    }

    /*
       starter i Spleis.
       [spleis][infotrygd][spleis]
          ^ ny fagsystemID  ^ ny fagsystemID
    */
    @Test
    fun `får ny fagsystemId når Infotrygdperiode foran`() {
        håndterSykmelding(Sykmeldingsperiode(1.april, 30.april, 100.prosent))
        håndterSøknad(Sykdom(1.april, 30.april, 100.prosent))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(1.april til 16.april))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        val historie1 = listOf(
            RefusjonTilArbeidsgiver(1.mai, 29.mai, 1000.daglig,  100.prosent,  ORGNUMMER)
        )

        håndterSykmelding(Sykmeldingsperiode(30.mai, 30.juni, 100.prosent))
        håndterSøknad(Sykdom(30.mai, 30.juni, 100.prosent))
        håndterUtbetalingshistorikk(2.vedtaksperiode, *historie1.toTypedArray())
        håndterYtelser(2.vedtaksperiode, *historie1.toTypedArray())
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt(2.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        assertEquals(2, inspektør.utbetalinger.size)
        val første = inspektør.utbetalinger.first().arbeidsgiverOppdrag()
        val siste = inspektør.utbetalinger.last().arbeidsgiverOppdrag()
        assertNotEquals(første.fagsystemId(), siste.fagsystemId())
        første.linjerUtenOpphør().also { linjer ->
            assertEquals(1, linjer.size)
            assertEquals(17.april, linjer.first().fom)
            assertEquals(30.april, linjer.first().tom)
        }
        siste.linjerUtenOpphør().also { linjer ->
            assertEquals(1, linjer.size)
            assertEquals(30.mai, linjer.first().fom)
            assertEquals(30.juni, linjer.first().tom)
        }
    }

    /*
       starter i Spleis.
       [spleis][infotrygd][     spleis     ][spleis]
          ^ ny fagsystemID  ^ ny fagsystemID  ^ samme fagsystemID
    */
    @Test
    fun `bruker samme fagsystemID ved forlengelse`() {
        håndterSykmelding(Sykmeldingsperiode(1.april, 30.april, 100.prosent))
        håndterSøknad(Sykdom(1.april, 30.april, 100.prosent))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(1.april til 16.april))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        val historie1 = listOf(
            RefusjonTilArbeidsgiver(1.mai, 29.mai, 1000.daglig,  100.prosent,  ORGNUMMER)
        )
        håndterSykmelding(Sykmeldingsperiode(30.mai, 30.juni, 100.prosent))
        håndterSøknad(Sykdom(30.mai, 30.juni, 100.prosent))
        håndterUtbetalingshistorikk(2.vedtaksperiode, *historie1.toTypedArray())
        håndterYtelser(2.vedtaksperiode, *historie1.toTypedArray())
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt(2.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Sykmeldingsperiode(1.juli, 31.juli, 100.prosent))
        håndterSøknad(Sykdom(1.juli, 31.juli, 100.prosent))
        håndterYtelser(3.vedtaksperiode, *historie1.toTypedArray())
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode, true)
        håndterUtbetalt(3.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        assertEquals(3, inspektør.utbetalinger.size)
        val første = inspektør.utbetalinger.first().arbeidsgiverOppdrag()
        val andre = inspektør.utbetalinger[1].arbeidsgiverOppdrag()
        val siste = inspektør.utbetalinger.last().arbeidsgiverOppdrag()
        assertNotEquals(første.fagsystemId(), siste.fagsystemId())
        assertEquals(andre.fagsystemId(), siste.fagsystemId())
        siste.linjerUtenOpphør().also { linjer ->
            assertEquals(1, linjer.size)
            assertEquals(30.mai, linjer.first().fom)
            assertEquals(31.juli, linjer.first().tom)
        }
    }

}
