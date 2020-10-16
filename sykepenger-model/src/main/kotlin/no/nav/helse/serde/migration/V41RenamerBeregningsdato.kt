package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

internal class V41RenamerBeregningsdato : JsonMigration(version = 41) {
    override val description: String = "beregningsdato renames til beregningsdatoFraInfotrygd"

    override fun doMigration(jsonNode: ObjectNode) {
        jsonNode.path("arbeidsgivere").forEach { arbeidsgiver ->
            renameBeregningsdato(arbeidsgiver.path("vedtaksperioder"))
            renameBeregningsdato(arbeidsgiver.path("forkastede"))
        }
    }

    private fun renameBeregningsdato(perioder: JsonNode) {
        perioder.forEach { periode ->
            periode as ObjectNode
            periode.path("beregningsdato")
                .takeIf(JsonNode::isTextual)
                ?.also { periode.put("beregningsdatoFraInfotrygd", it.textValue()) }
                ?: periode.putNull("beregningsdatoFraInfotrygd")
        }
    }

}