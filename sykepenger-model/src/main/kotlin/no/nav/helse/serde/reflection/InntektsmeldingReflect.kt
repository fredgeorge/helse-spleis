package no.nav.helse.serde.reflection

import no.nav.helse.hendelser.ModelInntektsmelding
import no.nav.helse.person.Aktivitetslogger
import no.nav.helse.person.ArbeidstakerHendelse.Hendelsestype
import no.nav.helse.serde.reflection.ReflectInstance.Companion.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class InntektsmeldingReflect(inntektsmelding: ModelInntektsmelding) {
    private val hendelseId: UUID = inntektsmelding.hendelseId()
    private val hendelsestype: Hendelsestype = inntektsmelding.hendelsestype()
    private val refusjon: ModelInntektsmelding.Refusjon = inntektsmelding["refusjon"]
    private val orgnummer: String = inntektsmelding["orgnummer"]
    private val fødselsnummer: String = inntektsmelding["fødselsnummer"]
    private val aktørId: String = inntektsmelding["aktørId"]
    private val mottattDato: LocalDateTime = inntektsmelding["mottattDato"]
    private val førsteFraværsdag: LocalDate = inntektsmelding["førsteFraværsdag"]
    private val beregnetInntekt: Double = inntektsmelding["beregnetInntekt"]
    private val aktivitetslogger: Aktivitetslogger = inntektsmelding["aktivitetslogger"]
    private val arbeidsgiverperioder: List<ModelInntektsmelding.InntektsmeldingPeriode.Arbeidsgiverperiode> =
        inntektsmelding["arbeidsgiverperioder"]
    private val ferieperioder: List<ModelInntektsmelding.InntektsmeldingPeriode.Ferieperiode> =
        inntektsmelding["ferieperioder"]

    fun toMap() = mutableMapOf<String, Any?>(
        "type" to hendelsestype.name,
        "data" to mutableMapOf<String, Any?>(
            "hendelseId" to hendelseId,
            "refusjon" to refusjon,
            "orgnummer" to orgnummer,
            "fødselsnummer" to fødselsnummer,
            "aktørId" to aktørId,
            "mottattDato" to mottattDato,
            "førsteFraværsdag" to førsteFraværsdag,
            "beregnetInntekt" to beregnetInntekt,
            "aktivitetslogger" to AktivitetsloggerReflect(aktivitetslogger).toMap(),
            "arbeidsgiverperioder" to arbeidsgiverperioder.map(::periodeToMap),
            "ferieperioder" to ferieperioder.map(::periodeToMap)
        )
    )

    internal fun toSpeilMap() = mutableMapOf<String, Any?>(
        "type" to hendelsestype.name,
        "hendelseId" to hendelseId,
        "refusjon" to refusjon,
        "orgnummer" to orgnummer,
        "fødselsnummer" to fødselsnummer,
        "aktørId" to aktørId,
        "mottattDato" to mottattDato,
        "førsteFraværsdag" to førsteFraværsdag,
        "beregnetInntekt" to beregnetInntekt,
        "arbeidsgiverperioder" to arbeidsgiverperioder.map(::periodeToMap),
        "ferieperioder" to ferieperioder.map(::periodeToMap)
    )

    private fun periodeToMap(periode: ModelInntektsmelding.InntektsmeldingPeriode) = mutableMapOf<String, Any?>(
        "fom" to periode.fom,
        "tom" to periode.tom
    )
}
