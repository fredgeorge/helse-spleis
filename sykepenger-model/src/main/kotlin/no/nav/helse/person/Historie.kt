package no.nav.helse.person

import no.nav.helse.hendelser.Utbetalingshistorikk
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Dag.Companion.replace
import no.nav.helse.sykdomstidslinje.Dag.UkjentDag
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse.Hendelseskilde.Companion.INGEN
import no.nav.helse.sykdomstidslinje.erHelg
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.*
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.NavDag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.NavHelgDag
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import no.nav.helse.økonomi.Økonomi
import java.time.LocalDate

internal class Historie(
    utbetalingshistorikk: Utbetalingshistorikk
) {

    private companion object {
        private const val ALLE_ARBEIDSGIVERE = "UKJENT"
    }

    private val infotrygdbøtte = Historikkbøtte()
    private val spleisbøtte = Historikkbøtte()
    private val sykdomstidslinjer get() = infotrygdbøtte.sykdomstidslinjer() + spleisbøtte.sykdomstidslinjer()

    init {
        utbetalingshistorikk.append(infotrygdbøtte)
    }

    internal fun skjæringstidspunkt(tom: LocalDate) = Sykdomstidslinje.skjæringstidspunkt(tom, sykdomstidslinjer)

    internal class Historikkbøtte {
        private val utbetalingstidslinjer = mutableMapOf<String, Utbetalingstidslinje>()
        private val sykdomstidslinjer = mutableMapOf<String, Sykdomstidslinje>()

        internal fun sykdomstidslinjer() = sykdomstidslinjer.values

        internal fun add(orgnummer: String = ALLE_ARBEIDSGIVERE, tidslinje: Utbetalingstidslinje) {
            utbetalingstidslinjer.merge(orgnummer, tidslinje, Utbetalingstidslinje::plus)
        }

        internal fun add(orgnummer: String = ALLE_ARBEIDSGIVERE, tidslinje: Sykdomstidslinje) {
            sykdomstidslinjer.merge(orgnummer, tidslinje) { venstre, høyre -> venstre.merge(høyre, replace) }
        }

        internal fun konverter(utbetalingstidslinje: Utbetalingstidslinje) =
            Sykdomstidslinje(utbetalingstidslinje.map {
                it.dato to when {
                    !it.dato.erHelg() && it.erSykedag() -> Dag.Sykedag(it.dato, it.økonomi.medGrad(), INGEN)
                    it.dato.erHelg() && it.erSykedag() -> Dag.SykHelgedag(it.dato, it.økonomi.medGrad(), INGEN)
                    else -> UkjentDag(it.dato, INGEN)
                }
            }.associate { it })

        private fun Utbetalingsdag.erSykedag() = this is NavDag || this is NavHelgDag || this is ArbeidsgiverperiodeDag
        private fun Økonomi.medGrad() = Økonomi.sykdomsgrad(reflection { grad, _, _, _, _, _, _ -> grad }.prosent)
    }
}