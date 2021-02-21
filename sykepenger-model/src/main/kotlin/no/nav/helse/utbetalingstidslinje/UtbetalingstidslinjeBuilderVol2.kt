package no.nav.helse.utbetalingstidslinje

import no.nav.helse.person.InntektshistorikkVol2
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler.Companion.NormalArbeidstaker
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Økonomi
import java.time.LocalDate

/**
 *  Forstår opprettelsen av en Utbetalingstidslinje
 */

internal class UtbetalingstidslinjeBuilderVol2 internal constructor(
    private val skjæringstidspunkter: List<LocalDate>,
    private val inntektshistorikk: InntektshistorikkVol2,
    forlengelseStrategy: (LocalDate) -> Boolean = { false },
    private val arbeidsgiverRegler: ArbeidsgiverRegler = NormalArbeidstaker
) : AbstractUtbetalingstidslinjeBuilder(forlengelseStrategy, arbeidsgiverRegler) {

    private val tidslinje = Utbetalingstidslinje()

    private fun inntektForDatoOrNull(dato: LocalDate) =
        skjæringstidspunkter
            .sorted()
            .lastOrNull { it <= dato }
            ?.let { skjæringstidspunkt ->
                inntektshistorikk.grunnlagForSykepengegrunnlag(skjæringstidspunkt, dato)
                    ?.let { inntekt -> skjæringstidspunkt to inntekt }
            }

    private fun inntektForDato(dato: LocalDate) =
        requireNotNull(inntektForDatoOrNull(dato)) { "Fant ikke inntekt for $dato med skjæringstidspunkter $skjæringstidspunkter" }

    private fun Økonomi.inntektIfNotNull(dato: LocalDate) =
        inntektForDatoOrNull(dato)
            ?.let { (skjæringstidspunkt, inntekt) -> inntekt(inntekt, inntekt.dekningsgrunnlag(arbeidsgiverRegler), skjæringstidspunkt) }
            ?: inntekt(INGEN, skjæringstidspunkt = dato)

    internal fun result(): Utbetalingstidslinje {
        return tidslinje
    }

    override fun addForeldetDag(dagen: LocalDate, økonomi: Økonomi) {
        val (skjæringstidspunkt, inntekt) = inntektForDato(dagen)
        tidslinje.addForeldetDag(dagen, økonomi.inntekt(inntekt, inntekt.dekningsgrunnlag(arbeidsgiverRegler), skjæringstidspunkt))
    }

    override fun addArbeidsgiverdag(dato: LocalDate) {
        tidslinje.addArbeidsgiverperiodedag(dato, Økonomi.ikkeBetalt().inntektIfNotNull(dato))
    }

    override fun addNAVdag(dato: LocalDate, økonomi: Økonomi) {
        val (skjæringstidspunkt, inntekt) = inntektForDato(dato)
        tidslinje.addNAVdag(dato, økonomi.inntekt(inntekt, inntekt.dekningsgrunnlag(arbeidsgiverRegler), skjæringstidspunkt))
    }

    override fun addNAVHelgedag(dato: LocalDate, økonomi: Økonomi) {
        val skjæringstidspunkt = inntektForDatoOrNull(dato)?.let { (skjæringstidspunkt) -> skjæringstidspunkt } ?: dato
        tidslinje.addHelg(dato, økonomi.inntekt(INGEN, skjæringstidspunkt = skjæringstidspunkt))
    }

    override fun addArbeidsdag(dato: LocalDate) {
        tidslinje.addArbeidsdag(dato, Økonomi.ikkeBetalt().inntektIfNotNull(dato))
    }

    override fun addAvvistDag(dato: LocalDate) {
        tidslinje.addAvvistDag(dato, Økonomi.ikkeBetalt().inntektIfNotNull(dato), Begrunnelse.EgenmeldingUtenforArbeidsgiverperiode)
    }

    override fun addFridag(dato: LocalDate) {
        tidslinje.addFridag(dato, Økonomi.ikkeBetalt().inntektIfNotNull(dato))
    }
}
