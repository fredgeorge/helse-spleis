package no.nav.helse.utbetalingstidslinje

import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler.Companion.NormalArbeidstaker
import no.nav.helse.økonomi.Økonomi
import java.time.LocalDate

internal class ArbeidsgiverperiodeBuilder(
    forlengelseStrategy: (LocalDate) -> Boolean = { false },
    arbeidsgiverRegler: ArbeidsgiverRegler = NormalArbeidstaker
) : AbstractUtbetalingstidslinjeBuilder(forlengelseStrategy, arbeidsgiverRegler) {

    private val arbeidsgiverperioder = mutableListOf<Arbeidsgiverperiode>()
    private var sisteFridag: LocalDate? = null
    private var tilstand: Tilstand = IngenArbeidsgiverperiode

    internal fun result(sykdomstidslinje: Sykdomstidslinje): List<Arbeidsgiverperiode> {
        sykdomstidslinje.accept(this)
        return arbeidsgiverperioder
    }

    override fun reset() {
        tilstand = IngenArbeidsgiverperiode
        sisteFridag = null
    }

    override fun arbeidsgiverperiodeFri() {
        tilstand = ArbeidsgiverperiodeFri
    }

    override fun arbeidsgiverperiodeOpphold() {
        tilstand = HarArbeidsgiverperiode
    }

    override fun arbeidsgiverperiodeMuligGjennomførtMedFri() {
        tilstand = ArbeidsgiverperiodeMuligGjennomførtMedFri
    }

    override fun arbeidsgiverperiodeGjennomført() {
        sisteFridag?.also { arbeidsgiverperioder.last().utvidSiste(it) }
        reset()
    }

    override fun addArbeidsgiverdag(dato: LocalDate) {
        tilstand.arbeidsgiverperiode(this, dato)
    }

    override fun addFridag(dato: LocalDate) {
        tilstand.fridag(this, dato)
    }

    override fun addForeldetDag(dagen: LocalDate, økonomi: Økonomi) {}
    override fun addNAVdag(dato: LocalDate, økonomi: Økonomi) {}
    override fun addNAVHelgedag(dato: LocalDate, økonomi: Økonomi) {}
    override fun addArbeidsdag(dato: LocalDate) {}
    override fun addAvvistDag(dato: LocalDate) {}

    private interface Tilstand {
        fun arbeidsgiverperiode(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate) {}
        fun fridag(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate) {}
    }
    private object IngenArbeidsgiverperiode : Tilstand {
        override fun arbeidsgiverperiode(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate) {
            builder.tilstand = HarArbeidsgiverperiode
            builder.arbeidsgiverperioder.add(Arbeidsgiverperiode(dato))
        }
    }
    private object ArbeidsgiverperiodeFri : Tilstand {
        override fun fridag(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate) {
            builder.sisteFridag = dato
        }

        override fun arbeidsgiverperiode(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate) {
            builder.tilstand = HarArbeidsgiverperiode
            builder.arbeidsgiverperioder.last().utvidSiste(dato)
        }
    }
    private object ArbeidsgiverperiodeMuligGjennomførtMedFri : Tilstand {}
    private object HarArbeidsgiverperiode : Tilstand {
        override fun arbeidsgiverperiode(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate) {
            builder.arbeidsgiverperioder.last().nyDag(dato)
        }
    }
}
