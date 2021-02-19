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
    private val fridager = mutableListOf<LocalDate>()
    private var tilstand: Tilstand = IngenArbeidsgiverperiode

    internal fun result(sykdomstidslinje: Sykdomstidslinje): List<Arbeidsgiverperiode> {
        sykdomstidslinje.accept(this)
        return arbeidsgiverperioder
    }

    override fun nyArbeidsgiverperiode() {
        tilstand = IngenArbeidsgiverperiode
    }

    override fun arbeidsgiverperiodeFri() {
        tilstand = ArbeidsgiverperiodeFri
    }

    override fun arbeidsgiverperiodeOpphold() {
        tilstand = ArbeidsgiverperiodeOpphold
        fridager.clear()
    }

    override fun arbeidsgiverperiodeFriFerdig() {
        tilstand = ArbeidsgiverperiodeFriFerdig
    }

    override fun arbeidsgiverperiodeFerdig() {
        fridager.forEach { arbeidsgiverperioder.last().nyDag(it) }
        fridager.clear()
        tilstand = IngenArbeidsgiverperiode
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
            builder.tilstand = IArbeidsgiverperiode
            builder.arbeidsgiverperioder.add(Arbeidsgiverperiode(dato))
        }
    }
    private object ArbeidsgiverperiodeOpphold : Tilstand {
        override fun arbeidsgiverperiode(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate) {
            builder.tilstand = IArbeidsgiverperiode
            builder.arbeidsgiverperioder.last().nyPeriode(dato)
        }
    }
    private object ArbeidsgiverperiodeFri : Tilstand {
        override fun fridag(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate) {
            builder.fridager.add(dato)
        }

        override fun arbeidsgiverperiode(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate) {
            builder.tilstand = IArbeidsgiverperiode
            builder.fridager.forEach { builder.addArbeidsdag(it) }
            builder.fridager.clear()
        }
    }
    private object ArbeidsgiverperiodeFriFerdig : Tilstand {}
    private object IArbeidsgiverperiode : Tilstand {
        override fun arbeidsgiverperiode(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate) {
            builder.arbeidsgiverperioder.last().nyDag(dato)
        }
    }
}
