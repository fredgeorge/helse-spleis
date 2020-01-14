package no.nav.helse.utbetalingstidslinje

import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.*
import java.time.LocalDate

internal class MinimumInntektsfilter (
    private val alder: Alder,
    private val tidslinjer: List<Utbetalingstidslinje>
): Utbetalingstidslinje.UtbetalingsdagVisitor {

    private var inntekter = mutableMapOf<LocalDate, Double>()

    internal fun filter() {
        tidslinjer.forEach { it.accept(this) }
        inntekter = inntekter
            .filter { (dato, inntekt) -> inntekt < alder.minimumInntekt(dato) }.toMutableMap()

        tidslinjer.forEach { it.avvis(inntekter.keys.toList(), Begrunnelse.MinimumInntekt) }
    }

    override fun visitNavDag(dag: NavDag) {
        addInntekt(dag)
    }

    override fun visitArbeidsdag(dag: Arbeidsdag) {
        addInntekt(dag)
    }

    override fun visitArbeidsgiverperiodeDag(dag: ArbeidsgiverperiodeDag) {
        addInntekt(dag)
    }

    override fun visitFridag(dag: Fridag) {
        addInntekt(dag)
    }

    private fun addInntekt(dag: Utbetalingstidslinje.Utbetalingsdag) {
        inntekter[dag.dato] = inntekter[dag.dato]?.plus(dag.inntekt) ?: dag.inntekt
    }

}