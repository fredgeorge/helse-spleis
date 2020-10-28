package no.nav.helse.spleis.e2e

import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.helse.person.OppdragVisitor
import no.nav.helse.person.TilstandType
import no.nav.helse.serde.api.TilstandstypeDTO
import no.nav.helse.testhelpers.*
import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.utbetalingslinjer.Oppdrag
import no.nav.helse.utbetalingslinjer.Utbetalingslinje
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class AnnullerUtbetalingTest : AbstractEndToEndTest() {

    @Test
    fun `avvis hvis arbeidsgiver er ukjent`() {
        nyttVedtak(3.januar, 26.januar, 100, 3.januar)
        håndterAnnullerUtbetaling(orgnummer = "999999")
        inspektør.also {
            assertTrue(it.personLogg.hasErrorsOrWorse(), it.personLogg.toString())
        }
    }

    @Test
    fun `avvis hvis vi ikke finner fagsystemId`() {
        nyttVedtak(3.januar, 26.januar, 100, 3.januar)
        håndterAnnullerUtbetaling(fagsystemId = "unknown")
        inspektør.also {
            assertEquals(TilstandType.AVSLUTTET, it.sisteTilstand(1.vedtaksperiode))
        }
    }

    @Test
    fun `annuller siste utbetaling`() {
        nyttVedtak(3.januar, 26.januar, 100, 3.januar)
        val behovTeller = inspektør.personLogg.behov().size
        håndterAnnullerUtbetaling(fagsystemId = inspektør.fagsystemId(1.vedtaksperiode))
        sjekkAt(inspektør) {
            !personLogg.hasErrorsOrWorse() ellers personLogg.toString()
            val behov = sisteBehov(Behovtype.Utbetaling)

            @Suppress("UNCHECKED_CAST")
            val statusForUtbetaling = (behov.detaljer()["linjer"] as List<Map<String, Any>>)[0]["statuskode"]
            statusForUtbetaling er "OPPH"
        }

        sjekkAt(speilApi().arbeidsgivere[0]) {
            vedtaksperioder[0].tilstand er TilstandstypeDTO.TilAnnullering
        }

        håndterUtbetalt(1.vedtaksperiode, status = UtbetalingHendelse.Oppdragstatus.AKSEPTERT, annullert = true)
        sjekkAt(inspektør) {
            !personLogg.hasErrorsOrWorse() ellers personLogg.toString()
            arbeidsgiverOppdrag.size er 2
            (personLogg.behov().size - behovTeller) skalVære 1 ellers personLogg.toString()
        }

        sjekkAt(TestOppdragInspektør(inspektør.arbeidsgiverOppdrag[1])) {
            linjer[0] er Utbetalingslinje(19.januar, 26.januar, 1431, 1431, 100.0)
            endringskoder[0] er Endringskode.ENDR
            refFagsystemIder[0] er null
        }

        sjekkAt(inspektør.personLogg.behov().last()) {
            type er Behovtype.Utbetaling
            detaljer()["maksdato"] er null
            detaljer()["saksbehandler"] er "Ola Nordmann"
            detaljer()["fagområde"] er "SPREF"
        }

        sjekkAt(speilApi().arbeidsgivere[0]) {
            vedtaksperioder[0].tilstand er TilstandstypeDTO.Annullert
        }
    }


    @Test
    fun `Annuller flere fagsystemid for samme arbeidsgiver`() {
        nyttVedtak(3.januar, 26.januar, 100, 3.januar)
        nyttVedtak(1.mars, 31.mars, 100, 1.mars)

        håndterAnnullerUtbetaling(fagsystemId = inspektør.fagsystemId(1.vedtaksperiode))
        håndterUtbetalt(1.vedtaksperiode, status = UtbetalingHendelse.Oppdragstatus.AKSEPTERT, annullert = true)
        sjekkAt(speilApi().arbeidsgivere[0]) {
            vedtaksperioder[0].tilstand er TilstandstypeDTO.Annullert
            vedtaksperioder[1].tilstand er TilstandstypeDTO.Utbetalt
        }


        sisteBehovErAnnullering(1.vedtaksperiode)

        håndterAnnullerUtbetaling(fagsystemId = inspektør.fagsystemId(2.vedtaksperiode))
        håndterUtbetalt(2.vedtaksperiode, status = UtbetalingHendelse.Oppdragstatus.AKSEPTERT, annullert = true)

        sjekkAt(speilApi().arbeidsgivere[0]) {
            vedtaksperioder[0].tilstand er TilstandstypeDTO.Annullert
            vedtaksperioder[1].tilstand er TilstandstypeDTO.Annullert
        }

        sisteBehovErAnnullering(2.vedtaksperiode)
    }

    @Test
    fun `Annuller flere fagsystemid for samme arbeidsgiver, utenom den i midten`() {
        nyttVedtak(3.januar, 26.januar, 100, 3.januar)
        nyttVedtak(1.mars, 31.mars, 100, 1.mars)
        nyttVedtak(1.mai, 31.mai, 100, 1.mai)

        håndterAnnullerUtbetaling(fagsystemId = inspektør.fagsystemId(1.vedtaksperiode))
        håndterUtbetalt(1.vedtaksperiode, status = UtbetalingHendelse.Oppdragstatus.AKSEPTERT, annullert = true)
        sjekkAt(speilApi().arbeidsgivere[0]) {
            vedtaksperioder[0].tilstand er TilstandstypeDTO.Annullert
            vedtaksperioder[1].tilstand er TilstandstypeDTO.Utbetalt
            vedtaksperioder[2].tilstand er TilstandstypeDTO.Utbetalt
        }

        sjekkAt(inspektør.personLogg.behov().last()) {
            type er Behovtype.Utbetaling
            detaljer()["fagsystemId"] er inspektør.fagsystemId(1.vedtaksperiode)
            hentLinjer()[0]["statuskode"] er "OPPH"
        }

        håndterAnnullerUtbetaling(fagsystemId = inspektør.fagsystemId(3.vedtaksperiode))
        håndterUtbetalt(3.vedtaksperiode, status = UtbetalingHendelse.Oppdragstatus.AKSEPTERT, annullert = true)

        sjekkAt(speilApi().arbeidsgivere[0]) {
            vedtaksperioder[0].tilstand er TilstandstypeDTO.Annullert
            vedtaksperioder[1].tilstand er TilstandstypeDTO.Utbetalt
            vedtaksperioder[2].tilstand er TilstandstypeDTO.Annullert
        }

        val vedtaksperiode = 3.vedtaksperiode

        sisteBehovErAnnullering(vedtaksperiode)
    }

    private fun sisteBehovErAnnullering(vedtaksperiode: UUID) {
        sjekkAt(inspektør.personLogg.behov().last()) {
            type er Behovtype.Utbetaling
            detaljer()["fagsystemId"] er inspektør.fagsystemId(vedtaksperiode)
            hentLinjer()[0]["statuskode"] er "OPPH"
        }
    }

    private fun assertIngenAnnulleringsbehov() {
        assertFalse(
            inspektør.personLogg.behov()
                .filter { it.type == Behovtype.Utbetaling }
                .any {
                    it.hentLinjer().any { linje ->
                        linje["statuskode"] == "OPPH"
                    }
                }
        )
    }

    @Test
    fun `Annuller oppdrag som er under utbetaling feiler`() {
        tilGodkjent(3.januar, 26.januar, 100, 3.januar)

        håndterAnnullerUtbetaling(fagsystemId = inspektør.fagsystemId(1.vedtaksperiode))
        sjekkAt(inspektør) {
            personLogg.hasErrorsOrWorse() ellers personLogg.toString()
        }

        sjekkAt(speilApi().arbeidsgivere[0]) {
            vedtaksperioder[0].tilstand er TilstandstypeDTO.TilUtbetaling
        }

        assertIngenAnnulleringsbehov()
    }

    @Test
    fun `Annuller av oppdrag med feilet utbetaling feiler`() {
        tilGodkjent(3.januar, 26.januar, 100, 3.januar)
        håndterUtbetalt(1.vedtaksperiode, status = UtbetalingHendelse.Oppdragstatus.FEIL)

        håndterAnnullerUtbetaling(fagsystemId = inspektør.fagsystemId(1.vedtaksperiode))
        sjekkAt(inspektør) {
            personLogg.hasErrorsOrWorse() ellers personLogg.toString()
        }

        sjekkAt(speilApi().arbeidsgivere[0]) {
            vedtaksperioder[0].tilstand er TilstandstypeDTO.Feilet
        }

        assertIngenAnnulleringsbehov()
    }


    @Test
    fun `Kan ikke annullere hvis noen vedtaksperioder er til utbetaling`() {
        nyttVedtak(3.januar, 26.januar, 100, 3.januar)
        tilGodkjent(1.mars, 31.mars, 100, 1.mars)

        håndterAnnullerUtbetaling(fagsystemId = inspektør.fagsystemId(1.vedtaksperiode))

        sjekkAt(inspektør) {
            personLogg.hasErrorsOrWorse() ellers personLogg.toString()
        }

        sjekkAt(speilApi().arbeidsgivere[0]) {
            vedtaksperioder[0].tilstand er TilstandstypeDTO.Utbetalt
            vedtaksperioder[1].tilstand er TilstandstypeDTO.TilUtbetaling
        }

        assertIngenAnnulleringsbehov()
    }


    @Test
    fun `Vedtaksperioder etter annulert fagsystemid blir sendt til infortrygd`() {
        nyttVedtak(3.januar, 26.januar, 100, 3.januar)
        tilSimulert(1.mars, 31.mars, 100, 1.mars)
        forlengPeriode(1.april, 30.april, 100)

        håndterAnnullerUtbetaling(fagsystemId = inspektør.fagsystemId(1.vedtaksperiode))
        håndterUtbetalt(1.vedtaksperiode, status = UtbetalingHendelse.Oppdragstatus.AKSEPTERT, annullert = true)

        sjekkAt(inspektør) {
            !personLogg.hasErrorsOrWorse() ellers personLogg.toString()
        }

        sjekkAt(speilApi().arbeidsgivere[0]) {
            vedtaksperioder.size er 1
            vedtaksperioder[0].tilstand er TilstandstypeDTO.Annullert
        }

        sjekkAt(observatør.avbruttEventer) {
            this[0].vedtaksperiodeId er 1.vedtaksperiode
            this[0].gjeldendeTilstand er TilstandType.AVSLUTTET
            this[1].vedtaksperiodeId er 2.vedtaksperiode
            this[1].gjeldendeTilstand er TilstandType.TIL_INFOTRYGD
            this[2].vedtaksperiodeId er 3.vedtaksperiode
            this[2].gjeldendeTilstand er TilstandType.TIL_INFOTRYGD
        }


        sisteBehovErAnnullering(1.vedtaksperiode)
    }

    private fun Aktivitetslogg.Aktivitet.Behov.hentLinjer() =
        @Suppress("UNCHECKED_CAST")
        (detaljer()["linjer"] as List<Map<String, Any>>)


    @Test
    fun `Ved feilet annulleringsutbetaling settes utbetaling til annullering feilet`() {
        nyttVedtak(3.januar, 26.januar, 100, 3.januar)
        håndterAnnullerUtbetaling(fagsystemId = inspektør.fagsystemId(1.vedtaksperiode))
        håndterUtbetalt(1.vedtaksperiode, status = UtbetalingHendelse.Oppdragstatus.FEIL, annullert = true)

        sjekkAt(inspektør) {
            personLogg.hasErrorsOrWorse() ellers personLogg.toString()
            arbeidsgiverOppdrag.size er 2
        }

        sjekkAt(speilApi().arbeidsgivere[0]) {
            vedtaksperioder[0].tilstand er TilstandstypeDTO.AnnulleringFeilet
        }
    }

    @Test
    fun `En enkel periode som er avsluttet som blir annullert blir også satt i tilstand TilAnnullering`() {
        nyttVedtak(3.januar, 26.januar, 100, 3.januar)
        inspektør.also {
            assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(1.vedtaksperiode))
        }
        håndterAnnullerUtbetaling(fagsystemId = inspektør.fagsystemId(1.vedtaksperiode))
        inspektør.also {
            assertFalse(it.personLogg.hasErrorsOrWorse(), it.personLogg.toString())
            assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(1.vedtaksperiode))
            assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
        }
    }

    @Test
    fun `Annullering av én periode fører til at alle avsluttede sammenhengende perioder blir satt i tilstand TilAnnullering`() {
        nyttVedtak(3.januar, 26.januar, 100, 3.januar)
        forlengVedtak(27.januar, 31.januar, 100)
        forlengPeriode(1.februar, 20.februar, 100)
        inspektør.also {
            assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(1.vedtaksperiode))
            assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(2.vedtaksperiode))
            assertEquals(TilstandType.AVVENTER_HISTORIKK, inspektør.sisteTilstand(3.vedtaksperiode))
        }
        håndterAnnullerUtbetaling(fagsystemId = inspektør.fagsystemId(1.vedtaksperiode))
        inspektør.also {
            assertFalse(it.personLogg.hasErrorsOrWorse(), it.personLogg.toString())
            assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(1.vedtaksperiode))
            assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
            assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(2.vedtaksperiode))
            assertTrue(inspektør.periodeErForkastet(2.vedtaksperiode))
            assertEquals(TilstandType.TIL_INFOTRYGD, inspektør.sisteTilstand(3.vedtaksperiode))
            assertTrue(inspektør.periodeErForkastet(3.vedtaksperiode))
            assertTrue(it.utbetalinger.last().erAnnullert())
            assertFalse(it.utbetalinger.last().erUtbetalt())
        }
        håndterUtbetalt(1.vedtaksperiode, status = UtbetalingHendelse.Oppdragstatus.AKSEPTERT, annullert = true)
        inspektør.also {
            assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(1.vedtaksperiode))
            assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
            assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(2.vedtaksperiode))
            assertTrue(inspektør.periodeErForkastet(2.vedtaksperiode))
            assertEquals(TilstandType.TIL_INFOTRYGD, inspektør.sisteTilstand(3.vedtaksperiode))
            assertTrue(inspektør.periodeErForkastet(3.vedtaksperiode))
            assertEquals(Behovtype.Utbetaling, it.personLogg.behov().last().type)
            assertTrue(it.utbetalinger.last().erAnnullert())
            assertTrue(it.utbetalinger.last().erUtbetalt())
        }
    }

    @Test
    fun `Periode som håndterer godkjent annullering i TilAnnullering blir forkastet`() {
        nyttVedtak(3.januar, 26.januar, 100, 3.januar)
        håndterAnnullerUtbetaling()
        inspektør.also {
            assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(1.vedtaksperiode))
            assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
        }
        håndterUtbetalt(1.vedtaksperiode, status = UtbetalingHendelse.Oppdragstatus.AKSEPTERT, annullert = true)
        inspektør.also {
            assertFalse(it.personLogg.hasErrorsOrWorse(), it.personLogg.toString())
            assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(1.vedtaksperiode))
            assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
        }
    }

    @Test
    fun `Periode som håndterer avvist annullering i TilAnnullering blir værende i TilAnnullering`() {
        nyttVedtak(3.januar, 26.januar, 100, 3.januar)
        håndterAnnullerUtbetaling(fagsystemId = inspektør.fagsystemId(1.vedtaksperiode))
        inspektør.also {
            assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(1.vedtaksperiode))
            assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
        }
        håndterUtbetalt(1.vedtaksperiode, status = UtbetalingHendelse.Oppdragstatus.AVVIST, annullert = true)
        inspektør.also {
            assertTrue(it.personLogg.hasErrorsOrWorse())
            assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(1.vedtaksperiode))
            assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
        }
    }

    @Disabled("Slik skal det virke etter at annullering trigger replay")
    @Test
    fun `Annullering av én periode fører kun til at sammehengende perioder blir satt i tilstand TilInfotrygd`() {
        nyttVedtak(3.januar, 26.januar, 100, 3.januar)
        forlengVedtak(27.januar, 30.januar, 100)
        nyttVedtak(1.mars, 20.mars, 100, 1.mars)
        inspektør.also {
            assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(1.vedtaksperiode))
            assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(2.vedtaksperiode))
            assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(3.vedtaksperiode))
        }
        val behovTeller = inspektør.personLogg.behov().size
        håndterAnnullerUtbetaling(fagsystemId = inspektør.arbeidsgiverOppdrag.first().fagsystemId())
        inspektør.also {
            assertFalse(it.personLogg.hasErrorsOrWorse(), it.personLogg.toString())
            assertEquals(1, it.personLogg.behov().size - behovTeller, it.personLogg.toString())
            assertEquals(TilstandType.TIL_INFOTRYGD, inspektør.sisteTilstand(1.vedtaksperiode))
            assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
            assertEquals(TilstandType.TIL_INFOTRYGD, inspektør.sisteTilstand(2.vedtaksperiode))
            assertTrue(inspektør.periodeErForkastet(2.vedtaksperiode))
            assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(3.vedtaksperiode))
        }
    }

    @Test
    fun `publiserer et event ved annullering`() {
        nyttVedtak(3.januar, 26.januar, 100, 3.januar)
        håndterAnnullerUtbetaling(fagsystemId = inspektør.fagsystemId(1.vedtaksperiode))
        håndterUtbetalt(
            1.vedtaksperiode,
            status = UtbetalingHendelse.Oppdragstatus.AKSEPTERT,
            saksbehandlerEpost = "tbd@nav.no",
            annullert = true
        )

        val annullering = observatør.annulleringer.lastOrNull()
        assertNotNull(annullering)

        assertEquals(inspektør.fagsystemId(1.vedtaksperiode), annullering!!.fagsystemId)

        val utbetalingslinje = annullering.utbetalingslinjer.first()
        assertEquals("tbd@nav.no", annullering.saksbehandlerEpost)
        assertEquals(19.januar, utbetalingslinje.fom)
        assertEquals(26.januar, utbetalingslinje.tom)
        assertEquals(8586, utbetalingslinje.beløp)
        assertEquals(100.0, utbetalingslinje.grad)
    }

    @Test
    fun `publiserer kun ett event ved annullering av utbetaling som strekker seg over flere vedtaksperioder`() {
        nyttVedtak(3.januar, 26.januar, 100, 3.januar)
        val fagsystemId = inspektør.fagsystemId(1.vedtaksperiode)
        forlengVedtak(27.januar, 20.februar, 100)
        assertEquals(2, inspektør.vedtaksperiodeTeller)

        håndterAnnullerUtbetaling(fagsystemId = fagsystemId)
        håndterUtbetalt(
            1.vedtaksperiode,
            status = UtbetalingHendelse.Oppdragstatus.AKSEPTERT,
            saksbehandlerEpost = "tbd@nav.no",
            annullert = true
        )

        assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(1.vedtaksperiode))
        assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(2.vedtaksperiode))

        val annulleringer = observatør.annulleringer
        assertEquals(1, annulleringer.size)
        val annullering = annulleringer.lastOrNull()
        assertNotNull(annullering)

        assertEquals(fagsystemId, annullering!!.fagsystemId)

        val utbetalingslinje = annullering.utbetalingslinjer.first()
        assertEquals("tbd@nav.no", annullering.saksbehandlerEpost)
        assertEquals(19.januar, utbetalingslinje.fom)
        assertEquals(20.februar, utbetalingslinje.tom)
        assertEquals(32913, utbetalingslinje.beløp)
        assertEquals(100.0, utbetalingslinje.grad)
    }


    private inner class TestOppdragInspektør(oppdrag: Oppdrag) : OppdragVisitor {
        val oppdrag = mutableListOf<Oppdrag>()
        val linjer = mutableListOf<Utbetalingslinje>()
        val endringskoder = mutableListOf<Endringskode>()
        val fagsystemIder = mutableListOf<String?>()
        val refFagsystemIder = mutableListOf<String?>()

        init {
            oppdrag.accept(this)
        }

        override fun preVisitOppdrag(
            oppdrag: Oppdrag,
            totalBeløp: Int,
            nettoBeløp: Int,
            tidsstempel: LocalDateTime,
            utbetalingtilstand: Oppdrag.Utbetalingtilstand
        ) {
            this.oppdrag.add(oppdrag)
            fagsystemIder.add(oppdrag.fagsystemId())
        }

        override fun visitUtbetalingslinje(
            linje: Utbetalingslinje,
            fom: LocalDate,
            tom: LocalDate,
            beløp: Int?,
            aktuellDagsinntekt: Int,
            grad: Double,
            delytelseId: Int,
            refDelytelseId: Int?,
            refFagsystemId: String?,
            endringskode: Endringskode,
            datoStatusFom: LocalDate?
        ) {
            linjer.add(linje)
            endringskoder.add(endringskode)
            refFagsystemIder.add(refFagsystemId)
        }

    }
}