package no.nav.helse.hendelser

import no.nav.helse.person.ArbeidstakerHendelse
import no.nav.helse.person.Person
import java.time.LocalDate

internal class Validation private constructor(private val hendelse: ArbeidstakerHendelse) {
    private var errorBlock: () -> Unit = {}

    internal companion object {
        internal fun validation(hendelse: ArbeidstakerHendelse, block: Validation.() -> Unit) {
            Validation(hendelse).apply(block)
        }
    }

    internal fun onError(block: () -> Unit) {
        errorBlock = block
    }

    internal fun valider(feilmelding: String? = null, isValid: () -> Boolean) {
        if (hendelse.hasErrorsOrWorse()) return
        if (isValid()) return
        feilmelding?.also { hendelse.error(it) }
        errorBlock()
    }

    internal fun onSuccess(successBlock: () -> Unit) {
        if (!hendelse.hasErrorsOrWorse()) successBlock()
    }
}

internal fun Validation.validerYtelser(
    periode: Periode,
    ytelser: Ytelser,
    skjæringstidspunkt: LocalDate
) = valider {
    !ytelser.valider(periode, skjæringstidspunkt).hasErrorsOrWorse()
}

internal fun Validation.validerUtbetalingshistorikk(
    periode: Periode,
    utbetalingshistorikk: Utbetalingshistorikk,
    skjæringstidspunkt: LocalDate?
) = valider {
    !utbetalingshistorikk.validerOverlappende(periode, skjæringstidspunkt).hasErrorsOrWorse()
}

internal fun Validation.overlappende(
    sykdomsperiode: Periode,
    foreldrepermisjon: Foreldrepermisjon
) = valider("Har overlappende foreldrepengeperioder med syketilfelle") {
    !foreldrepermisjon.overlapper(sykdomsperiode)
}

internal fun Validation.overlappende(
    sykdomsperiode: Periode,
    pleiepenger: Pleiepenger
) = valider("Har overlappende pleiepengeytelse med syketilfelle") {
    !pleiepenger.overlapper(sykdomsperiode)
}

internal fun Validation.overlappende(
    sykdomsperiode: Periode,
    omsorgspenger: Omsorgspenger
) = valider("Har overlappende omsorgspengerytelse med syketilfelle") {
    !omsorgspenger.overlapper(sykdomsperiode)
}

internal fun Validation.overlappende(
    sykdomsperiode: Periode,
    opplæringspenger: Opplæringspenger
) = valider("Har overlappende opplæringspengerytelse med syketilfelle") {
    !opplæringspenger.overlapper(sykdomsperiode)
}

internal fun Validation.overlappende(
    sykdomsperiode: Periode,
    institusjonsopphold: Institusjonsopphold
) = valider("Har overlappende institusjonsopphold med syketilfelle") {
    !institusjonsopphold.overlapper(sykdomsperiode)
}

internal fun Validation.harNødvendigInntekt(
    person: Person,
    skjæringstidspunkt: LocalDate
) = valider("Vi har ikke inntektshistorikken vi trenger for $skjæringstidspunkt") {
    person.harNødvendigInntekt(skjæringstidspunkt)
}
