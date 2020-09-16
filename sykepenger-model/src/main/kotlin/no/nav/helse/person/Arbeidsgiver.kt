package no.nav.helse.person

import no.nav.helse.hendelser.*
import no.nav.helse.hendelser.Periode.Companion.slåSammen
import no.nav.helse.hendelser.Utbetalingshistorikk.Inntektsopplysning.Companion.lagreInntekter
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Behov.Companion.sendUtbetalingsbehov
import no.nav.helse.person.ForkastetÅrsak.UKJENT
import no.nav.helse.sykdomstidslinje.Sykdomshistorikk
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingslinjer.Utbetaling.Companion.utbetalte
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler.Companion.NormalArbeidstaker
import no.nav.helse.utbetalingstidslinje.Oldtidsutbetalinger
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.utbetalingstidslinje.UtbetalingstidslinjeBuilder
import no.nav.helse.økonomi.Inntekt
import java.time.LocalDate
import java.util.*

internal class Arbeidsgiver private constructor(
    private val person: Person,
    private val organisasjonsnummer: String,
    private val id: UUID,
    private val inntektshistorikk: Inntektshistorikk,
    private val inntektshistorikkVol2: InntektshistorikkVol2,
    private val sykdomshistorikk: Sykdomshistorikk,
    private val vedtaksperioder: MutableList<Vedtaksperiode>,
    private val forkastede: SortedMap<Vedtaksperiode, ForkastetÅrsak>,
    private val utbetalinger: MutableList<Utbetaling>
) : Aktivitetskontekst {
    internal constructor(person: Person, organisasjonsnummer: String) : this(
        person = person,
        organisasjonsnummer = organisasjonsnummer,
        id = UUID.randomUUID(),
        inntektshistorikk = Inntektshistorikk(),
        inntektshistorikkVol2 = InntektshistorikkVol2(),
        sykdomshistorikk = Sykdomshistorikk(),
        vedtaksperioder = mutableListOf(),
        forkastede = sortedMapOf(),
        utbetalinger = mutableListOf()
    )

    internal companion object {
        internal val TIDLIGERE: VedtaksperioderSelector = Arbeidsgiver::tidligere
        internal val TIDLIGERE_OG_ETTERGØLGENDE: VedtaksperioderSelector = Arbeidsgiver::tidligereOgEtterfølgende
        internal val SENERE: VedtaksperioderSelector = Arbeidsgiver::senere
        internal val SENERE_EXCLUSIVE: VedtaksperioderSelector = Arbeidsgiver::senereExclusive
        internal val KUN: VedtaksperioderSelector = Arbeidsgiver::kun
        internal val ALLE: VedtaksperioderSelector = Arbeidsgiver::alle

        internal fun sammenhengendeSykeperioder(arbeidsgivere: List<Arbeidsgiver>) =
            arbeidsgivere
                .flatMap { it.vedtaksperioder }
                .map(Vedtaksperiode::sykeperioder)
                .flatten()
                .slåSammen()

        internal fun inntektsdatoer(arbeidsgivere: List<Arbeidsgiver>) =
            sammenhengendeSykeperioder(arbeidsgivere)
                .map { it.start.minusDays(1) }
    }

    internal fun accept(visitor: ArbeidsgiverVisitor) {
        visitor.preVisitArbeidsgiver(this, id, organisasjonsnummer)
        inntektshistorikk.accept(visitor)
        inntektshistorikkVol2.accept(visitor)
        sykdomshistorikk.accept(visitor)
        visitor.preVisitUtbetalinger(utbetalinger)
        utbetalinger.forEach { it.accept(visitor) }
        visitor.postVisitUtbetalinger(utbetalinger)
        visitor.preVisitPerioder(vedtaksperioder)
        vedtaksperioder.forEach { it.accept(visitor) }
        visitor.postVisitPerioder(vedtaksperioder)
        visitor.preVisitForkastedePerioder(forkastede)
        forkastede.forEach { it.key.accept(visitor) }
        visitor.postVisitForkastedePerioder(forkastede)
        visitor.postVisitArbeidsgiver(this, id, organisasjonsnummer)
    }

    internal fun organisasjonsnummer() = organisasjonsnummer

    internal fun utbetaling() = utbetalinger.lastOrNull()

    internal fun utbetalteUtbetalinger() = utbetalinger.utbetalte()

    internal fun nåværendeTidslinje() =
        utbetaling()?.utbetalingstidslinje() ?: throw IllegalStateException("mangler utbetalinger")

    internal fun createUtbetaling(
        fødselsnummer: String,
        organisasjonsnummer: String,
        utbetalingstidslinje: Utbetalingstidslinje,
        sisteDato: LocalDate,
        aktivitetslogg: Aktivitetslogg
    ) {
        utbetalinger.add(
            Utbetaling(
                fødselsnummer,
                organisasjonsnummer,
                utbetalingstidslinje,
                sisteDato,
                aktivitetslogg,
                utbetalinger
            )
        )
    }

    private fun validerSykdomstidslinjer() = vedtaksperioder.forEach {
        it.validerSykdomstidslinje(sykdomshistorikk.sykdomstidslinje())
    }

    internal fun håndter(sykmelding: Sykmelding) {
        sykmelding.kontekst(this)
        if (vedtaksperioder.toList().map { it.håndter(sykmelding) }.none { it }) {
            sykmelding.info("Lager ny vedtaksperiode")
            nyVedtaksperiode(sykmelding).håndter(sykmelding)
            vedtaksperioder.sort()
        } else {
            validerSykdomstidslinjer()
        }
    }

    internal fun håndter(søknad: Søknad) {
        søknad.kontekst(this)
        if (vedtaksperioder.toList().map { it.håndter(søknad) }.none { it }) {
            søknad.error("Forventet ikke søknad. Har nok ikke mottatt sykmelding")
        } else {
            validerSykdomstidslinjer()
        }
    }

    internal fun håndter(søknad: SøknadArbeidsgiver) {
        søknad.kontekst(this)
        if (vedtaksperioder.toList().map { it.håndter(søknad) }.none { it }) {
            søknad.error("Forventet ikke søknad til arbeidsgiver. Har nok ikke mottatt sykmelding")
        } else {
            validerSykdomstidslinjer()
        }
    }

    internal fun håndter(inntektsmelding: Inntektsmelding) {
        inntektsmelding.kontekst(this)
        if (vedtaksperioder.toList().map { it.håndter(inntektsmelding) }.none { it }) {
            inntektsmelding.error("Forventet ikke inntektsmelding. Har nok ikke mottatt sykmelding")
        } else {
            validerSykdomstidslinjer()
        }
    }

    internal fun håndter(utbetalingshistorikk: Utbetalingshistorikk) {
        utbetalingshistorikk.kontekst(this)
        vedtaksperioder.toList().forEach { it.håndter(utbetalingshistorikk) }
    }

    internal fun håndter(ytelser: Ytelser) {
        ytelser.kontekst(this)
        vedtaksperioder.toList().forEach { it.håndter(ytelser) }
    }

    internal fun håndter(utbetalingsgodkjenning: Utbetalingsgodkjenning) {
        utbetalingsgodkjenning.kontekst(this)
        vedtaksperioder.toList().forEach { it.håndter(utbetalingsgodkjenning) }
    }

    internal fun håndter(vilkårsgrunnlag: Vilkårsgrunnlag) {
        vilkårsgrunnlag.kontekst(this)
        vedtaksperioder.toList().forEach { it.håndter(vilkårsgrunnlag) }
    }

    internal fun håndter(simulering: Simulering) {
        simulering.kontekst(this)
        vedtaksperioder.toList().forEach { it.håndter(simulering) }
    }

    internal fun håndter(utbetaling: UtbetalingOverført) {
        utbetaling.kontekst(this)
        vedtaksperioder.toList().forEach { it.håndter(utbetaling) }
    }

    internal fun håndter(utbetaling: UtbetalingHendelse) {
        utbetaling.kontekst(this)
        vedtaksperioder.toList().forEach { it.håndter(utbetaling) }
    }

    internal fun håndter(påminnelse: Påminnelse): Boolean {
        påminnelse.kontekst(this)
        return vedtaksperioder.toList().any { it.håndter(påminnelse) }
    }

    internal fun håndter(kansellerUtbetaling: KansellerUtbetaling) {
        // TODO: Håndterer kun arbeidsgiverOppdrag p.t. Må på sikt håndtere personOppdrag
        kansellerUtbetaling.kontekst(this)
        val sisteUtbetaling = utbetalinger.reversed().firstOrNull {
            it.arbeidsgiverOppdrag().fagsystemId() == kansellerUtbetaling.fagsystemId
        }
        if (sisteUtbetaling == null) {
            kansellerUtbetaling.error(
                "Avvis hvis vi ikke finner fagsystemId %s",
                kansellerUtbetaling.fagsystemId
            )
            return
        }

        if (sisteUtbetaling.erAnnullert()) {
            kansellerUtbetaling.info("Forsøkte å kansellere en utbetaling som allerede er annullert")
            return
        }

        val utbetaling = sisteUtbetaling.kansellerUtbetaling()

        person.annullert(
            PersonObserver.UtbetalingAnnullertEvent(
                fødselsnummer = kansellerUtbetaling.fødselsnummer(),
                aktørId = kansellerUtbetaling.aktørId(),
                organisasjonsnummer = kansellerUtbetaling.organisasjonsnummer(),
                fagsystemId = kansellerUtbetaling.fagsystemId,
                utbetalingslinjer = sisteUtbetaling?.let {
                    it.arbeidsgiverOppdrag().map {
                        PersonObserver.UtbetalingAnnullertEvent.Utbetalingslinje(
                            fom = it.fom,
                            tom = it.tom,
                            beløp = it.totalbeløp(),
                            grad = it.grad
                        )
                    }
                },
                annullertAvSaksbehandler = kansellerUtbetaling.opprettet,
                saksbehandlerEpost = kansellerUtbetaling.saksbehandlerEpost
            )
        )

        kansellerUtbetaling.info("Annullerer utbetalinger med fagsystemId ${kansellerUtbetaling.fagsystemId}")
        utbetalinger.add(utbetaling)
        sendUtbetalingsbehov(
            aktivitetslogg = kansellerUtbetaling.aktivitetslogg,
            oppdrag = utbetaling.arbeidsgiverOppdrag(),
            saksbehandler = kansellerUtbetaling.saksbehandler
        )
        vedtaksperioder.toList().forEach { it.håndter(kansellerUtbetaling) }
    }

    internal fun håndter(hendelse: Rollback) {
        hendelse.kontekst(this)
        søppelbøtte(hendelse)
    }

    internal fun håndter(hendelse: OverstyrTidslinje) {
        hendelse.kontekst(this)
        sykdomshistorikk.nyHåndter(hendelse)
        vedtaksperioder.toList().forEach { it.håndter(hendelse) }
    }

    internal fun oppdaterSykdom(hendelse: SykdomstidslinjeHendelse) = sykdomshistorikk.nyHåndter(hendelse)

    internal fun sykdomstidslinje() = sykdomshistorikk.sykdomstidslinje()

    internal fun inntekt(dato: LocalDate): Inntekt? = inntektshistorikk.inntekt(dato)

    internal fun addInntekt(inntektsmelding: Inntektsmelding) {
        inntektsmelding.addInntekt(inntektshistorikk)
    }

    internal fun addInntekt(ytelser: Ytelser) {
        ytelser.addInntekt(organisasjonsnummer, inntektshistorikk)
    }

    internal fun addInntektVol2(inntektsmelding: Inntektsmelding) {
        inntektsmelding.addInntekt(inntektshistorikkVol2)
    }

    internal fun addInntektVol2(inntektsopplysninger: List<Utbetalingshistorikk.Inntektsopplysning>, ytelser: Ytelser) {
        inntektsopplysninger.lagreInntekter(inntektshistorikkVol2, ytelser.meldingsreferanseId())
    }

    internal fun lagreInntekter(
        arbeidsgiverInntekt: Inntektsvurdering.ArbeidsgiverInntekt,
        førsteFraværsdag: LocalDate,
        vilkårsgrunnlag: Vilkårsgrunnlag
    ) {
        arbeidsgiverInntekt.lagreInntekter(
            inntektshistorikkVol2,
            førsteFraværsdag,
            vilkårsgrunnlag.meldingsreferanseId()
        )
    }

    internal fun sykepengegrunnlag(dato: LocalDate): Inntekt? = inntektshistorikk.sykepengegrunnlag(dato)

    internal fun søppelbøtte(hendelse: PersonHendelse) {
        vedtaksperioder.firstOrNull()?.also { søppelbøtte(it, hendelse, ALLE) }
    }

    internal fun søppelbøtte(
        vedtaksperiode: Vedtaksperiode,
        hendelse: PersonHendelse,
        block: VedtaksperioderSelector,
        sendTilInfotrygd: Boolean = true
    ): List<Vedtaksperiode> {
        if (vedtaksperiode !in vedtaksperioder) return listOf()
        return forkastet(vedtaksperiode, block)
            .takeIf { it.isNotEmpty() }
            ?.also { perioder ->
                perioder
                    .onEach {
                        it.ferdig(hendelse, sendTilInfotrygd)
                        sykdomshistorikk.fjernDager(it.periode())
                    }
                if (vedtaksperioder.isEmpty()) sykdomshistorikk.tøm()
                gjenopptaBehandling(hendelse)
            }
            ?: listOf()
    }

    private fun forkastet(vedtaksperiode: Vedtaksperiode, block: VedtaksperioderSelector) =
        block(this, vedtaksperiode).also { selected ->
            vedtaksperioder.removeAll(selected)
            forkastede.putAll(selected.map { it to UKJENT })
        }

    private fun tidligere(vedtaksperiode: Vedtaksperiode) =
        vedtaksperioder.subList(0, vedtaksperioder.indexOf(vedtaksperiode) + 1).toMutableList()

    private fun tidligereOgEtterfølgende(vedtaksperiode: Vedtaksperiode): MutableList<Vedtaksperiode> {
        var index = vedtaksperioder.indexOf(vedtaksperiode)
        val results = vedtaksperioder.subList(0, index + 1).toMutableList()
        while (vedtaksperioder.last() != results.last()) {
            if (!vedtaksperioder[index].erSykeperiodeRettFør(vedtaksperioder[index + 1])) break
            results.add(vedtaksperioder[index + 1])
            index++
        }
        return results
    }

    private fun senere(vedtaksperiode: Vedtaksperiode) =
        vedtaksperioder.let {
            it.sort()
            it.subList(
                vedtaksperioder.indexOf(vedtaksperiode),
                vedtaksperioder.size
            ).toMutableList()
        }

    private fun senereExclusive(vedtaksperiode: Vedtaksperiode) =
        vedtaksperioder.let {
            it.sort()
            it.subList(
                minOf(vedtaksperioder.indexOf(vedtaksperiode) + 1, vedtaksperioder.size),
                vedtaksperioder.size
            ).toMutableList()
        }

    private fun kun(vedtaksperiode: Vedtaksperiode) = mutableListOf(vedtaksperiode)

    private fun alle(vedtaksperiode: Vedtaksperiode) = vedtaksperioder.toMutableList()

    private fun gjenopptaBehandling(hendelse: PersonHendelse) {
        vedtaksperioder.firstOrNull { it.skalGjenopptaBehandling() }?.also {
            it.håndter(GjenopptaBehandling(hendelse))
        }
    }

    internal fun forkastAlleTidligere(vedtaksperiode: Vedtaksperiode, hendelse: ArbeidstakerHendelse) {
        if (vedtaksperiode == vedtaksperioder.first()) return
        søppelbøtte(vedtaksperioder[vedtaksperioder.indexOf(vedtaksperiode) - 1], hendelse, TIDLIGERE)
    }

    private fun nyVedtaksperiode(sykmelding: Sykmelding): Vedtaksperiode {
        return Vedtaksperiode(
            person = person,
            arbeidsgiver = this,
            id = UUID.randomUUID(),
            aktørId = sykmelding.aktørId(),
            fødselsnummer = sykmelding.fødselsnummer(),
            organisasjonsnummer = sykmelding.organisasjonsnummer()
        ).also {
            vedtaksperioder.add(it)
        }
    }

    internal fun finnSykeperiodeRettFør(vedtaksperiode: Vedtaksperiode) =
        vedtaksperioder.firstOrNull { other -> other.erSykeperiodeRettFør(vedtaksperiode) }

    internal fun finnSykeperiodeRettEtter(vedtaksperiode: Vedtaksperiode) =
        vedtaksperioder.firstOrNull { other -> vedtaksperiode.erSykeperiodeRettFør(other) }

    internal fun harPeriodeEtter(vedtaksperiode: Vedtaksperiode) =
        vedtaksperioder.any { other -> other.starterEtter(vedtaksperiode) }

    internal fun tidligerePerioderFerdigBehandlet(vedtaksperiode: Vedtaksperiode) =
        Vedtaksperiode.tidligerePerioderFerdigBehandlet(vedtaksperioder, vedtaksperiode)

    internal fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: PersonHendelse) {
        vedtaksperioder.toList().forEach { it.håndter(vedtaksperiode, GjenopptaBehandling(hendelse)) }
        person.nåværendeVedtaksperioder().firstOrNull()?.gjentaHistorikk(hendelse)
    }

    internal class GjenopptaBehandling(internal val hendelse: PersonHendelse)

    internal class TilbakestillBehandling(
        internal val organisasjonsnummer: String,
        internal val hendelse: PersonHendelse
    ) : ArbeidstakerHendelse(hendelse.meldingsreferanseId(), hendelse.aktivitetslogg) {
        override fun organisasjonsnummer() = organisasjonsnummer
        override fun aktørId() = hendelse.aktørId()
        override fun fødselsnummer() = hendelse.fødselsnummer()
    }

    override fun toSpesifikkKontekst(): SpesifikkKontekst {
        return SpesifikkKontekst("Arbeidsgiver", mapOf("organisasjonsnummer" to organisasjonsnummer))
    }

    internal fun lås(periode: Periode) {
        sykdomshistorikk.sykdomstidslinje().lås(periode)
    }

    internal fun overlapper(periode: Periode) = sykdomstidslinje().periode()?.overlapperMed(periode) ?: false

    internal fun nåværendeVedtaksperiode(): Vedtaksperiode? {
        return vedtaksperioder.firstOrNull { !it.erFerdigBehandlet() }
    }

    internal fun harHistorikk() = !sykdomshistorikk.isEmpty()

    internal fun oppdatertUtbetalingstidslinje(
        inntektDatoer: List<LocalDate>,
        sammenhengendePeriode: Periode,
        ytelser: Ytelser
    ): Utbetalingstidslinje {
        return UtbetalingstidslinjeBuilder(
            sammenhengendePeriode = sammenhengendePeriode,
            inntektshistorikk = inntektshistorikk.subset(inntektDatoer.map { it.minusDays(1) }),
            inntektshistorikkVol2 = inntektshistorikkVol2,
            forlengelseStrategy = { sykdomstidslinje ->
                Oldtidsutbetalinger().let { oldtid ->
                    ytelser.utbetalingshistorikk().append(oldtid)
                    utbetalteUtbetalinger()
                        .forEach { it.append(organisasjonsnummer, oldtid) }
                    oldtid.utbetalingerInkludert(this)
                        .arbeidsgiverperiodeErBetalt(requireNotNull(sykdomstidslinje.periode()))
                }
            },
            arbeidsgiverRegler = NormalArbeidstaker
        ).result(sykdomstidslinje())
    }

    fun støtterReplayFor(vedtaksperiode: Vedtaksperiode): Boolean {
        return finnSykeperiodeRettEtter(vedtaksperiode) == null
            && !sykdomstidslinje().harNyArbeidsgiverperiodeEtter(vedtaksperiode.periode().endInclusive)
    }

    internal fun grunnlagForSammenligningsgrunnlag(dato: LocalDate) =
        inntektshistorikkVol2.grunnlagForSammenligningsgrunnlag(dato)

    internal class JsonRestorer private constructor() {
        internal companion object {
            internal fun restore(
                person: Person,
                organisasjonsnummer: String,
                id: UUID,
                inntektshistorikk: Inntektshistorikk,
                inntektshistorikkVol2: InntektshistorikkVol2,
                sykdomshistorikk: Sykdomshistorikk,
                vedtaksperioder: MutableList<Vedtaksperiode>,
                forkastede: SortedMap<Vedtaksperiode, ForkastetÅrsak>,
                utbetalinger: MutableList<Utbetaling>
            ) = Arbeidsgiver(
                person,
                organisasjonsnummer,
                id,
                inntektshistorikk,
                inntektshistorikkVol2,
                sykdomshistorikk,
                vedtaksperioder,
                forkastede,
                utbetalinger
            )
        }
    }
}

internal enum class ForkastetÅrsak {
    IKKE_STØTTET,
    UKJENT,
    ERSTATTES,
    ANNULLERING
}

internal fun List<Arbeidsgiver>.sammenhengendeSykeperioder() = Arbeidsgiver.sammenhengendeSykeperioder(this)

internal typealias VedtaksperioderSelector = (Arbeidsgiver, Vedtaksperiode) -> MutableList<Vedtaksperiode>
