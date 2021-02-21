package no.nav.helse.utbetalingstidslinje

import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.testhelpers.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class ArbeidsgiverperiodeBuilderTest {
    @BeforeEach
    internal fun reset() {
        resetSeed()
    }

    @AfterEach
    fun after() {
        val a = 1
    }

    private fun assertArbeidsgiverperiode(indeks: Int, vararg dato: LocalDate) {
        assertEquals(dato.toList(), arbeidsgiverperioder[indeks].toList())
    }

    private fun assertArbeidsgiverperiode(indeks: Int, vararg periode: Periode) {
        assertEquals(periode.flatMap { it.toList() }, arbeidsgiverperioder[indeks].toList())
    }

    @Test
    fun `to dager blir betalt av arbeidsgiver`() {
        2.S.arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar, 2.januar)
    }


    @Test
    fun `sykedager i periode som starter i helg får riktig inntekt`() {
        resetSeed(6.januar)
        (16.S + 4.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 6.januar til 21.januar)
    }

    @Test
    fun `en utbetalingslinje med tre dager`() {
        (16.S + 3.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
    }

    @Test
    fun `en utbetalingslinje med helg`() {
        (16.S + 6.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
    }

    @Test
    fun `utbetalingstidslinjer kan starte i helg`() {
        (3.A + 16.S + 6.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 4.januar til 19.januar)
    }

    @Test
    fun `Sykedager med inneklemte arbeidsdager`() {
        (16.S + 7.S + 2.A + 1.S).arbeidsgiverperioder() //6 utbetalingsdager
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
    }

    @Test
    fun `Arbeidsdager i arbeidsgiverperioden`() {
        (15.S + 2.A + 1.S + 7.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 15.januar, 18.januar til 18.januar)
    }

    @Test
    fun `Ferie i arbeidsgiverperiode`() {
        (1.S + 2.F + 13.S + 1.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
    }

    @Test
    fun `Arbeidsdag etter ferie i arbeidsgiverperioden`() {
        (1.S + 2.F + 1.A + 1.S + 14.S + 3.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 1.januar, 5.januar til 19.januar)
    }

    @Test
    fun `Arbeidsdag før ferie i arbeidsgiverperioden`() {
        (1.S + 1.A + 2.F + 1.S + 14.S + 3.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 1.januar, 5.januar til 19.januar)
    }

    @Test
    fun `Ferie etter arbeidsgiverperioden`() {
        (16.S + 2.F + 1.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
    }

    @Test
    fun `Arbeidsdag etter ferie i arbeidsgiverperiode teller som gap, men ikke ferie`() {
        (15.S + 2.F + 1.A + 1.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 15.januar, 19.januar til 19.januar)
    }

    @Test
    fun `Ferie rett etter arbeidsgiverperioden teller ikke som opphold`() {
        (16.S + 16.F + 1.A + 3.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
    }

    @Test
    fun `Ferie i slutten av arbeidsgiverperioden teller som opphold`() {
        (15.S + 16.F + 1.A + 3.S).arbeidsgiverperioder()
        assertEquals(2, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 15.januar)
        assertArbeidsgiverperiode(1, 2.februar til 4.februar)
    }

    @Test
    fun `Ferie og arbeid påvirker ikke initiell tilstand`() {
        (2.F + 2.A + 16.S + 2.F).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 5.januar til 20.januar)
    }

    @Test
    fun `Arbeidsgiverperioden resettes når det er opphold over 16 dager`() {
        (10.S + 20.F + 1.A + 10.S + 20.F).arbeidsgiverperioder()
        assertEquals(2, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 10.januar)
        assertArbeidsgiverperiode(1, 1.februar til 10.februar)
    }

    @Test
    fun `Ferie fullfører arbeidsgiverperioden`() {
        (10.S + 20.F + 10.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
    }

    @Test
    fun `Ferie mer enn 16 dager gir ikke ny arbeidsgiverperiode`() {
        (20.S + 20.F + 10.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
    }

    @Test
    fun `egenmelding sammen med sykdom oppfører seg som sykdom`() {
        (5.U + 15.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
    }

    @Test
    fun `16 dagers opphold etter en utbetaling gir ny arbeidsgiverperiode ved påfølgende sykdom`() {
        (22.S + 16.A + 10.S).arbeidsgiverperioder()
        assertEquals(2, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
        assertArbeidsgiverperiode(1, 8.februar til 17.februar)
    }

    @Test
    fun `Ferie i arbeidsgiverperioden direkte etterfulgt av en arbeidsdag gjør at ferien teller som opphold`() {
        (10.S + 15.F + 1.A + 10.S).arbeidsgiverperioder()
        assertEquals(2, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 10.januar)
        assertArbeidsgiverperiode(1, 27.januar til 5.februar)
    }

    @Test
    fun `Ferie etter arbeidsdag i arbeidsgiverperioden gjør at ferien teller som opphold`() {
        (10.S + 1.A + 15.F + 10.S).arbeidsgiverperioder()
        assertEquals(2, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 10.januar)
        assertArbeidsgiverperiode(1, 27.januar til 5.februar)
    }

    @Test
    fun `Ferie direkte etter arbeidsgiverperioden teller ikke som opphold, selv om det er en direkte etterfølgende arbeidsdag`() {
        (16.S + 15.F + 1.A + 10.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
    }

    @Test
    fun `Ferie direkte etter en sykedag utenfor arbeidsgiverperioden teller ikke som opphold, selv om det er en direkte etterfølgende arbeidsdag`() {
        (20.S + 15.F + 1.A + 10.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
    }

    @Test
    fun `Ferie direkte etter en arbeidsdag utenfor arbeidsgiverperioden teller som opphold`() {
        (21.S + 1.A + 15.F + 10.S).arbeidsgiverperioder()
        assertEquals(2, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
        assertArbeidsgiverperiode(1, 7.februar til 16.februar)
    }

    @Test
    fun `Ferie direkte etter en sykedag utenfor arbeidsgiverperioden teller ikke som opphold, mens ferie direkte etter en arbeidsdag utenfor arbeidsgiverperioden teller som opphold, så A + 15F gir ett opphold på 16 dager og dette resulterer i to arbeidsgiverperioder`() {
        (17.S + 4.F + 1.A + 15.F + 10.S).arbeidsgiverperioder()
        assertEquals(2, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
        assertArbeidsgiverperiode(1, 7.februar til 16.februar)
    }

    @Test
    fun `Ferie direkte etter en sykedag utenfor arbeidsgiverperioden teller ikke som opphold, mens ferie direkte etter en arbeidsdag utenfor arbeidsgiverperioden teller som opphold, så A + 13F gir ett opphold på 14 dager og dette resulterer i én arbeidsgiverperiode`() {
        (17.S + 4.F + 1.A + 13.F + 10.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
    }

    @Test
    fun `arbeidsgiverperiode med tre påfølgende sykedager i helg`() {
        (3.A + 19.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 4.januar til 19.januar)
    }

    @Test
    fun `arbeidsgiverperioden slutter på en fredag`() {
        (3.A + 5.S + 2.F + 13.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 4.januar til 19.januar)
    }

    @Test
    fun `ferie før arbeidsdag etter arbeidsgiverperioden teller ikke som opphold`() {
        (16.S + 6.S + 16.F + 1.A + 16.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
    }

    @Test
    fun `ta hensyn til en andre arbeidsgiverperiode, arbeidsdageropphold`() {
        (16.S + 6.S + 16.A + 16.S).arbeidsgiverperioder()
        assertEquals(2, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
        assertArbeidsgiverperiode(1, 8.februar til 23.februar)
    }

    @Test
    fun `resetter arbeidsgiverperioden etter 16 arbeidsdager`() {
        (15.S + 16.A + 14.S).arbeidsgiverperioder()
        assertEquals(2, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 15.januar)
        assertArbeidsgiverperiode(1, 1.februar til 14.februar)
    }

    @Test
    fun `siste dag i arbeidsgiverperioden faller på mandag`() {
        (1.S + 3.A + 4.S + 3.A + 11.S + 4.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 1.januar, 5.januar til 8.januar, 12.januar til 22.januar)
    }

    @Test
    fun `siste dag i arbeidsgiverperioden faller på søndag`() {
        (1.S + 3.A + 4.S + 2.A + 12.S + 4.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 1.januar, 5.januar til 8.januar, 11.januar til 21.januar)
    }

    @Test
    fun `siste dag i arbeidsgiverperioden faller på lørdag`() {
        (1.S + 3.A + 4.S + 1.A + 13.S + 4.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 1.januar, 5.januar til 8.januar, 10.januar til 20.januar)
    }

    @Test
    fun `ForeldetSykedag godkjennes som ArbeidsgverperiodeDag`() {
        (10.K + 6.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
    }

    @Test
    fun `ForeldetSykedag blir ForeldetDag utenfor arbeidsgiverperioden`() {
        (20.K).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
    }

    @Test
    fun `feriedag før siste arbeidsgiverperiodedag`() {
        (15.U + 1.F + 1.U + 10.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
    }

    @Test
    fun `feriedag før siste arbeidsgiverperiodedag med påfølgende helg`() {
        resetSeed(1.januar(2020))
        (10.U + 7.F + 14.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar(2020) til 16.januar(2020))
    }

    @Test
    fun `opphold i arbeidsgiverperioden`() {
        resetSeed(1.januar(2020))
        (1.S + 11.A + 21.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar(2020) til 1.januar(2020), 13.januar(2020) til 27.januar(2020))
    }

    @Test
    fun `opphold etter arbeidsgiverperiode i helg`() {
        resetSeed(3.januar(2020))
        (16.U + 1.R + 2.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 3.januar(2020) til 18.januar(2020))
    }

    @Test
    fun `opphold i arbeidsgiverperiode`() {
        resetSeed(4.januar(2020))
        (16.U + 2.A + 2.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 4.januar(2020) til 19.januar(2020))
    }

    @Test
    fun `egenmeldingsdager med frisk helg gir opphold i arbeidsgiverperiode`() {
        (12.U + 2.R + 2.F + 2.U).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 12.januar, 17.januar til 18.januar)
    }

    @Test
    fun `frisk helg gir opphold i arbeidsgiverperiode`() {
        (4.U + 8.S + 2.R + 2.F + 2.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 12.januar, 17.januar til 18.januar)
    }

    @Test
    fun `Sykedag etter langt opphold nullstiller tellere`() {
        (4.S + 1.A + 2.R + 5.A + 2.R + 5.A + 2.R + 5.A + 2.R + 5.A + 2.R + 2.S + 3.A + 2.R + 18.S).arbeidsgiverperioder()
        assertEquals(2, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 4.januar)
        assertArbeidsgiverperiode(1, 5.februar til 6.februar, 12.februar til 25.februar)
    }

    @Test
    fun `Syk helgedag etter langt opphold nullstiller tellere`() {
        (3.S + 2.A + 2.R + 5.A + 2.R + 5.A + 2.R + 5.A + 2.R + 5.A + 1.R + 1.H + 1.S + 4.A + 2.R + 18.S).arbeidsgiverperioder()
        assertEquals(2, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 3.januar)
        assertArbeidsgiverperiode(1, 4.februar til 5.februar, 12.februar til 25.februar)
    }

    @Test
    fun `Sykmelding som starter i helg etter oppholdsdager gir NavHelgDag i helgen`() {
        (16.U + 2.S + 1.A + 2.R + 5.A + 2.R + 5.A + 2.H + 1.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
    }

    @Test
    fun `Etter sykdom som slutter på fredag starter gap-telling i helgen - helg som friskHelgdag`() { // Fordi vi vet når hen gjenopptok arbeidet, og det var i helgen
        (16.U + 3.S + 2.R + 5.A + 2.R + 5.A + 2.R + 18.S).arbeidsgiverperioder()
        assertEquals(2, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
        assertArbeidsgiverperiode(1, 5.februar til 20.februar)
    }

    @Test
    fun `Etter sykdom som slutter på fredag starter gap-telling mandagen etter (ikke i helgen) - helg som ukjent-dag`() { // Fordi vi ikke vet når hen gjenopptok arbeidet, men antar mandag
        (16.U + 3.S + 2.UK + 5.A + 2.R + 5.A + 2.R + 18.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
    }

    @Test
    fun `Etter sykdom som slutter på fredag starter gap-telling mandagen etter (ikke i helgen) - helg som sykhelgdag`() {
        (16.U + 3.S + 2.H + 5.A + 2.R + 5.A + 2.R + 18.S).arbeidsgiverperioder()
        assertEquals(1, arbeidsgiverperioder.size)
        assertArbeidsgiverperiode(0, 1.januar til 16.januar)
    }

    private lateinit var arbeidsgiverperioder: List<Arbeidsgiverperiode>
    private fun Sykdomstidslinje.arbeidsgiverperioder(forlengelseStrategy: (LocalDate) -> Boolean = { false }) =
        ArbeidsgiverperiodeBuilder(forlengelseStrategy = forlengelseStrategy).also { it.build(this) }.result().also {
            arbeidsgiverperioder = it
        }
}
