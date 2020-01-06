package no.nav.helse.person


import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.DecimalNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.behov.Behov
import no.nav.helse.behov.Behovtype
import no.nav.helse.hendelser.*
import no.nav.helse.person.TilstandType.*
import no.nav.helse.person.VedtaksperiodeObserver.StateChangeEvent
import no.nav.helse.serde.safelyUnwrapDate
import no.nav.helse.sykdomstidslinje.ConcreteSykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.sykdomstidslinje.Utbetalingslinje
import no.nav.helse.sykdomstidslinje.joinForOppdrag
import org.apache.commons.codec.binary.Base32
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.time.Duration
import java.time.LocalDate
import java.util.*
private inline fun <reified T> Set<*>.førsteAvType(): T {
    return first { it is T } as T
}

internal class Vedtaksperiode internal constructor(
    private val id: UUID,
    private val aktørId: String,
    private val fødselsnummer: String,
    private val organisasjonsnummer: String,
    private var sykdomstidslinje: ConcreteSykdomstidslinje,
    private var tilstand: Vedtaksperiodetilstand = StartTilstand
) {

    private val `6G` = (6 * 99858)

    private var maksdato: LocalDate? = null

    private var utbetalingslinjer: List<Utbetalingslinje>? = null

    private var godkjentAv: String? = null

    private var utbetalingsreferanse: String? = null

    private val observers: MutableList<VedtaksperiodeObserver> = mutableListOf()

    private fun inntektsmeldingHendelse() =
        this.sykdomstidslinje.hendelser().førsteAvType<Inntektsmelding>()

    internal fun dagsats() = inntektsmeldingHendelse().dagsats(`6G`)

    internal fun håndter(nySøknad: NySøknad) = overlapperMed(nySøknad).also {
        if (it) tilstand.håndter(this, nySøknad)
    }

    internal fun håndter(sendtSøknad: SendtSøknad) = overlapperMed(sendtSøknad).also {
        if (it) tilstand.håndter(this, sendtSøknad)
    }

    internal fun håndter(inntektsmelding: Inntektsmelding): Boolean {
        return overlapperMed(inntektsmelding).also {
            if (it) {
                tilstand.håndter(this, inntektsmelding)
            }
        }
    }

    internal fun håndter(person: Person, arbeidsgiver: Arbeidsgiver, ytelser: Ytelser) {
        if (id.toString() == ytelser.vedtaksperiodeId()) tilstand.håndter(
            person,
            arbeidsgiver,
            this,
            ytelser
        )
    }

    internal fun håndter(manuellSaksbehandling: ManuellSaksbehandling) {
        if (id.toString() == manuellSaksbehandling.vedtaksperiodeId()) tilstand.håndter(
            this,
            manuellSaksbehandling
        )
    }

    internal fun håndter(vilkårsgrunnlag: Vilkårsgrunnlag) {
        if (id.toString() == vilkårsgrunnlag.vedtaksperiodeId()) tilstand.håndter(
            this,
            vilkårsgrunnlag
        )
    }

    internal fun håndter(påminnelse: Påminnelse): Boolean {
        if (id.toString() != påminnelse.vedtaksperiodeId()) return false
        tilstand.håndter(this, påminnelse)
        return true
    }

    internal fun invaliderPeriode(hendelse: ArbeidstakerHendelse) {
        setTilstand(hendelse, TilInfotrygd)
    }

    private fun overlapperMed(hendelse: SykdomstidslinjeHendelse) =
        this.sykdomstidslinje.overlapperMed(hendelse.sykdomstidslinje())

    private fun setTilstand(event: ArbeidstakerHendelse, nyTilstand: Vedtaksperiodetilstand, block: () -> Unit = {}) {
        tilstand.leaving()

        val previousStateName = tilstand.type

        tilstand = nyTilstand
        block()

        tilstand.entering(this)

        emitVedtaksperiodeEndret(tilstand.type, event, previousStateName, tilstand.timeout)
    }

    private fun håndter(hendelse: SykdomstidslinjeHendelse, nesteTilstand: Vedtaksperiodetilstand) {
        val tidslinje = this.sykdomstidslinje.plus(hendelse.sykdomstidslinje())

        if (tidslinje.erUtenforOmfang()) {
            setTilstand(hendelse, TilInfotrygd)
        } else {
            setTilstand(hendelse, nesteTilstand) {
                sykdomstidslinje = tidslinje
            }
        }
    }

    private fun trengerYtelser() {
        emitTrengerLøsning(Ytelser.lagBehov(
            vedtaksperiodeId = id,
            aktørId = aktørId,
            fødselsnummer = fødselsnummer,
            organisasjonsnummer = organisasjonsnummer,
            utgangspunktForBeregningAvYtelse = sykdomstidslinje.utgangspunktForBeregningAvYtelse().minusDays(1)
        ))
    }

    private fun trengerVilkårsgrunnlag() {
        emitTrengerLøsning(Vilkårsgrunnlag.lagBehov(
            vedtaksperiodeId = id,
            aktørId = aktørId,
            fødselsnummer = fødselsnummer,
            organisasjonsnummer = organisasjonsnummer
        ))
    }

    private fun emitTrengerLøsning(behov: Behov) {
        observers.forEach { observer ->
            observer.vedtaksperiodeTrengerLøsning(behov)
        }
    }

    // Gang of four Observer pattern
    internal fun addVedtaksperiodeObserver(observer: VedtaksperiodeObserver) {
        observers.add(observer)
    }

    private fun emitVedtaksperiodeEndret(
        currentState: TilstandType,
        tidslinjeEvent: ArbeidstakerHendelse,
        previousState: TilstandType,
        varighet: Duration
    ) {
        val event = StateChangeEvent(
            id = id,
            aktørId = aktørId,
            fødselsnummer = fødselsnummer,
            organisasjonsnummer = organisasjonsnummer,
            gjeldendeTilstand = currentState,
            forrigeTilstand = previousState,
            sykdomshendelse = tidslinjeEvent,
            timeout = varighet
        )

        observers.forEach { observer ->
            observer.vedtaksperiodeEndret(event)
        }
    }

    private fun emitVedtaksperiodePåminnet(påminnelse: Påminnelse) {
        observers.forEach { observer ->
            observer.vedtaksperiodePåminnet(påminnelse)
        }
    }

    // Gang of four State pattern
    internal interface Vedtaksperiodetilstand {
        val type: TilstandType

        val timeout: Duration

        // Default implementasjoner av transisjonene
        fun håndter(vedtaksperiode: Vedtaksperiode, nySøknad: NySøknad) {
            vedtaksperiode.setTilstand(nySøknad, TilInfotrygd)
        }

        fun håndter(vedtaksperiode: Vedtaksperiode, sendtSøknad: SendtSøknad) {
            vedtaksperiode.setTilstand(sendtSøknad, TilInfotrygd)
        }

        fun håndter(vedtaksperiode: Vedtaksperiode, inntektsmelding: Inntektsmelding) {
            vedtaksperiode.setTilstand(inntektsmelding, TilInfotrygd)
        }

        fun håndter(vedtaksperiode: Vedtaksperiode, vilkårsgrunnlag: Vilkårsgrunnlag) {
        }

        fun håndter(person: Person, arbeidsgiver: Arbeidsgiver, vedtaksperiode: Vedtaksperiode, ytelser: Ytelser) {
        }

        fun håndter(vedtaksperiode: Vedtaksperiode, manuellSaksbehandling: ManuellSaksbehandling) {
        }

        fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            if (!påminnelse.gjelderTilstand(type)) return
            vedtaksperiode.emitVedtaksperiodePåminnet(påminnelse)
            vedtaksperiode.setTilstand(påminnelse, TilInfotrygd)
        }

        fun leaving() {
        }

        fun entering(vedtaksperiode: Vedtaksperiode) {
        }

    }

    private object StartTilstand : Vedtaksperiodetilstand {

        override fun håndter(vedtaksperiode: Vedtaksperiode, nySøknad: NySøknad) {
            val tidslinje = nySøknad.sykdomstidslinje()
            if (tidslinje.erUtenforOmfang()) return vedtaksperiode.setTilstand(nySøknad, TilInfotrygd)

            vedtaksperiode.setTilstand(nySøknad, MottattNySøknad) {
                vedtaksperiode.sykdomstidslinje = tidslinje
            }
        }
        override val type = START
        override val timeout: Duration = Duration.ofDays(30)

    }

    private object MottattNySøknad : Vedtaksperiodetilstand {

        override fun håndter(vedtaksperiode: Vedtaksperiode, sendtSøknad: SendtSøknad) {
            vedtaksperiode.håndter(sendtSøknad, MottattSendtSøknad)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, inntektsmelding: Inntektsmelding) {
            vedtaksperiode.håndter(inntektsmelding, MottattInntektsmelding)
        }
        override val type = MOTTATT_NY_SØKNAD

        override val timeout: Duration = Duration.ofDays(30)

    }

    private object MottattSendtSøknad : Vedtaksperiodetilstand {

        override fun håndter(vedtaksperiode: Vedtaksperiode, inntektsmelding: Inntektsmelding) {
            vedtaksperiode.håndter(inntektsmelding, Vilkårsprøving)
        }
        override val type = MOTTATT_SENDT_SØKNAD

        override val timeout: Duration = Duration.ofDays(30)

    }

    internal object MottattInntektsmelding : Vedtaksperiodetilstand {
        override fun håndter(vedtaksperiode: Vedtaksperiode, sendtSøknad: SendtSøknad) {
            vedtaksperiode.håndter(sendtSøknad, Vilkårsprøving)
        }
        override val type = MOTTATT_INNTEKTSMELDING

        override val timeout: Duration = Duration.ofDays(30)

    }

    internal object Vilkårsprøving : Vedtaksperiodetilstand {
        override val type = VILKÅRSPRØVING

        override val timeout = Duration.ofHours(1)

        override fun entering(vedtaksperiode: Vedtaksperiode) {
            vedtaksperiode.trengerVilkårsgrunnlag()
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            vedtaksperiode.trengerVilkårsgrunnlag()
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, vilkårsgrunnlag: Vilkårsgrunnlag) {
            if (vilkårsgrunnlag.erEgenAnsatt()) return vedtaksperiode.setTilstand(vilkårsgrunnlag, TilInfotrygd)
            vedtaksperiode.setTilstand(vilkårsgrunnlag, BeregnUtbetaling)
        }

    }

    internal object BeregnUtbetaling : Vedtaksperiodetilstand {

        override val type = BEREGN_UTBETALING
        override val timeout: Duration = Duration.ofHours(1)

        private const val seksMåneder = 180

        override fun entering(vedtaksperiode: Vedtaksperiode) {
            vedtaksperiode.trengerYtelser()
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            if (!påminnelse.gjelderTilstand(type)) return
            vedtaksperiode.emitVedtaksperiodePåminnet(påminnelse)
            vedtaksperiode.trengerYtelser()
        }

        override fun håndter(person: Person, arbeidsgiver: Arbeidsgiver, vedtaksperiode: Vedtaksperiode, ytelser: Ytelser) {
            if (harFraværsdagInnen6Mnd(ytelser, vedtaksperiode.sykdomstidslinje)) {
                return vedtaksperiode.setTilstand(ytelser, TilInfotrygd)
            }

            val utbetalingsberegning = try {
                vedtaksperiode.sykdomstidslinje.utbetalingsberegning(vedtaksperiode.dagsats(), vedtaksperiode.fødselsnummer)
            } catch (ie: IllegalArgumentException) {
                return vedtaksperiode.setTilstand(ytelser, TilInfotrygd)
            }

            val sisteUtbetalingsdag = utbetalingsberegning.utbetalingslinjer.lastOrNull()?.tom
            if (sisteUtbetalingsdag == null || vedtaksperiode.inntektsmeldingHendelse().harEndringIRefusjon(sisteUtbetalingsdag))  {
                return vedtaksperiode.setTilstand(ytelser, TilInfotrygd)
            }

            vedtaksperiode.setTilstand(ytelser, TilGodkjenning) {
                vedtaksperiode.maksdato = utbetalingsberegning.maksdato
                vedtaksperiode.utbetalingslinjer = utbetalingsberegning.utbetalingslinjer
            }
        }

        private fun harFraværsdagInnen6Mnd(
            ytelser: Ytelser,
            tidslinje: ConcreteSykdomstidslinje
        ): Boolean {
            val sisteFraværsdag = ytelser.sykepengehistorikk().sisteFraværsdag() ?: return false

            return sisteFraværsdag > tidslinje.utgangspunktForBeregningAvYtelse()
                || sisteFraværsdag.datesUntil(tidslinje.utgangspunktForBeregningAvYtelse()).count() <= seksMåneder
        }
    }

    private object TilGodkjenning : Vedtaksperiodetilstand {
        override val type = TIL_GODKJENNING
        override val timeout: Duration = Duration.ofDays(7)

        override fun entering(vedtaksperiode: Vedtaksperiode) {
            vedtaksperiode.emitTrengerLøsning(ManuellSaksbehandling.lagBehov(
                vedtaksperiode.id, vedtaksperiode.aktørId, vedtaksperiode.fødselsnummer, vedtaksperiode.organisasjonsnummer
            ))
        }

        override fun håndter(
            vedtaksperiode: Vedtaksperiode,
            manuellSaksbehandling: ManuellSaksbehandling
        ) {
            if (manuellSaksbehandling.utbetalingGodkjent()) {
                vedtaksperiode.setTilstand(manuellSaksbehandling, TilUtbetaling) {
                    vedtaksperiode.godkjentAv = manuellSaksbehandling.saksbehandler()
                }
            } else {
                vedtaksperiode.setTilstand(manuellSaksbehandling, TilInfotrygd)
            }
        }
    }

    private object TilUtbetaling : Vedtaksperiodetilstand {
        override val type = TIL_UTBETALING
        override val timeout: Duration = Duration.ZERO

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {}

        override fun entering(vedtaksperiode: Vedtaksperiode) {
            val utbetalingsreferanse = lagUtbetalingsReferanse(vedtaksperiode)
            vedtaksperiode.utbetalingsreferanse = utbetalingsreferanse


            vedtaksperiode.emitTrengerLøsning(Behov.nyttBehov(
                hendelsetype = ArbeidstakerHendelse.Hendelsetype.Utbetaling,
                behov = listOf(Behovtype.Utbetaling),
                aktørId = vedtaksperiode.aktørId,
                fødselsnummer = vedtaksperiode.fødselsnummer,
                organisasjonsnummer = vedtaksperiode.organisasjonsnummer,
                vedtaksperiodeId = vedtaksperiode.id,
                additionalParams = mapOf(
                    "utbetalingsreferanse" to utbetalingsreferanse,
                    "utbetalingslinjer" to (vedtaksperiode.utbetalingslinjer?.joinForOppdrag() ?: emptyList()),
                    "maksdato" to (vedtaksperiode.maksdato ?: ""),
                    "saksbehandler" to (vedtaksperiode.godkjentAv ?: "")
                )
            ))

            val event = VedtaksperiodeObserver.UtbetalingEvent(
                vedtaksperiodeId = vedtaksperiode.id,
                aktørId = vedtaksperiode.aktørId,
                fødselsnummer = vedtaksperiode.fødselsnummer,
                organisasjonsnummer = vedtaksperiode.organisasjonsnummer,
                utbetalingsreferanse = utbetalingsreferanse,
                utbetalingslinjer = vedtaksperiode.utbetalingslinjer ?: emptyList(),
                opprettet = LocalDate.now()
            )
            vedtaksperiode.observers.forEach {
                it.vedtaksperiodeTilUtbetaling(event)
            }
        }

        private fun lagUtbetalingsReferanse(vedtaksperiode: Vedtaksperiode) = vedtaksperiode.id.base32Encode()

        private fun UUID.base32Encode(): String {
            val pad = '='
            return Base32(pad.toByte())
                .encodeAsString(this.byteArray())
                .replace(pad.toString(), "")
        }

        private fun UUID.byteArray() = ByteBuffer.allocate(Long.SIZE_BYTES * 2).apply {
            putLong(this@byteArray.mostSignificantBits)
            putLong(this@byteArray.leastSignificantBits)
        }.array()
    }

    private object TilInfotrygd : Vedtaksperiodetilstand {
        override val type = TIL_INFOTRYGD
        override val timeout: Duration = Duration.ZERO

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {}
    }

    // Gang of four Memento pattern
    companion object {

        internal fun restore(memento: Memento): Vedtaksperiode {
            return Vedtaksperiode(
                id = memento.id,
                aktørId = memento.aktørId,
                fødselsnummer = memento.fødselsnummer,
                organisasjonsnummer = memento.organisasjonsnummer,
                sykdomstidslinje = ConcreteSykdomstidslinje.fromJson(memento.sykdomstidslinje.toString())
            ).also {
                it.tilstand = tilstandFraEnum(memento.tilstandType)
                it.maksdato = memento.maksdato
                it.utbetalingslinjer = memento.utbetalingslinjer?.map {
                    Utbetalingslinje(
                        fom = LocalDate.parse(it["fom"].textValue()),
                        tom = LocalDate.parse(it["tom"].textValue()),
                        dagsats = when (it["dagsats"]) {
                            is DecimalNode -> it["dagsats"].decimalValue().setScale(0, RoundingMode.HALF_UP).toInt()
                            else -> it["dagsats"].intValue()
                        }
                    )
                }
                it.godkjentAv = memento.godkjentAv
                it.utbetalingsreferanse = memento.utbetalingsreferanse
            }
        }

        private fun tilstandFraEnum(tilstand: TilstandType) = when (tilstand) {
            START -> StartTilstand
            MOTTATT_NY_SØKNAD -> MottattNySøknad
            MOTTATT_SENDT_SØKNAD -> MottattSendtSøknad
            MOTTATT_INNTEKTSMELDING -> MottattInntektsmelding
            VILKÅRSPRØVING -> Vilkårsprøving
            BEREGN_UTBETALING -> BeregnUtbetaling
            TIL_GODKJENNING -> TilGodkjenning
            TIL_UTBETALING -> TilUtbetaling
            TIL_INFOTRYGD -> TilInfotrygd
        }

        internal fun nyPeriode(hendelse: SykdomstidslinjeHendelse, id: UUID = UUID.randomUUID()): Vedtaksperiode {
            return Vedtaksperiode(
                id = id,
                aktørId = hendelse.aktørId(),
                fødselsnummer = hendelse.fødselsnummer(),
                organisasjonsnummer = hendelse.organisasjonsnummer(),
                sykdomstidslinje = hendelse.sykdomstidslinje()
            )
        }

        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    internal fun memento(): Memento {
        return Memento(
            id = id,
            aktørId = aktørId,
            fødselsnummer = fødselsnummer,
            organisasjonsnummer = organisasjonsnummer,
            tilstandType = tilstand.type,
            sykdomstidslinje = objectMapper.readTree(sykdomstidslinje.toJson()),
            maksdato = maksdato,
            utbetalingslinjer = utbetalingslinjer
                ?.let { objectMapper.convertValue<JsonNode>(it) },
            godkjentAv = godkjentAv,
            utbetalingsreferanse = utbetalingsreferanse
        )
    }

    internal class Memento internal constructor(
        internal val id: UUID,
        internal val aktørId: String,
        internal val fødselsnummer: String,
        internal val organisasjonsnummer: String,
        internal val tilstandType: TilstandType,
        internal val sykdomstidslinje: JsonNode,
        internal val maksdato: LocalDate?,
        internal val utbetalingslinjer: JsonNode?,
        internal val godkjentAv: String?,
        internal val utbetalingsreferanse: String?
    ) {

        internal companion object {
            private val objectMapper = jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

            fun fromString(state: String): Memento {
                val json = objectMapper.readTree(state)

                return Memento(
                    id = UUID.fromString(json["id"].textValue()),
                    aktørId = json["aktørId"].textValue(),
                    fødselsnummer = json["fødselsnummer"].textValue(),
                    organisasjonsnummer = json["organisasjonsnummer"].textValue(),
                    tilstandType = valueOf(json["tilstandType"].textValue()),
                    sykdomstidslinje = json["sykdomstidslinje"],
                    maksdato = json["maksdato"].safelyUnwrapDate(),
                    utbetalingslinjer = json["utbetalingslinjer"]?.takeUnless { it.isNull },
                    godkjentAv = json["godkjentAv"]?.textValue(),
                    utbetalingsreferanse = json["utbetalingsreferanse"]?.textValue()
                )
            }
        }

        fun state(): String =
            objectMapper.writeValueAsString(
                mapOf(
                    "id" to this.id,
                    "aktørId" to this.aktørId,
                    "fødselsnummer" to this.fødselsnummer,
                    "organisasjonsnummer" to this.organisasjonsnummer,
                    "tilstandType" to this.tilstandType,
                    "sykdomstidslinje" to this.sykdomstidslinje,
                    "maksdato" to this.maksdato,
                    "utbetalingslinjer" to this.utbetalingslinjer,
                    "godkjentAv" to this.godkjentAv,
                    "utbetalingsreferanse" to this.utbetalingsreferanse
                )
            )
    }
}
