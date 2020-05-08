package no.nav.helse.sykdomstidslinje

import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.contains
import no.nav.helse.person.IAktivitetslogg
import no.nav.helse.person.NySykdomstidslinjeVisitor
import no.nav.helse.sykdomstidslinje.NyDag.*
import no.nav.helse.sykdomstidslinje.NyDag.Companion.default
import no.nav.helse.sykdomstidslinje.NyDag.Companion.noOverlap
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse.Hendelseskilde
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse.Hendelseskilde.Companion.INGEN
import no.nav.helse.sykdomstidslinje.dag.erHelg
import no.nav.helse.sykdomstidslinje.dag.harTilstøtende
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.stream.Collectors.toMap

internal class NySykdomstidslinje private constructor(
    private val dager: SortedMap<LocalDate, NyDag>,
    periode: Periode? = null,
    private val låstePerioder: MutableList<Periode> = mutableListOf(),
    private val id: UUID = UUID.randomUUID(),
    private val tidsstempel: LocalDateTime = LocalDateTime.now()
) : Iterable<NyDag> {

    private val periode: Periode?

    init {
        this.periode = periode ?: periode(dager)
    }

    internal constructor(dager: Map<LocalDate, NyDag> = emptyMap()) : this(
        dager.toSortedMap()
    )

    private constructor(dager: List<Pair<LocalDate, NyDag>>) : this(
        dager.fold(mutableMapOf<LocalDate, NyDag>()) { acc, (date, dag) ->
            acc.apply { put(date, dag) }
        }
    )


    internal fun length() = count() // Added to limit number of changes when removing old sykdomstidlinje
    internal fun periode() = periode
    internal fun førsteDag() = periode!!.start
    internal fun sisteDag() = periode!!.endInclusive

    internal fun overlapperMed(other: NySykdomstidslinje) =
        when {
            this.count() == 0 && other.count() == 0 -> true
            this.count() == 0 || other.count() == 0 -> false
            else -> this.overlapp(other)
        }

    private fun overlapp(other: NySykdomstidslinje): Boolean {
        requireNotNull(periode, { "Kan ikke undersøke overlapping med tom sykdomstidslinje"})
        requireNotNull(other.periode, { "Kan ikke undersøke overlapping med tom sykdomstidslinje"})

        return periode.overlapperMed(other.periode)
    }

    internal fun merge(other: NySykdomstidslinje, beste: BesteStrategy = default): NySykdomstidslinje {
        val dager = mutableMapOf<LocalDate, NyDag>()
        this.dager.toMap(dager)
        other.dager.filter { it.key !in låstePerioder }.forEach { (dato, dag) -> dager.merge(dato, dag, beste) }
        return NySykdomstidslinje(
            dager.toSortedMap(),
            this.periode?.merge(other.periode) ?: other.periode,
            this.låstePerioder
        )
    }

    private class ProblemDagVisitor(internal val problemmeldinger: MutableList<String>) : NySykdomstidslinjeVisitor {
        override fun visitDag(
            dag: ProblemDag,
            dato: LocalDate,
            kilde: Hendelseskilde,
            melding: String
        ) {
            problemmeldinger.add(melding)
        }
    }

    internal fun valider(aktivitetslogg: IAktivitetslogg): Boolean {
        val problemmeldinger = mutableListOf<String>()
        val visitor = ProblemDagVisitor(problemmeldinger)
        dager.values.filter { it::class == ProblemDag::class }
            .forEach { it.accept(visitor) }

        return problemmeldinger
            .distinct()
            .onEach {
                aktivitetslogg.error(
                    "Sykdomstidslinjen inneholder ustøttet dag. Problem oppstått fordi: %s",
                    it
                )
            }
            .isEmpty()
    }

    internal operator fun plus(other: NySykdomstidslinje) = this.merge(other)
    internal operator fun get(dato: LocalDate): NyDag = dager[dato] ?: NyUkjentDag(dato, INGEN)

    internal fun harTilstøtende(other: NySykdomstidslinje) = this.sisteDag().harTilstøtende(other.førsteDag())

    internal fun subset(periode: Periode) =
        NySykdomstidslinje(dager.filter { it.key in periode }.toSortedMap(), periode)

    /**
     * Without padding of days
     */
    internal fun kutt(kuttDatoInclusive: LocalDate) =
        when {
            periode == null -> this
            kuttDatoInclusive < periode.start -> NySykdomstidslinje()
            kuttDatoInclusive > periode.endInclusive -> this
            else -> subset(Periode(periode.start, kuttDatoInclusive))
        }

    internal fun lås(periode: Periode) = this.also {
        requireNotNull(this.periode)
        require(periode in this.periode)
        låstePerioder.add(periode)
    }

    /**
     * Støtter kun å låse opp de perioder som tidligere har blitt låst
     */
    internal fun låsOpp(periode: Periode) = this.also {
        låstePerioder.removeIf { it == periode } || throw IllegalArgumentException("Kan ikke låse opp periode $periode")
    }

    internal fun førsteFraværsdag(): LocalDate? {
        return førsteSykedagDagEtterSisteIkkeSykedag() ?: førsteSykedag()
    }

    /**
     * Første fraværsdag i siste sammenhengende sykefravær i perioden
     */
    private fun førsteSykedagDagEtterSisteIkkeSykedag() =
        fjernDagerEtterSisteSykedag().let { tidslinje ->
            tidslinje.periode?.lastOrNull { this[it] is NyArbeidsdag || this[it] is NyFriskHelgedag || this[it] is NyUkjentDag }
                ?.let { ikkeSykedag ->
                    tidslinje.dager.entries.firstOrNull {
                        it.key.isAfter(ikkeSykedag) && erEnSykedag(it.value)
                    }?.key
                }
        }

    private fun førsteSykedag() = dager.entries.firstOrNull { erEnSykedag(it.value) }?.key

    private fun fjernDagerEtterSisteSykedag(): NySykdomstidslinje = periode
        ?.findLast { erEnSykedag(this[it]) }
        ?.let { this.subset(Periode(dager.firstKey(), it)) } ?: NySykdomstidslinje()


    private fun erEnSykedag(it: NyDag) =
        it is NySykedag || it is NySykHelgedag || it is NyArbeidsgiverdag || it is NyArbeidsgiverHelgedag || it is NyForeldetSykedag

    internal fun accept(visitor: NySykdomstidslinjeVisitor) {
        visitor.preVisitNySykdomstidslinje(this, låstePerioder, id, tidsstempel)
        periode?.forEach { this[it].accept(visitor) }
        visitor.postVisitNySykdomstidslinje(this, id, tidsstempel)
    }

    override fun toString() = toShortString()

    internal fun toShortString(): String {
        return periode?.joinToString(separator = "") {
            (if (it.dayOfWeek == DayOfWeek.MONDAY) " " else "") +
                when (this[it]::class) {
                    NySykedag::class -> "S"
                    NyArbeidsdag::class -> "A"
                    NyUkjentDag::class -> "?"
                    ProblemDag::class -> "X"
                    NySykHelgedag::class -> "H"
                    NyArbeidsgiverdag::class -> "U"
                    NyArbeidsgiverHelgedag::class -> "G"
                    NyFeriedag::class -> "F"
                    NyFriskHelgedag::class -> "R"
                    NyForeldetSykedag::class -> "K"
                    else -> "*"
                }
        } ?: "Tom tidslinje"
    }

    internal companion object {

        private fun periode(dager: SortedMap<LocalDate, NyDag>) =
            if (dager.size > 0) Periode(dager.firstKey(), dager.lastKey()) else null

        internal fun arbeidsdager(periode: Periode?, kilde: Hendelseskilde) =
            NySykdomstidslinje(
                periode?.map { it to if (it.erHelg()) NyFriskHelgedag(it, kilde) else NyArbeidsdag(it, kilde) } ?: emptyList<Pair<LocalDate, NyDag>>()
            )

        internal fun arbeidsdager(førsteDato: LocalDate, sisteDato: LocalDate, kilde: Hendelseskilde) =
            arbeidsdager(Periode(førsteDato, sisteDato), kilde)

        internal fun sykedager(
            førsteDato: LocalDate,
            sisteDato: LocalDate,
            grad: Number = 100.0,
            kilde: Hendelseskilde
        ) =
            NySykdomstidslinje(
                førsteDato.datesUntil(sisteDato.plusDays(1))
                    .collect(
                        toMap<LocalDate, LocalDate, NyDag>(
                            { it },
                            { if (it.erHelg()) NySykHelgedag(it, grad, kilde) else NySykedag(it, grad, kilde) })
                    )
            )

        internal fun sykedager(
            førsteDato: LocalDate,
            sisteDato: LocalDate,
            avskjæringsdato: LocalDate,
            grad: Number = 100.0,
            kilde: Hendelseskilde
        ) =
            NySykdomstidslinje(
                førsteDato.datesUntil(sisteDato.plusDays(1))
                    .collect(
                        toMap<LocalDate, LocalDate, NyDag>(
                            { it },
                            {
                                if (it.erHelg()) NySykHelgedag(it, grad, kilde) else sykedag(
                                    it,
                                    avskjæringsdato,
                                    grad,
                                    kilde
                                )
                            })
                    )
            )

        private fun sykedag(
            dato: LocalDate,
            avskjæringsdato: LocalDate,
            grad: Number,
            kilde: Hendelseskilde
        ) = if (dato < avskjæringsdato) NyForeldetSykedag(dato, grad, kilde) else NySykedag(dato, grad, kilde)

        internal fun foreldetSykedag(
            førsteDato: LocalDate,
            sisteDato: LocalDate,
            grad: Number = 100.0,
            kilde: Hendelseskilde
        ) =
            NySykdomstidslinje(
                førsteDato.datesUntil(sisteDato.plusDays(1))
                    .collect(
                        toMap<LocalDate, LocalDate, NyDag>(
                            { it },
                            { if (it.erHelg()) NySykHelgedag(it, grad, kilde) else NyForeldetSykedag(it, grad, kilde) })
                    )
            )

        internal fun arbeidsgiverdager(
            førsteDato: LocalDate,
            sisteDato: LocalDate,
            grad: Number = 100.0,
            kilde: Hendelseskilde
        ) =
            NySykdomstidslinje(
                førsteDato.datesUntil(sisteDato.plusDays(1))
                    .collect(
                        toMap<LocalDate, LocalDate, NyDag>(
                            { it },
                            {
                                if (it.erHelg()) NyArbeidsgiverHelgedag(it, grad, kilde)
                                else NyArbeidsgiverdag(it, grad, kilde)
                            })
                    )
            )

        internal fun feriedager(
            førsteDato: LocalDate,
            sisteDato: LocalDate,
            kilde: Hendelseskilde
        ) =
            NySykdomstidslinje(
                førsteDato.datesUntil(sisteDato.plusDays(1))
                    .collect(toMap<LocalDate, LocalDate, NyDag>({ it }, { NyFeriedag(it, kilde) }))
            )

        internal fun problemdager(
            førsteDato: LocalDate,
            sisteDato: LocalDate,
            kilde: Hendelseskilde,
            melding: String
        ) =
            NySykdomstidslinje(
                førsteDato.datesUntil(sisteDato.plusDays(1))
                    .collect(toMap<LocalDate, LocalDate, NyDag>({ it }, { ProblemDag(it, kilde, melding) }))
            )
    }

    override operator fun iterator() = object : Iterator<NyDag> {
        private val periodeIterator = periode?.iterator()

        override fun hasNext() = periodeIterator?.hasNext() ?: false

        override fun next() =
            periodeIterator?.let { this@NySykdomstidslinje[it.next()] }
                ?: throw NoSuchElementException()
    }

    fun starterFør(other: NySykdomstidslinje): Boolean {
        requireNotNull(periode)
        requireNotNull(other.periode)
        return periode.start < other.periode.start
    }
}

internal fun List<NySykdomstidslinje>.merge(beste: BesteStrategy = default): NySykdomstidslinje =
    if (this.isEmpty()) NySykdomstidslinje()
    else reduce { result, tidslinje -> result.merge(tidslinje, beste) }

internal fun List<NySykdomstidslinje>.join() = merge(noOverlap)
