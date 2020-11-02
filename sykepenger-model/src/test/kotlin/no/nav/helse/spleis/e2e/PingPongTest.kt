package no.nav.helse.spleis.e2e

import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.hendelser.Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.testhelpers.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class PingPongTest : AbstractEndToEndTest() {

    @Test
    fun `Forlengelser av infotrygd overgang har samme maksdato som forrige`() {
        val historikk1 = RefusjonTilArbeidsgiver(20.november(2019), 29.mai(2020), 1145, 100, ORGNUMMER)

        håndterSykmelding(Sykmeldingsperiode(30.mai(2020), 19.juni(2020), 100))
        håndterSøknad(Sykdom(30.mai(2020), 19.juni(2020), 100))
        håndterYtelser(1.vedtaksperiode, historikk1)
        håndterYtelser(1.vedtaksperiode, historikk1)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)


        håndterSykmelding(Sykmeldingsperiode(22.juni(2020), 9.juli(2020), 100))
        håndterSøknad(
            Sykdom(22.juni(2020), 9.juli(2020), 100)
        )
        håndterYtelser(2.vedtaksperiode, historikk1)
        håndterYtelser(2.vedtaksperiode, historikk1)
        håndterSimulering(2.vedtaksperiode)
        håndterPåminnelse(2.vedtaksperiode, AVVENTER_GODKJENNING, LocalDateTime.now().minusWeeks(2))

        val historikk2 = RefusjonTilArbeidsgiver(22.juni(2020), 17.august(2020), 1145, 100, ORGNUMMER)

        håndterSykmelding(Sykmeldingsperiode(18.august(2020), 2.september(2020), 100))
        håndterSøknad(Sykdom(18.august(2020), 2.september(2020), 100))
        håndterYtelser(3.vedtaksperiode, historikk2, historikk1)
        håndterYtelser(3.vedtaksperiode, historikk2, historikk1)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode, true)
        håndterUtbetalt(3.vedtaksperiode, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
        assertEquals(30.oktober(2020), inspektør.maksdato(3.vedtaksperiode))
    }

}