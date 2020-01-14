package no.nav.helse.hendelser

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import java.time.LocalDate
import java.util.UUID

abstract class SøknadHendelse protected constructor(
    hendelseId: UUID,
    hendelsestype: Hendelsestype,
    protected val søknad: JsonNode
) : SykdomstidslinjeHendelse(hendelseId, hendelsestype) {

    private companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private val aktørId = søknad["aktorId"].asText()!!
    private val fnr = søknad["fnr"].asText()!!

    internal val sykeperioder
        get() = søknad["soknadsperioder"]?.map {
            Sykeperiode(
                it
            )
        } ?: emptyList()

    private val arbeidsgiver: Arbeidsgiver
        get() = søknad["arbeidsgiver"].let {
            Arbeidsgiver(
                it
            )
        }

    override fun aktørId() = aktørId

    override fun fødselsnummer(): String = fnr

    override fun organisasjonsnummer(): String = arbeidsgiver.orgnummer

    override fun kanBehandles(): Boolean {
        return søknad.hasNonNull("fnr")
            && søknad["arbeidsgiver"]?.hasNonNull("orgnummer") == true
    }

    override fun toJson(): String = objectMapper.writeValueAsString(
        mapOf(
            "hendelseId" to hendelseId(),
            "type" to hendelsetype(),
            "søknad" to søknad
        )
    )

    internal class Arbeidsgiver(val jsonNode: JsonNode) {
        val orgnummer: String get() = jsonNode["orgnummer"].textValue()
    }

    internal class Sykeperiode(jsonNode: JsonNode) {
        val fom: LocalDate = LocalDate.parse(jsonNode["fom"].textValue())
        val tom: LocalDate = LocalDate.parse(jsonNode["tom"].textValue())
        val sykmeldingsgrad: Int = jsonNode["sykmeldingsgrad"].intValue()
        val faktiskGrad: Int? = jsonNode["faktiskGrad"]?.let {
            if (!it.isNull) it.intValue() else null
        }
    }
}
