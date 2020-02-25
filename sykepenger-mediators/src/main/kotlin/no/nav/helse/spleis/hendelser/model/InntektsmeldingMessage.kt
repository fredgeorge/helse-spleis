package no.nav.helse.spleis.hendelser.model

import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.Aktivitetslogger
import no.nav.helse.spleis.hendelser.*
import no.nav.helse.spleis.rest.HendelseDTO
import java.util.*

// Understands a JSON message representing an Inntektsmelding
internal class InntektsmeldingMessage(
    originalMessage: String,
    private val aktivitetslogger: Aktivitetslogger,
    private val aktivitetslogg: Aktivitetslogg
) :
    JsonMessage(originalMessage, aktivitetslogger, aktivitetslogg) {
    init {
        requiredKey(
            "inntektsmeldingId", "arbeidstakerFnr",
            "arbeidstakerAktorId", "virksomhetsnummer",
            "arbeidsgivertype", "beregnetInntekt",
            "endringIRefusjoner", "arbeidsgiverperioder",
            "status", "arkivreferanse", "ferieperioder",
            "foersteFravaersdag", "mottattDato"
        )
        interestedIn("refusjon.beloepPrMnd")
        interestedIn("refusjon.opphoersdato")
    }

    override val id: UUID get() = UUID.fromString(this["inntektsmeldingId"].textValue())

    override fun accept(processor: MessageProcessor) {
        processor.process(this)
    }

    private val refusjon
        get() = this["refusjon.beloepPrMnd"].takeUnless { it.isMissingNode || it.isNull }?.let { beløpPerMåned ->
            Inntektsmelding.Refusjon(
                this["refusjon.opphoersdato"].asOptionalLocalDate(),
                beløpPerMåned.asDouble(),
                this["endringIRefusjoner"].map { it.path("endringsdato").asLocalDate() }
            )
        }
    private val orgnummer get() = this["virksomhetsnummer"].asText()
    private val fødselsnummer get() = this["arbeidstakerFnr"].asText()
    private val aktørId get() = this["arbeidstakerAktorId"].asText()
    private val mottattDato get() = this["mottattDato"].asLocalDateTime()
    private val førsteFraværsdag get() = this["foersteFravaersdag"].asLocalDate()
    private val beregnetInntekt get() = this["beregnetInntekt"].asDouble()
    private val arbeidsgiverperioder get() = this["arbeidsgiverperioder"].map(::asPeriode)
    private val ferieperioder get() = this["ferieperioder"].map(::asPeriode)

    internal fun asInntektsmelding() = Inntektsmelding(
        meldingsreferanseId = this.id,
        refusjon = refusjon,
        orgnummer = orgnummer,
        fødselsnummer = fødselsnummer,
        aktørId = aktørId,
        førsteFraværsdag = førsteFraværsdag,
        beregnetInntekt = beregnetInntekt,
        aktivitetslogger = aktivitetslogger,
        aktivitetslogg = aktivitetslogg,
        arbeidsgiverperioder = arbeidsgiverperioder,
        ferieperioder = ferieperioder
    )

    fun asSpeilDTO() = HendelseDTO.InntektsmeldingDTO(
        beregnetInntekt = beregnetInntekt,
        førsteFraværsdag = førsteFraværsdag
    )

    object Factory : MessageFactory {

        override fun createMessage(message: String, problems: Aktivitetslogger, aktivitetslogg: Aktivitetslogg) =
            InntektsmeldingMessage(message, problems, aktivitetslogg)
    }
}
