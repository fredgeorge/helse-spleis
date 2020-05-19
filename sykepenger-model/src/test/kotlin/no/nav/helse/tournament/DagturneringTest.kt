package no.nav.helse.tournament

import no.nav.helse.sykdomstidslinje.Dag.*
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.testhelpers.*
import no.nav.helse.testhelpers.TestEvent.Companion.inntektsmelding
import no.nav.helse.testhelpers.TestEvent.Companion.sykmelding
import no.nav.helse.testhelpers.TestEvent.Companion.søknad
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DagturneringTest {

    @Test
    internal fun `Arbeidsdag fra søknad vinner over sykedag fra sykmelding`() {
        val sykmeldingSykedag = Sykdomstidslinje.sykedager(1.mandag, 1.mandag, 100.0, sykmelding)
        val søknadArbeidsdag = Sykdomstidslinje.arbeidsdager(1.mandag, 1.mandag, søknad)

        val tidslinje = sykmeldingSykedag.merge(søknadArbeidsdag, dagturnering::beste)

        assertTrue(
            tidslinje[1.mandag] is Arbeidsdag,
            "Torsdag er en arbeidsdag etter kombinering av sykmelding og søknad"
        )
    }

    @Test
    internal fun `kombinering av tidslinjer fører til at dagsturnering slår sammen dagene`() {
        val søknadSykedager = Sykdomstidslinje.sykedager(1.mandag, 1.fredag, 100.0, søknad)
        val søknadArbeidsdager = Sykdomstidslinje.arbeidsdager(1.torsdag, 1.fredag, søknad)

        val tidslinje = søknadSykedager.merge(søknadArbeidsdager, dagturnering::beste)
        assertTrue(
            tidslinje[1.onsdag] is Sykedag,
            "Onsdag er fortsatt en sykedag etter kombinering av sykmelding og søknad"
        )
        assertTrue(
            tidslinje[1.torsdag] is Arbeidsdag,
            "Torsdag er en arbeidsdag etter kombinering av sykmelding og søknad"
        )
    }

    @Test
    internal fun `sykedag fra arbeidsgiver taper mot syk helgedag fra sykmeldingen`() {
        val sykmeldingSykHelgedag = Sykdomstidslinje.sykedager(1.søndag, 1.søndag, 100.0, sykmelding)
        val inntektsmeldingArbeidsgiverdag = Sykdomstidslinje.arbeidsgiverdager(1.søndag, 1.søndag, 100.0, inntektsmelding)

        val tidslinje = inntektsmeldingArbeidsgiverdag.merge(sykmeldingSykHelgedag, dagturnering::beste)

        assertTrue(tidslinje[1.søndag] is SykHelgedag)
    }

    @Test
    internal fun `arbeidsdag fra inntektsmelding vinner over egenmelding fra søknad`() {
        val søknadArbeidsgiverdag = Sykdomstidslinje.arbeidsgiverdager(1.mandag, 1.mandag, 100.0, søknad)
        val inntektsmeldingArbeidsdag = Sykdomstidslinje.arbeidsdager(1.mandag, 1.mandag, inntektsmelding)

        val tidslinje = søknadArbeidsgiverdag.merge(inntektsmeldingArbeidsdag, dagturnering::beste)

        assertTrue(tidslinje[1.mandag] is Arbeidsdag)
    }

    @Test
    internal fun `arbeidsdag fra inntektsmelding vinner over sykedag fra sykmelding`() {
        val sykmeldingSykedag = Sykdomstidslinje.sykedager(1.mandag, 1.mandag, 100.0, sykmelding)
        val inntektsmeldingArbeidsdag = Sykdomstidslinje.arbeidsdager(1.mandag, 1.mandag, inntektsmelding)

        val tidslinje = sykmeldingSykedag.merge(inntektsmeldingArbeidsdag, dagturnering::beste)

        assertTrue(tidslinje[1.mandag] is Arbeidsdag)
    }
}
