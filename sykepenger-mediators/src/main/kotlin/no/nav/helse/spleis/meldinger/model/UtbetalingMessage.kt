package no.nav.helse.spleis.meldinger.model

import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.hendelser.UtbetalingHendelse.Oppdragstatus
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Behov.Behovtype.Utbetaling
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.spleis.IHendelseMediator
import no.nav.helse.spleis.MessageDelegate

internal class UtbetalingMessage(packet: MessageDelegate) : BehovMessage(packet) {
    private val vedtaksperiodeId: String? = packet["vedtaksperiodeId"].takeUnless { it.isMissingOrNull() }?.asText()
    private val organisasjonsnummer = packet["organisasjonsnummer"].asText()
    private val aktørId = packet["aktørId"].asText()
    private val fagsystemId = packet["fagsystemId"].asText()
    private val status: Oppdragstatus = enumValueOf(packet["@løsning.${Utbetaling.name}.status"].asText())
    private val beskrivelse = packet["@løsning.${Utbetaling.name}.beskrivelse"].asText()
    private val saksbehandler = packet["saksbehandler"].asText()
    private val saksbehandlerEpost = packet["saksbehandlerEpost"].asText()
    private val godkjenttidspunkt = packet["godkjenttidspunkt"].asLocalDateTime()
    private val annullering = packet["annullering"].asBoolean()

    private val utbetaling
        get() = UtbetalingHendelse(
            meldingsreferanseId = id,
            vedtaksperiodeId = vedtaksperiodeId,
            aktørId = aktørId,
            fødselsnummer = fødselsnummer,
            orgnummer = organisasjonsnummer,
            utbetalingsreferanse = fagsystemId,
            status = status,
            melding = beskrivelse,
            godkjenttidspunkt = godkjenttidspunkt,
            saksbehandler = saksbehandler,
            saksbehandlerEpost = saksbehandlerEpost,
            annullert = annullering
        )

    override fun behandle(mediator: IHendelseMediator) {
        mediator.behandle(this, utbetaling)
    }
}
