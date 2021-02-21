package no.nav.helse.utbetalingstidslinje

import no.nav.helse.person.SykdomstidslinjeVisitor
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.sykdomstidslinje.erHelg
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler.Companion.NormalArbeidstaker
import no.nav.helse.økonomi.Økonomi
import java.time.LocalDate

internal abstract class AbstractUtbetalingstidslinjeBuilder protected constructor(
    private val forlengelseStrategy: (LocalDate) -> Boolean = { false },
    private val arbeidsgiverRegler: ArbeidsgiverRegler = NormalArbeidstaker
) : SykdomstidslinjeVisitor {
    private var tilstand: UtbetalingState = Initiell

    private var sykedagerIArbeidsgiverperiode = 0
    private var ikkeSykedager = 0
    private var fridager = 0

    internal fun build(sykdomstidslinje: Sykdomstidslinje) {
        sykdomstidslinje.accept(this)
    }

    protected open fun reset() {}
    protected open fun arbeidsgiverperiodeOpphold() {}
    protected open fun arbeidsgiverperiodeFri() {}
    protected open fun arbeidsgiverperiodeMuligGjennomførtMedFri() {}
    protected open fun arbeidsgiverperiodeGjennomført() {}

    protected abstract fun addForeldetDag(dagen: LocalDate, økonomi: Økonomi)
    protected abstract fun addArbeidsgiverdag(dato: LocalDate)
    protected abstract fun addNAVdag(dato: LocalDate, økonomi: Økonomi)
    protected abstract fun addNAVHelgedag(dato: LocalDate, økonomi: Økonomi)
    protected abstract fun addArbeidsdag(dato: LocalDate)
    protected abstract fun addAvvistDag(dato: LocalDate)
    protected abstract fun addFridag(dato: LocalDate)

    final override fun visitDag(dag: Dag.UkjentDag, dato: LocalDate, kilde: SykdomstidslinjeHendelse.Hendelseskilde) =
        implisittDag(dato)

    final override fun visitDag(dag: Dag.Studiedag, dato: LocalDate, kilde: SykdomstidslinjeHendelse.Hendelseskilde) =
        implisittDag(dato)

    final override fun visitDag(dag: Dag.Permisjonsdag, dato: LocalDate, kilde: SykdomstidslinjeHendelse.Hendelseskilde) =
        fridag(dato)

    final override fun visitDag(dag: Dag.Utenlandsdag, dato: LocalDate, kilde: SykdomstidslinjeHendelse.Hendelseskilde) =
        implisittDag(dato)

    final override fun visitDag(dag: Dag.Arbeidsdag, dato: LocalDate, kilde: SykdomstidslinjeHendelse.Hendelseskilde) =
        arbeidsdag(dato)

    final override fun visitDag(
        dag: Dag.Arbeidsgiverdag,
        dato: LocalDate,
        økonomi: Økonomi,
        kilde: SykdomstidslinjeHendelse.Hendelseskilde
    ) = egenmeldingsdag(dato)

    final override fun visitDag(dag: Dag.Feriedag, dato: LocalDate, kilde: SykdomstidslinjeHendelse.Hendelseskilde) =
        fridag(dato)

    final override fun visitDag(dag: Dag.FriskHelgedag, dato: LocalDate, kilde: SykdomstidslinjeHendelse.Hendelseskilde) =
        arbeidsdag(dato)

    final override fun visitDag(
        dag: Dag.ArbeidsgiverHelgedag,
        dato: LocalDate,
        økonomi: Økonomi,
        kilde: SykdomstidslinjeHendelse.Hendelseskilde
    ) = sykHelgedag(dato, økonomi)

    final override fun visitDag(
        dag: Dag.Sykedag,
        dato: LocalDate,
        økonomi: Økonomi,
        kilde: SykdomstidslinjeHendelse.Hendelseskilde
    ) = sykedag(dato, økonomi)

    final override fun visitDag(
        dag: Dag.ForeldetSykedag,
        dato: LocalDate,
        økonomi: Økonomi,
        kilde: SykdomstidslinjeHendelse.Hendelseskilde
    ) = foreldetSykedag(dato, økonomi)

    final override fun visitDag(
        dag: Dag.SykHelgedag,
        dato: LocalDate,
        økonomi: Økonomi,
        kilde: SykdomstidslinjeHendelse.Hendelseskilde
    ) = sykHelgedag(dato, økonomi)

    final override fun visitDag(
        dag: Dag.ProblemDag,
        dato: LocalDate,
        kilde: SykdomstidslinjeHendelse.Hendelseskilde,
        melding: String
    ) = throw IllegalArgumentException("Forventet ikke problemdag i utbetalingstidslinjen. Melding: $melding")

    private fun foreldetSykedag(dagen: LocalDate, økonomi: Økonomi) {
        if (arbeidsgiverperiodeGjennomført(dagen)) {
            state(UtbetalingSykedager)
            addForeldetDag(dagen, økonomi)
        } else tilstand.sykedagerIArbeidsgiverperioden(this, dagen, økonomi)
    }

    private fun egenmeldingsdag(dato: LocalDate) =
        if (arbeidsgiverperiodeGjennomført(dato))
            addAvvistDag(dato)
        else
            tilstand.egenmeldingsdagIArbeidsgiverperioden(this, dato)

    private fun implisittDag(dagen: LocalDate) = if (dagen.erHelg()) fridag(dagen) else arbeidsdag(dagen)

    private fun sykedag(dagen: LocalDate, økonomi: Økonomi) {
        if (arbeidsgiverperiodeGjennomført(dagen))
            tilstand.sykedagerEtterArbeidsgiverperioden(this, dagen, økonomi)
        else
            tilstand.sykedagerIArbeidsgiverperioden(this, dagen, økonomi)
    }

    private fun sykHelgedag(dagen: LocalDate, økonomi: Økonomi) =
        if (arbeidsgiverperiodeGjennomført(dagen))
            tilstand.sykHelgedagEtterArbeidsgiverperioden(this, dagen, økonomi)
        else
            tilstand.sykHelgedagIArbeidsgiverperioden(this, dagen, økonomi)

    private fun arbeidsdag(dagen: LocalDate) =
        if (arbeidsgiverRegler.burdeStarteNyArbeidsgiverperiode(ikkeSykedager))
            tilstand.arbeidsdagerEtterOppholdsdager(this, dagen)
        else
            tilstand.arbeidsdagerIOppholdsdager(this, dagen)

    private fun fridag(dagen: LocalDate) {
        tilstand.fridag(this, dagen)
    }

    private fun håndterArbeidsgiverdag(dagen: LocalDate) {
        sykedagerIArbeidsgiverperiode += 1
        addArbeidsgiverdag(dagen)
        if (arbeidsgiverRegler.arbeidsgiverperiodenGjennomført(sykedagerIArbeidsgiverperiode))
            state(UtbetalingSykedager)
    }

    private fun inkrementerIkkeSykedager() {
        ikkeSykedager += 1
        if (arbeidsgiverRegler.burdeStarteNyArbeidsgiverperiode(ikkeSykedager)) state(Initiell)
    }

    private fun håndterFridag(dato: LocalDate) {
        fridager += 1
        addFridag(dato)
    }

    private fun håndterFriEgenmeldingsdag(dato: LocalDate) {
        sykedagerIArbeidsgiverperiode += fridager
        addAvvistDag(dato)
        if (arbeidsgiverperiodeGjennomført(dato))
            return state(UtbetalingSykedager)
        state(ArbeidsgiverperiodeSykedager)
    }

    private fun arbeidsgiverperiodeGjennomført(dagen: LocalDate): Boolean {
        if (sykedagerIArbeidsgiverperiode == 0 && forlengelseStrategy(dagen)) sykedagerIArbeidsgiverperiode = arbeidsgiverRegler.gjennomførArbeidsgiverperiode()
        return arbeidsgiverRegler.arbeidsgiverperiodenGjennomført(sykedagerIArbeidsgiverperiode)
    }

    private fun state(state: UtbetalingState) {
        this.tilstand.leaving(this)
        this.tilstand = state
        this.tilstand.entering(this)
    }

    internal interface UtbetalingState {
        fun sykedagerIArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        )

        fun egenmeldingsdagIArbeidsgiverperioden(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            sykedagerIArbeidsgiverperioden(splitter, dagen, Økonomi.ikkeBetalt())
        }

        fun sykedagerEtterArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        )

        fun fridag(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate)
        fun arbeidsdagerIOppholdsdager(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate)
        fun arbeidsdagerEtterOppholdsdager(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate)
        fun sykHelgedagIArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        )

        fun sykHelgedagEtterArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        )

        fun entering(splitter: AbstractUtbetalingstidslinjeBuilder) {}
        fun leaving(splitter: AbstractUtbetalingstidslinjeBuilder) {}
    }

    private object Initiell : UtbetalingState {

        override fun entering(splitter: AbstractUtbetalingstidslinjeBuilder) {
            splitter.sykedagerIArbeidsgiverperiode = 0
            splitter.ikkeSykedager = 0
            splitter.fridager = 0
            splitter.reset()
        }

        override fun sykedagerIArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(ArbeidsgiverperiodeSykedager)
            splitter.ikkeSykedager = 0
            splitter.fridager = 0
            splitter.håndterArbeidsgiverdag(dagen)
        }

        override fun sykedagerEtterArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(UtbetalingSykedager)
            splitter.addNAVdag(dagen, økonomi)
        }

        override fun sykHelgedagIArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(ArbeidsgiverperiodeSykedager)
            splitter.ikkeSykedager = 0
            splitter.fridager = 0
            splitter.håndterArbeidsgiverdag(dagen)
        }

        override fun sykHelgedagEtterArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(UtbetalingSykedager)
            splitter.addNAVHelgedag(dagen, økonomi)
        }

        override fun fridag(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.håndterFridag(dagen)
        }

        override fun arbeidsdagerIOppholdsdager(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.inkrementerIkkeSykedager()
            splitter.addArbeidsdag(dagen)
        }

        override fun arbeidsdagerEtterOppholdsdager(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.state(Ugyldig)
        }
    }

    private object ArbeidsgiverperiodeSykedager : UtbetalingState {

        override fun sykedagerIArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.håndterArbeidsgiverdag(dagen)
        }

        override fun sykedagerEtterArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(UtbetalingSykedager)
            splitter.addNAVdag(dagen, økonomi)
        }

        override fun sykHelgedagEtterArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(UtbetalingSykedager)
            splitter.addNAVHelgedag(dagen, økonomi)
        }

        override fun sykHelgedagIArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.håndterArbeidsgiverdag(dagen)
        }

        override fun arbeidsdagerIOppholdsdager(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.state(ArbeidsgiverperiodeOpphold)
            splitter.addArbeidsdag(dagen)
        }

        override fun arbeidsdagerEtterOppholdsdager(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.state(Ugyldig)
        }

        override fun fridag(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.fridager = 0
            splitter.state(ArbeidsgiverperiodeFri)
            splitter.håndterFridag(dagen)
        }
    }

    private object ArbeidsgiverperiodeFri : UtbetalingState {
        override fun entering(splitter: AbstractUtbetalingstidslinjeBuilder) {
            splitter.arbeidsgiverperiodeFri()
        }

        override fun fridag(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.håndterFridag(dagen)
            if (splitter.arbeidsgiverRegler.arbeidsgiverperiodenGjennomført(splitter.sykedagerIArbeidsgiverperiode + splitter.fridager))
                splitter.arbeidsgiverperiodeMuligGjennomførtMedFri()
        }

        override fun egenmeldingsdagIArbeidsgiverperioden(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.håndterFriEgenmeldingsdag(dagen)
        }

        override fun arbeidsdagerIOppholdsdager(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.addArbeidsdag(dagen)
            if (!splitter.arbeidsgiverRegler.arbeidsgiverperiodenGjennomført(splitter.sykedagerIArbeidsgiverperiode)
                && splitter.arbeidsgiverRegler.burdeStarteNyArbeidsgiverperiode(splitter.fridager + 1)
            ) {
                return splitter.state(Initiell)
            }
            splitter.state(ArbeidsgiverperiodeOpphold)
            splitter.arbeidsgiverperiodeOpphold()
        }

        override fun arbeidsdagerEtterOppholdsdager(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.state(Ugyldig)
        }

        override fun sykedagerEtterArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(UtbetalingSykedager)
            splitter.addNAVdag(dagen, økonomi)
        }

        override fun sykHelgedagEtterArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.addNAVHelgedag(dagen, økonomi)
        }

        override fun sykedagerIArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.sykedagerIArbeidsgiverperiode += splitter.fridager
            if (splitter.arbeidsgiverRegler.arbeidsgiverperiodenGjennomført(splitter.sykedagerIArbeidsgiverperiode)) {
                splitter.state(UtbetalingSykedager)
                splitter.addNAVdag(dagen, økonomi)
            } else {
                splitter.state(ArbeidsgiverperiodeSykedager)
                splitter.håndterArbeidsgiverdag(dagen)
            }
        }

        override fun sykHelgedagIArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.sykedagerIArbeidsgiverperiode += splitter.fridager
            if (splitter.arbeidsgiverRegler.arbeidsgiverperiodenGjennomført(splitter.sykedagerIArbeidsgiverperiode)) {
                splitter.state(UtbetalingSykedager)
                splitter.addNAVHelgedag(dagen, økonomi)
            } else {
                splitter.state(ArbeidsgiverperiodeSykedager)
                splitter.håndterArbeidsgiverdag(dagen)
            }
        }
    }

    private object ArbeidsgiverperiodeOpphold : UtbetalingState {
        override fun entering(splitter: AbstractUtbetalingstidslinjeBuilder) {
            splitter.ikkeSykedager = 1
        }

        override fun arbeidsdagerIOppholdsdager(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.inkrementerIkkeSykedager()
            splitter.addArbeidsdag(dagen)
        }

        override fun arbeidsdagerEtterOppholdsdager(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.state(Initiell)
            splitter.addArbeidsdag(dagen)
        }

        override fun sykedagerEtterArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(UtbetalingSykedager)
            splitter.addNAVdag(dagen, økonomi)
        }

        override fun sykedagerIArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(ArbeidsgiverperiodeSykedager)
            splitter.håndterArbeidsgiverdag(dagen)
        }

        override fun fridag(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.inkrementerIkkeSykedager()
            splitter.håndterFridag(dagen)
        }

        override fun sykHelgedagIArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(ArbeidsgiverperiodeSykedager)
            splitter.håndterArbeidsgiverdag(dagen)
        }

        override fun sykHelgedagEtterArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(UtbetalingSykedager)
            splitter.addNAVHelgedag(dagen, økonomi)
        }
    }

    private object UtbetalingSykedager : UtbetalingState {
        override fun entering(splitter: AbstractUtbetalingstidslinjeBuilder) {
            splitter.arbeidsgiverperiodeGjennomført()
            splitter.ikkeSykedager = 0
        }

        override fun sykHelgedagIArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(Ugyldig)
        }

        override fun sykHelgedagEtterArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.addNAVHelgedag(dagen, økonomi)
        }

        override fun sykedagerEtterArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.addNAVdag(dagen, økonomi)
        }

        override fun sykedagerIArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(Ugyldig)
        }

        override fun arbeidsdagerIOppholdsdager(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.state(UtbetalingOpphold)
            splitter.ikkeSykedager += 1
            splitter.addArbeidsdag(dagen)
        }

        override fun arbeidsdagerEtterOppholdsdager(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.state(Ugyldig)
        }

        override fun fridag(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.state(UtbetalingFri)
            splitter.håndterFridag(dagen)
        }
    }

    private object UtbetalingFri : UtbetalingState {
        override fun entering(splitter: AbstractUtbetalingstidslinjeBuilder) {
            splitter.fridager = 1
        }

        override fun sykedagerIArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(Ugyldig)
        }

        override fun sykedagerEtterArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(UtbetalingSykedager)
            splitter.addNAVdag(dagen, økonomi)
        }

        override fun fridag(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.håndterFridag(dagen)
        }

        override fun arbeidsdagerIOppholdsdager(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.ikkeSykedager = 1
            splitter.state(UtbetalingOpphold)
            splitter.addArbeidsdag(dagen)
        }

        override fun arbeidsdagerEtterOppholdsdager(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.state(Ugyldig)
        }

        override fun sykHelgedagIArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(Ugyldig)
        }

        override fun sykHelgedagEtterArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(UtbetalingSykedager)
            splitter.addNAVHelgedag(dagen, økonomi)
        }
    }

    private object UtbetalingOpphold : UtbetalingState {
        override fun sykedagerEtterArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(UtbetalingSykedager)
            splitter.addNAVdag(dagen, økonomi)
        }

        override fun sykedagerIArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(Ugyldig)
        }

        override fun arbeidsdagerIOppholdsdager(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.inkrementerIkkeSykedager()
            splitter.addArbeidsdag(dagen)
        }

        override fun arbeidsdagerEtterOppholdsdager(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.state(Initiell)
            splitter.addArbeidsdag(dagen)
        }

        override fun fridag(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
            splitter.inkrementerIkkeSykedager()
            splitter.håndterFridag(dagen)
        }

        override fun sykHelgedagIArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(Ugyldig)
        }

        override fun sykHelgedagEtterArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
            splitter.state(UtbetalingSykedager)
            splitter.addNAVHelgedag(dagen, økonomi)
        }
    }

    private object Ugyldig : UtbetalingState {
        override fun sykedagerIArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
        }

        override fun sykedagerEtterArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
        }

        override fun fridag(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
        }

        override fun arbeidsdagerIOppholdsdager(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
        }

        override fun arbeidsdagerEtterOppholdsdager(splitter: AbstractUtbetalingstidslinjeBuilder, dagen: LocalDate) {
        }

        override fun sykHelgedagIArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
        }

        override fun sykHelgedagEtterArbeidsgiverperioden(
            splitter: AbstractUtbetalingstidslinjeBuilder,
            dagen: LocalDate,
            økonomi: Økonomi
        ) {
        }
    }

}
