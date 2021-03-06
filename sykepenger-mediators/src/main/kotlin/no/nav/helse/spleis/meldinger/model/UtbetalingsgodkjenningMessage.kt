package no.nav.helse.spleis.meldinger.model

import no.nav.helse.hendelser.Utbetalingsgodkjenning
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Behov.Behovtype.Godkjenning
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.spleis.IHendelseMediator
import no.nav.helse.spleis.MessageDelegate
import java.util.*

// Understands a JSON message representing a Godkjenning-behov
internal class UtbetalingsgodkjenningMessage(packet: MessageDelegate) : BehovMessage(packet) {
    private val utbetalingId = packet["utbetalingId"].asText()
    private val vedtaksperiodeId = packet["vedtaksperiodeId"].asText()
    private val organisasjonsnummer = packet["organisasjonsnummer"].asText()
    private val aktørId = packet["aktørId"].asText()
    private val saksbehandler = packet["@løsning.${Godkjenning.name}.saksbehandlerIdent"].asText()
    private val saksbehandlerEpost = packet["@løsning.${Godkjenning.name}.saksbehandlerEpost"].asText()
    private val godkjenttidspunkt = packet["@løsning.${Godkjenning.name}.godkjenttidspunkt"].asLocalDateTime()
    private val utbetalingGodkjent = packet["@løsning.${Godkjenning.name}.godkjent"].asBoolean()
    private val automatiskBehandling = packet["@løsning.${Godkjenning.name}.automatiskBehandling"].asBoolean()
    private val makstidOppnådd = packet["@løsning.${Godkjenning.name}.makstidOppnådd"].asBoolean()

    private val utbetalingsgodkjenning
        get() = Utbetalingsgodkjenning(
            meldingsreferanseId = id,
            aktørId = aktørId,
            fødselsnummer = fødselsnummer,
            organisasjonsnummer = organisasjonsnummer,
            utbetalingId = UUID.fromString(utbetalingId),
            vedtaksperiodeId = vedtaksperiodeId,
            saksbehandler = saksbehandler,
            saksbehandlerEpost = saksbehandlerEpost,
            godkjenttidspunkt = godkjenttidspunkt,
            utbetalingGodkjent = utbetalingGodkjent,
            automatiskBehandling = automatiskBehandling,
            makstidOppnådd = makstidOppnådd
        )

    override fun behandle(mediator: IHendelseMediator) {
        mediator.behandle(this, utbetalingsgodkjenning)
    }
}
