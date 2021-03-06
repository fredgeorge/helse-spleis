package no.nav.helse.utbetalingslinjer

import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import no.nav.helse.person.OppdragVisitor
import no.nav.helse.sykdomstidslinje.erHelg
import no.nav.helse.utbetalingslinjer.Endringskode.*
import no.nav.helse.utbetalingslinjer.Klassekode.RefusjonIkkeOpplysningspliktig
import java.time.LocalDate
import kotlin.streams.toList

internal class Utbetalingslinje internal constructor(
    internal var fom: LocalDate,
    internal var tom: LocalDate,
    internal var beløp: Int?, //TODO: arbeidsgiverbeløp || personbeløp
    internal var aktuellDagsinntekt: Int,
    internal val grad: Double,
    internal var refFagsystemId: String? = null,
    private var delytelseId: Int = 1,
    private var refDelytelseId: Int? = null,
    private var endringskode: Endringskode = NY,
    private var klassekode: Klassekode = RefusjonIkkeOpplysningspliktig,
    private var datoStatusFom: LocalDate? = null
) : Iterable<LocalDate> {

    internal val periode get() = fom til tom

    override operator fun iterator() = object : Iterator<LocalDate> {
        private val periodeIterator = periode.iterator()
        override fun hasNext() = periodeIterator.hasNext()
        override fun next() = periodeIterator.next()
    }

    internal fun accept(visitor: OppdragVisitor) {
        visitor.visitUtbetalingslinje(
            this,
            fom,
            tom,
            beløp,
            aktuellDagsinntekt,
            grad,
            delytelseId,
            refDelytelseId,
            refFagsystemId,
            endringskode,
            datoStatusFom
        )
    }

    internal fun linkTo(other: Utbetalingslinje) {
        this.delytelseId = other.delytelseId + 1
        this.refDelytelseId = other.delytelseId
    }

    internal fun datoStatusFom() = datoStatusFom
    internal fun totalbeløp() = beløp?.let { it * stønadsdager() } ?: 0
    internal fun stønadsdager() = if (!erOpphør()) filterNot(LocalDate::erHelg).size else 0

    internal fun dager() = fom
        .datesUntil(tom.plusDays(1))
        .filter { !it.erHelg() }
        .toList()

    override fun equals(other: Any?) = other is Utbetalingslinje && this.equals(other)

    private fun equals(other: Utbetalingslinje) =
        this.fom == other.fom &&
            this.tom == other.tom &&
            this.beløp == other.beløp &&
            this.grad == other.grad &&
            this.datoStatusFom == other.datoStatusFom

    internal fun kunTomForskjelligFra(other: Utbetalingslinje) =
        this.fom == other.fom &&
            this.beløp == other.beløp &&
            this.grad == other.grad &&
            this.datoStatusFom == other.datoStatusFom

    override fun hashCode(): Int {
        return fom.hashCode() * 37 +
            tom.hashCode() * 17 +
            beløp.hashCode() * 41 +
            grad.hashCode() * 61 +
            endringskode.name.hashCode() * 59 +
            datoStatusFom.hashCode() * 23
    }

    internal fun ghostFrom(tidligere: Utbetalingslinje) = copyWith(UEND, tidligere)

    internal fun utvidTom(tidligere: Utbetalingslinje) = copyWith(ENDR, tidligere)
        .also {
            this.refDelytelseId = null
            this.refFagsystemId = null
        }

    private fun copyWith(linjetype: Endringskode, tidligere: Utbetalingslinje) {
        this.refFagsystemId = tidligere.refFagsystemId
        this.delytelseId = tidligere.delytelseId
        this.refDelytelseId = tidligere.refDelytelseId
        this.klassekode = tidligere.klassekode
        this.endringskode = linjetype
        this.datoStatusFom = tidligere.datoStatusFom
    }

    internal fun erForskjell() = endringskode != UEND

    internal fun deletion(datoStatusFom: LocalDate) =
        Utbetalingslinje(
            fom = fom,
            tom = tom,
            beløp = beløp,
            aktuellDagsinntekt = aktuellDagsinntekt,
            grad = grad,
            refFagsystemId = null,
            delytelseId = delytelseId,
            refDelytelseId = null,
            endringskode = ENDR,
            datoStatusFom = datoStatusFom
        )

    internal fun erOpphør() = datoStatusFom != null
}

