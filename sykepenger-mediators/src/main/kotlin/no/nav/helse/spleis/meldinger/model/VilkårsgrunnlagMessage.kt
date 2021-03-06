package no.nav.helse.spleis.meldinger.model

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.hendelser.Inntektsvurdering
import no.nav.helse.hendelser.Inntektsvurdering.Inntektsgrunnlag.SAMMENLIGNINGSGRUNNLAG
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.Opptjeningvurdering
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Behov.Behovtype.*
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asOptionalLocalDate
import no.nav.helse.rapids_rivers.asYearMonth
import no.nav.helse.spleis.IHendelseMediator
import no.nav.helse.spleis.MessageDelegate
import no.nav.helse.økonomi.Inntekt.Companion.månedlig

// Understands a JSON message representing a Vilkårsgrunnlagsbehov
internal class VilkårsgrunnlagMessage(packet: MessageDelegate) : BehovMessage(packet) {

    private val vedtaksperiodeId = packet["vedtaksperiodeId"].asText()
    private val organisasjonsnummer = packet["organisasjonsnummer"].asText()
    private val aktørId = packet["aktørId"].asText()

    private val inntekter = packet["@løsning.${InntekterForSammenligningsgrunnlag.name}"]
        .flatMap { måned ->
            måned["inntektsliste"]
                .groupBy({ inntekt -> inntekt.arbeidsgiver() }) { inntekt ->
                    Inntektsvurdering.ArbeidsgiverInntekt.MånedligInntekt(
                        yearMonth = måned["årMåned"].asYearMonth(),
                        inntekt = inntekt["beløp"].asDouble().månedlig,
                        type = inntekt["inntektstype"].asInntekttype(),
                        inntektsgrunnlag = SAMMENLIGNINGSGRUNNLAG,
                        fordel = if (inntekt.path("fordel").isTextual) inntekt["fordel"].asText() else "",
                        beskrivelse = if (inntekt.path("beskrivelse").isTextual) inntekt["beskrivelse"].asText() else ""
                    )
                }.toList()
        }
        .groupBy({ (arbeidsgiver, _) -> arbeidsgiver }) { (_, inntekter) -> inntekter }
        .map { (arbeidsgiver, inntekter) ->
            Inntektsvurdering.ArbeidsgiverInntekt(arbeidsgiver, inntekter.flatten())
        }

    private fun JsonNode.asInntekttype() = when (this.asText()) {
        "LOENNSINNTEKT" -> Inntektsvurdering.Inntekttype.LØNNSINNTEKT
        "NAERINGSINNTEKT" -> Inntektsvurdering.Inntekttype.NÆRINGSINNTEKT
        "PENSJON_ELLER_TRYGD" -> Inntektsvurdering.Inntekttype.PENSJON_ELLER_TRYGD
        "YTELSE_FRA_OFFENTLIGE" -> Inntektsvurdering.Inntekttype.YTELSE_FRA_OFFENTLIGE
        else -> error("Kunne ikke mappe Inntekttype")
    }

    private fun JsonNode.arbeidsgiver() = when {
        path("orgnummer").isTextual -> path("orgnummer").asText()
        path("fødselsnummer").isTextual -> path("fødselsnummer").asText()
        path("aktørId").isTextual -> path("aktørId").asText()
        else -> error("Mangler arbeidsgiver for inntekt i hendelse $id")
    }

    private val arbeidsforhold = packet["@løsning.${Opptjening.name}"].map {
        Opptjeningvurdering.Arbeidsforhold(
            orgnummer = it["orgnummer"].asText(),
            fom = it["ansattSiden"].asLocalDate(),
            tom = it["ansattTil"].asOptionalLocalDate()
        )
    }

    private val medlemskapstatus = when (packet["@løsning.${Medlemskap.name}.resultat.svar"].asText()) {
        "JA" -> Medlemskapsvurdering.Medlemskapstatus.Ja
        "NEI" -> Medlemskapsvurdering.Medlemskapstatus.Nei
        else -> Medlemskapsvurdering.Medlemskapstatus.VetIkke
    }

    private val vilkårsgrunnlag
        get() = Vilkårsgrunnlag(
            meldingsreferanseId = this.id,
            vedtaksperiodeId = vedtaksperiodeId,
            aktørId = aktørId,
            fødselsnummer = fødselsnummer,
            orgnummer = organisasjonsnummer,
            inntektsvurdering = Inntektsvurdering(
                inntekter = inntekter
            ),
            opptjeningvurdering = Opptjeningvurdering(
                arbeidsforhold = arbeidsforhold
            ),
            medlemskapsvurdering = Medlemskapsvurdering(
                medlemskapstatus = medlemskapstatus
            )
        )

    override fun behandle(mediator: IHendelseMediator) {
        mediator.behandle(this, vilkårsgrunnlag)
    }

}
