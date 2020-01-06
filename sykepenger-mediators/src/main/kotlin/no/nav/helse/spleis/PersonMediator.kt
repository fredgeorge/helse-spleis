package no.nav.helse.spleis

import no.nav.helse.Topics
import no.nav.helse.behov.Behov
import no.nav.helse.hendelser.*
import no.nav.helse.person.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

internal class PersonMediator(
    private val personRepository: PersonRepository,
    private val lagrePersonDao: PersonObserver,
    private val utbetalingsreferanseRepository: UtbetalingsreferanseRepository,
    private val lagreUtbetalingDao: PersonObserver,
    private val vedtaksperiodeProbe: VedtaksperiodeProbe = VedtaksperiodeProbe,
    private val producer: KafkaProducer<String, String>
) : PersonObserver, HendelseListener {

    private val log = LoggerFactory.getLogger(PersonMediator::class.java)

    override fun onPåminnelse(påminnelse: Påminnelse) {
        person(påminnelse) { person -> person.håndter(påminnelse) }
    }

    override fun onYtelser(ytelser: Ytelser) {
        person(ytelser) { person -> person.håndter(ytelser) }
    }

    override fun onManuellSaksbehandling(manuellSaksbehandling: ManuellSaksbehandling) {
        person(manuellSaksbehandling) { person -> person.håndter(manuellSaksbehandling) }
    }

    override fun onVilkårsgrunnlag(vilkårsgrunnlag: Vilkårsgrunnlag) {
        person(vilkårsgrunnlag) { person -> person.håndter(vilkårsgrunnlag) }
    }

    override fun onInntektsmelding(inntektsmelding: Inntektsmelding) {
        person(inntektsmelding) { person -> person.håndter(inntektsmelding) }
    }

    override fun onNySøknad(søknad: NySøknad) {
        person(søknad) { person -> person.håndter(søknad) }
    }

    override fun onSendtSøknad(søknad: SendtSøknad) {
        person(søknad) { person -> person.håndter(søknad) }
    }

    fun hentSak(aktørId: String): Person? = personRepository.hentPerson(aktørId)

    fun hentSakForUtbetaling(utbetalingsreferanse: String): Person? {
        return utbetalingsreferanseRepository.hentUtbetaling(utbetalingsreferanse)?.let {
            personRepository.hentPerson(it.aktørId)
        }
    }

    override fun personEndret(personEndretEvent: PersonObserver.PersonEndretEvent) {}

    override fun vedtaksperiodeTrengerLøsning(event: Behov) {
        producer.send(event.producerRecord()).also {
            log.info("produserte behov=$event, recordMetadata=$it")
        }
    }

    override fun vedtaksperiodeEndret(event: VedtaksperiodeObserver.StateChangeEvent) {
        producer.send(event.producerRecord())
    }

    override fun vedtaksperiodeTilUtbetaling(event: VedtaksperiodeObserver.UtbetalingEvent) {
        producer.send(event.producerRecord())
    }

    override fun vedtaksperiodeIkkeFunnet(vedtaksperiodeEvent: PersonObserver.VedtaksperiodeIkkeFunnetEvent) {
        producer.send(vedtaksperiodeEvent.producerRecord())
    }

    private fun Behov.producerRecord() =
        ProducerRecord<String, String>(Topics.behovTopic, id().toString(), toJson())

    private fun person(arbeidstakerHendelse: ArbeidstakerHendelse) =
        (personRepository.hentPerson(arbeidstakerHendelse.aktørId()) ?: Person(
            aktørId = arbeidstakerHendelse.aktørId(),
            fødselsnummer = arbeidstakerHendelse.fødselsnummer()
        )).also {
            it.addObserver(this)
            it.addObserver(lagrePersonDao)
            it.addObserver(lagreUtbetalingDao)
            it.addObserver(vedtaksperiodeProbe)
        }

    private fun person(
        hendelse: ArbeidstakerHendelse,
        block: (Person) -> Unit
    ) {
        try {
            block(person(hendelse))
        } catch (err: UtenforOmfangException) {
            vedtaksperiodeProbe.utenforOmfang(hendelse)
        } catch (err: PersonskjemaForGammelt) {
            vedtaksperiodeProbe.forGammelSkjemaversjon(err)
        }
    }

}