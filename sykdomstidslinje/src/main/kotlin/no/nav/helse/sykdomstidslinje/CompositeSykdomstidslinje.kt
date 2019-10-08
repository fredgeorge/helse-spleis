package no.nav.helse.sykdomstidslinje

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.hendelse.Sykdomshendelse
import no.nav.helse.sykdomstidslinje.dag.Dag
import no.nav.helse.sykdomstidslinje.dag.Nulldag
import java.time.LocalDate

class CompositeSykdomstidslinje(
    sykdomstidslinjer: List<Sykdomstidslinje?>
) : Sykdomstidslinje() {
    override fun accept(visitor: SykdomstidslinjeVisitor) {
        visitor.preVisitComposite(this)
        tidslinjer.forEach { it.accept(visitor) }
        visitor.postVisitComposite(this)
    }

    override fun sisteHendelse() = tidslinjer.map { it.sisteHendelse() }.maxBy { it.rapportertdato() }!!

    private val tidslinjer = sykdomstidslinjer.filterNotNull()

    override fun length() = tidslinjer.sumBy { it.length() }

    override fun dag(dato: LocalDate, hendelse: Sykdomshendelse) =
        tidslinjer.map { it.dag(dato, hendelse) }.firstOrNull { it !is Nulldag } ?: Nulldag(
            dato,
            hendelse
        )


    override fun flatten() = tidslinjer.flatMap { it.flatten() }

    override fun startdato() = tidslinjer.first().startdato()

    override fun sluttdato() = tidslinjer.last().sluttdato()

    override fun antallSykedagerHvorViIkkeTellerMedHelg() = tidslinjer.flatMap { it.flatten() }
        .sumBy { it.antallSykedagerHvorViIkkeTellerMedHelg() }

    override fun antallSykedagerHvorViTellerMedHelg() = tidslinjer.flatMap { it.flatten() }
        .sumBy { it.antallSykedagerHvorViTellerMedHelg() }

    override fun toString() = tidslinjer.joinToString(separator = "\n") { it.toString() }

    override fun toJson(): String {
        return objectMapper.writeValueAsString(flatten().map { it.jsonRepresentation() })
    }

    companion object {
        internal fun fromJson(node:JsonNode): CompositeSykdomstidslinje {
            return CompositeSykdomstidslinje(node.map { Dag.fromJson(it) })
        }
    }
}
