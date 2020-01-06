package no.nav.helse.person

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.util.RawValue
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.hendelser.*
import java.util.*

private const val CURRENT_SKJEMA_VERSJON = 3

class Person(private val aktørId: String, private val fødselsnummer: String) : VedtaksperiodeObserver {
    private val arbeidsgivere = mutableMapOf<String, Arbeidsgiver>()
    private var skjemaVersjon = CURRENT_SKJEMA_VERSJON

    private val observers = mutableListOf<PersonObserver>()

    fun håndter(nySøknad: NySøknad) {
        if (!nySøknad.kanBehandles()) {
            throw UtenforOmfangException("kan ikke behandle ny søknad", nySøknad)
        }

        if (arbeidsgivere.isNotEmpty()) {
            invaliderAllePerioder(nySøknad)
            throw UtenforOmfangException("støtter ikke forlengelse eller flere arbeidsgivere", nySøknad)
        }

        finnEllerOpprettArbeidsgiver(nySøknad).håndter(nySøknad)
    }

    fun håndter(sendtSøknad: SendtSøknad) {
        if (!sendtSøknad.kanBehandles()) {
            throw UtenforOmfangException("kan ikke behandle sendt søknad", sendtSøknad)
        }

        if (harAndreArbeidsgivere(sendtSøknad)) {
            invaliderAllePerioder(sendtSøknad)
            throw UtenforOmfangException(
                "forventer at vi har mottatt ny søknad for arbeidsgiver i sendt søknad, og bare én arbeidsgiver",
                sendtSøknad
            )
        }

        finnEllerOpprettArbeidsgiver(sendtSøknad).håndter(sendtSøknad)
    }

    fun håndter(inntektsmelding: Inntektsmelding) {
        if (!inntektsmelding.kanBehandles()) {
            invaliderAllePerioder(inntektsmelding)
            throw UtenforOmfangException("kan ikke behandle inntektsmelding", inntektsmelding)
        }
        finnEllerOpprettArbeidsgiver(inntektsmelding).håndter(inntektsmelding)
    }

    fun håndter(ytelser: Ytelser) {
        finnArbeidsgiver(ytelser)?.håndter(this, ytelser)
    }

    fun håndter(manuellSaksbehandling: ManuellSaksbehandling) {
        finnArbeidsgiver(manuellSaksbehandling)?.håndter(manuellSaksbehandling)
    }

    fun håndter(vilkårsgrunnlag: Vilkårsgrunnlag) {
        finnArbeidsgiver(vilkårsgrunnlag)?.håndter(vilkårsgrunnlag)
    }

    fun håndter(påminnelse: Påminnelse) {
        if (true == finnArbeidsgiver(påminnelse)?.håndter(påminnelse)) return
        observers.forEach {
            it.vedtaksperiodeIkkeFunnet(PersonObserver.VedtaksperiodeIkkeFunnetEvent(
                vedtaksperiodeId = UUID.fromString(påminnelse.vedtaksperiodeId()),
                aktørId = påminnelse.aktørId(),
                fødselsnummer = påminnelse.fødselsnummer(),
                organisasjonsnummer = påminnelse.organisasjonsnummer()
            ))
        }
    }

    override fun vedtaksperiodeEndret(event: VedtaksperiodeObserver.StateChangeEvent) {
        observers.forEach {
            it.personEndret(
                PersonObserver.PersonEndretEvent(
                    aktørId = aktørId,
                    sykdomshendelse = event.sykdomshendelse,
                    memento = memento()
                )
            )
        }
    }

    fun addObserver(observer: PersonObserver) {
        observers.add(observer)
        arbeidsgivere.values.forEach { it.addObserver(observer) }
    }

    private fun harAndreArbeidsgivere(hendelse: ArbeidstakerHendelse): Boolean {
        if (arbeidsgivere.isEmpty()) return false
        if (arbeidsgivere.size > 1) return true
        return !arbeidsgivere.containsKey(hendelse.organisasjonsnummer())
    }

    private fun invaliderAllePerioder(arbeidstakerHendelse: ArbeidstakerHendelse) {
        arbeidsgivere.forEach { (_, arbeidsgiver) ->
            arbeidsgiver.invaliderPerioder(arbeidstakerHendelse)
        }
    }

    private fun finnArbeidsgiver(hendelse: ArbeidstakerHendelse) =
        hendelse.organisasjonsnummer().let { arbeidsgivere[it] }

    private fun finnEllerOpprettArbeidsgiver(hendelse: ArbeidstakerHendelse) =
        hendelse.organisasjonsnummer().let { orgnr ->
            arbeidsgivere.getOrPut(orgnr) {
                arbeidsgiver(orgnr)
            }
        }

    private fun arbeidsgiver(organisasjonsnummer: String) =
        Arbeidsgiver(organisasjonsnummer).also {
            it.addObserver(this)
            observers.forEach { observer ->
                it.addObserver(observer)
            }
        }

    fun memento() =
        Memento(
            aktørId = this.aktørId,
            fødselsnummer = this.fødselsnummer,
            skjemaVersjon = this.skjemaVersjon,
            arbeidsgivere = this.arbeidsgivere.values.map { it.memento() }
        )

    class Memento internal constructor(
        internal val aktørId: String,
        internal val fødselsnummer: String,
        internal val skjemaVersjon: Int,
        internal val arbeidsgivere: List<Arbeidsgiver.Memento>
    ) {

        companion object {
            private val objectMapper = jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

            fun fromString(state: String): Memento {
                val jsonNode = objectMapper.readTree(state)

                if (!jsonNode.hasNonNull("skjemaVersjon")) throw PersonskjemaForGammelt(-1, CURRENT_SKJEMA_VERSJON)

                val skjemaVersjon = jsonNode["skjemaVersjon"].intValue()

                if (skjemaVersjon < CURRENT_SKJEMA_VERSJON) throw PersonskjemaForGammelt(skjemaVersjon, CURRENT_SKJEMA_VERSJON)

                return Memento(
                    aktørId = jsonNode["aktørId"].textValue(),
                    fødselsnummer = jsonNode["fødselsnummer"].textValue(),
                    skjemaVersjon = skjemaVersjon,
                    arbeidsgivere = jsonNode["arbeidsgivere"].map {
                        Arbeidsgiver.Memento.fromString(it.toString())
                    }
                )
            }
        }

        fun state(): String =
            objectMapper.convertValue<ObjectNode>(mapOf(
                "aktørId" to this.aktørId,
                "fødselsnummer" to this.fødselsnummer,
                "skjemaVersjon" to this.skjemaVersjon
            )).also {
                this.arbeidsgivere.fold(it.putArray("arbeidsgivere")) { result, current ->
                    result.addRawValue(RawValue(current.state()))
                }
            }.toString()
    }

    companion object {
        fun restore(memento: Memento): Person {
            return Person(memento.aktørId, memento.fødselsnummer)
                .apply {
                    this.arbeidsgivere.putAll(memento.arbeidsgivere.map {
                        Arbeidsgiver.restore(it).also {
                            it.addObserver(this)
                        }.let {
                            it.organisasjonsnummer() to it
                        }
                    })
                }
        }
    }
}