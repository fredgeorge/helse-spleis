package no.nav.helse.person

import no.nav.helse.Toggles
import no.nav.helse.hendelser.*
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Inntekt
import java.time.LocalDate
import java.util.*

class Person private constructor(
    private val aktørId: String,
    private val fødselsnummer: String,
    private val arbeidsgivere: MutableList<Arbeidsgiver>,
    internal val aktivitetslogg: Aktivitetslogg
) : Aktivitetskontekst {

    constructor(
        aktørId: String,
        fødselsnummer: String
    ) : this(aktørId, fødselsnummer, mutableListOf(), Aktivitetslogg())

    private val observers = mutableListOf<PersonObserver>()

    fun håndter(sykmelding: Sykmelding) = håndter(sykmelding, "sykmelding") { sykmelding.forGammel() }

    fun håndter(søknad: Søknad) = håndter(søknad, "søknad")

    fun håndter(søknad: SøknadArbeidsgiver) = håndter(søknad, "søknad til arbeidsgiver")

    fun håndter(inntektsmelding: Inntektsmelding) = håndter(inntektsmelding, "inntektsmelding")

    private fun håndter(
        hendelse: SykdomstidslinjeHendelse,
        hendelsesmelding: String,
        avvisIf: () -> Boolean = { false }
    ) {
        registrer(hendelse, "Behandler $hendelsesmelding")
        if (avvisIf()) return
        val arbeidsgiver = finnEllerOpprettArbeidsgiver(hendelse)
        if (!arbeidsgiver.harHistorikk() && arbeidsgivere.size > 1 && !Toggles.flereArbeidsgivereEnabled) return invaliderAllePerioder(hendelse)

        hendelse.fortsettÅBehandle(arbeidsgiver)
    }

    fun håndter(utbetalingshistorikk: Utbetalingshistorikk) {
        utbetalingshistorikk.kontekst(this)
        finnArbeidsgiver(utbetalingshistorikk).håndter(utbetalingshistorikk)
    }

    fun håndter(ytelser: Ytelser) {
        registrer(ytelser, "Behandler historiske utbetalinger og inntekter")
        finnArbeidsgiver(ytelser).håndter(ytelser)
    }

    fun håndter(utbetalingsgodkjenning: Utbetalingsgodkjenning) {
        registrer(utbetalingsgodkjenning, "Behandler utbetalingsgodkjenning")
        finnArbeidsgiver(utbetalingsgodkjenning).håndter(utbetalingsgodkjenning)
    }

    fun håndter(vilkårsgrunnlag: Vilkårsgrunnlag) {
        registrer(vilkårsgrunnlag, "Behandler vilkårsgrunnlag")
        finnArbeidsgiver(vilkårsgrunnlag).håndter(vilkårsgrunnlag)
    }

    fun håndter(simulering: Simulering) {
        registrer(simulering, "Behandler simulering")
        finnArbeidsgiver(simulering).håndter(simulering)
    }

    fun håndter(utbetaling: UtbetalingOverført) {
        registrer(utbetaling, "Behandler utbetaling overført")
        finnArbeidsgiver(utbetaling).håndter(utbetaling)
    }

    fun håndter(utbetaling: UtbetalingHendelse) {
        registrer(utbetaling, "Behandler utbetaling")
        finnArbeidsgiver(utbetaling).håndter(utbetaling)
    }

    fun håndter(påminnelse: Påminnelse) {
        try {
            påminnelse.kontekst(this)
            finnArbeidsgiver(påminnelse).håndter(påminnelse)
        } catch (err: Aktivitetslogg.AktivitetException) {
            påminnelse.error("Fikk påminnelse uten at vi fant arbeidsgiver eller vedtaksperiode")
            observers.forEach {
                it.vedtaksperiodeIkkeFunnet(
                    PersonObserver.VedtaksperiodeIkkeFunnetEvent(
                        vedtaksperiodeId = UUID.fromString(påminnelse.vedtaksperiodeId),
                        aktørId = påminnelse.aktørId(),
                        fødselsnummer = påminnelse.fødselsnummer(),
                        organisasjonsnummer = påminnelse.organisasjonsnummer()
                    )
                )
            }
        }
    }

    fun håndter(hendelse: Rollback) {
        hendelse.kontekst(this)
        arbeidsgivere.forEach {
            it.håndter(hendelse)
        }
        hendelse.warn("Personen har blitt tilbakestilt og kan derfor ha avvik i historikken fra infotrygd.")
    }

    fun håndter(hendelse: RollbackDelete) {
        hendelse.kontekst(this)
        hendelse.warn("Personen har blitt tilbakestilt og kan derfor ha avvik i historikken fra infotrygd.")
    }

    fun håndter(hendelse: OverstyrTidslinje) {
        hendelse.kontekst(this)
        finnArbeidsgiver(hendelse).håndter(hendelse)
    }

    fun håndter(hendelse: Annullering) {
        hendelse.kontekst(this)
        finnArbeidsgiver(hendelse).håndter(hendelse)
    }

    fun annullert(event: PersonObserver.UtbetalingAnnullertEvent) {
        observers.forEach { it.annullering(event) }
    }

    fun vedtaksperiodePåminnet(påminnelse: Påminnelse) {
        observers.forEach { it.vedtaksperiodePåminnet(påminnelse) }
    }

    fun vedtaksperiodeIkkePåminnet(påminnelse: Påminnelse, tilstandType: TilstandType) {
        observers.forEach { it.vedtaksperiodeIkkePåminnet(påminnelse, tilstandType) }
    }

    fun vedtaksperiodeAvbrutt(event: PersonObserver.VedtaksperiodeAvbruttEvent) {
        observers.forEach { it.vedtaksperiodeAvbrutt(event) }
    }

    fun vedtaksperiodeUtbetalt(event: PersonObserver.UtbetaltEvent) {
        observers.forEach { it.vedtaksperiodeUtbetalt(event) }
    }

    fun vedtaksperiodeEndret(event: PersonObserver.VedtaksperiodeEndretTilstandEvent) {
        observers.forEach {
            it.vedtaksperiodeEndret(event)
            it.personEndret(
                PersonObserver.PersonEndretEvent(
                    aktørId = aktørId,
                    fødselsnummer = fødselsnummer,
                    person = this
                )
            )
        }
    }

    fun vedtaksperiodeReplay(event: PersonObserver.VedtaksperiodeReplayEvent) {
        observers.forEach {
            it.vedtaksperiodeReplay(event)
        }
    }

    fun trengerInntektsmelding(event: PersonObserver.ManglendeInntektsmeldingEvent) {
        observers.forEach { it.manglerInntektsmelding(event) }
    }

    fun trengerIkkeInntektsmelding(event: PersonObserver.TrengerIkkeInntektsmeldingEvent) {
        observers.forEach { it.trengerIkkeInntektsmelding(event) }
    }

    fun håndter(hendelse: AnnullerUtbetaling) {
        hendelse.kontekst(this)
        arbeidsgivere.finn(hendelse.organisasjonsnummer())?.håndter(hendelse)
            ?: hendelse.error("Finner ikke arbeidsgiver")
    }

    fun håndter(hendelse: Grunnbeløpsregulering) {
        hendelse.kontekst(this)
        arbeidsgivere.finn(hendelse.organisasjonsnummer())?.håndter(hendelse)
            ?: hendelse.error("Finner ikke arbeidsgiver")
    }

    fun addObserver(observer: PersonObserver) {
        observers.add(observer)
    }

    internal fun accept(visitor: PersonVisitor) {
        visitor.preVisitPerson(this, aktørId, fødselsnummer)
        visitor.visitPersonAktivitetslogg(aktivitetslogg)
        aktivitetslogg.accept(visitor)
        visitor.preVisitArbeidsgivere()
        arbeidsgivere.forEach { it.accept(visitor) }
        visitor.postVisitArbeidsgivere()
        visitor.postVisitPerson(this, aktørId, fødselsnummer)
    }

    override fun toSpesifikkKontekst(): SpesifikkKontekst {
        return SpesifikkKontekst("Person", mapOf("fødselsnummer" to fødselsnummer, "aktørId" to aktørId))
    }

    private fun registrer(hendelse: ArbeidstakerHendelse, melding: String) {
        hendelse.kontekst(this)
        hendelse.info(melding)
    }

    internal fun invaliderAllePerioder(hendelse: ArbeidstakerHendelse) {
        hendelse.error("Invaliderer alle perioder pga flere arbeidsgivere")
        arbeidsgivere.forEach { it.søppelbøtte(hendelse, Arbeidsgiver.ALLE) }
    }

    private fun finnEllerOpprettArbeidsgiver(hendelse: ArbeidstakerHendelse) =
        hendelse.organisasjonsnummer().let { orgnr ->
            arbeidsgivere.finnEllerOpprett(orgnr) {
                hendelse.info("Ny arbeidsgiver med organisasjonsnummer %s for denne personen", orgnr)
                Arbeidsgiver(this, orgnr)
            }
        }

    private fun finnArbeidsgiver(hendelse: ArbeidstakerHendelse) =
        hendelse.organisasjonsnummer().let { orgnr ->
            arbeidsgivere.finn(orgnr) ?: hendelse.severe("Finner ikke arbeidsgiver")
        }

    private fun MutableList<Arbeidsgiver>.finn(orgnr: String) = find { it.organisasjonsnummer() == orgnr }

    private fun MutableList<Arbeidsgiver>.finnEllerOpprett(orgnr: String, creator: () -> Arbeidsgiver) =
        finn(orgnr) ?: run {
            val newValue = creator()
            add(newValue)
            newValue
        }

    internal fun arbeidsgiverOverlapper(periode: Periode, arbeidstakerHendelse: ArbeidstakerHendelse) =
        (arbeidsgivere.filter { it.overlapper(periode) }.size > 1).also {
            if (it) invaliderAllePerioder(arbeidstakerHendelse)
        }

    internal fun nåværendeVedtaksperioder() = arbeidsgivere.mapNotNull { it.nåværendeVedtaksperiode() }.sorted()

    internal fun lagreInntekter(
        arbeidsgiverId: String,
        arbeidsgiverInntekt: Inntektsvurdering.ArbeidsgiverInntekt,
        skjæringstidspunkt: LocalDate,
        vilkårsgrunnlag: Vilkårsgrunnlag
    ) {
        finnArbeidsgiverForInntekter(arbeidsgiverId, vilkårsgrunnlag).lagreInntekter(
            arbeidsgiverInntekt,
            skjæringstidspunkt,
            vilkårsgrunnlag
        )
    }

    internal fun lagreInntekter(
        arbeidsgiverId: String,
        inntektsopplysninger: List<Utbetalingshistorikk.Inntektsopplysning>,
        ytelser: Ytelser
    ) {
        finnArbeidsgiverForInntekter(arbeidsgiverId, ytelser).addInntektVol2(inntektsopplysninger, ytelser)
    }

    internal fun sammenligningsgrunnlag(periode: Periode): Inntekt {
        val dato = skjæringstidspunkt(periode.endInclusive) ?: periode.start
        return arbeidsgivere.fold(Inntekt.INGEN) {acc, arbeidsgiver -> acc.plus(arbeidsgiver.grunnlagForSammenligningsgrunnlag(dato))}
    }

    internal fun skjæringstidspunkt(dato: LocalDate) = skjæringstidspunkt(dato, Historie())
    internal fun skjæringstidspunkt(dato: LocalDate, utbetalingshistorikk: Utbetalingshistorikk) = skjæringstidspunkt(dato, Historie(utbetalingshistorikk))
    internal fun skjæringstidspunkter(dato: LocalDate, utbetalingshistorikk: Utbetalingshistorikk) = Arbeidsgiver.skjæringstidspunkter(arbeidsgivere, dato, Historie(utbetalingshistorikk))
    private fun skjæringstidspunkt(dato: LocalDate, historie: Historie) = Arbeidsgiver.skjæringstidspunkt(arbeidsgivere, dato, historie)
    private fun sammenhengendePeriode(periode: Periode, historie: Historie = Historie()) = skjæringstidspunkt(periode.endInclusive, historie)?.let { periode.oppdaterFom(it) } ?: periode

    internal fun utbetalingstidslinjer(periode: Periode, ytelser: Ytelser): Map<Arbeidsgiver, Utbetalingstidslinje> {
        val sammenhengendePeriode = sammenhengendePeriode(periode)
        val skjæringstidspunkter = skjæringstidspunkter(periode.endInclusive, ytelser.utbetalingshistorikk())

        return arbeidsgivere
            .filter(Arbeidsgiver::harHistorikk)
            .map { arbeidsgiver ->
                arbeidsgiver to arbeidsgiver.oppdatertUtbetalingstidslinje(
                    skjæringstidspunkter = skjæringstidspunkter,
                    sammenhengendePeriode = sammenhengendePeriode,
                    ytelser = ytelser
                )
            }.toMap()
    }

    private fun finnArbeidsgiverForInntekter(arbeidsgiver: String, hendelse: ArbeidstakerHendelse): Arbeidsgiver {
        return arbeidsgivere.finnEllerOpprett(arbeidsgiver) {
            hendelse.info("Ny arbeidsgiver med organisasjonsnummer %s for denne personen", arbeidsgiver)
            Arbeidsgiver(this, arbeidsgiver)
        }
    }
}
