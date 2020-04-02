package no.nav.helse.person

import no.nav.helse.e2e.TestPersonInspektør
import no.nav.helse.hendelser.*
import no.nav.helse.testhelpers.januar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.YearMonth
import java.util.*

internal class SimuleringHendelseTest {
    companion object {
        private const val UNG_PERSON_FNR_2018 = "12020052345"
        private const val orgnummer = "12345"
        private val førsteSykedag = 1.januar
        private val sisteSykedag = 31.januar
    }

    private lateinit var person: Person
    private val inspektør get() = TestPersonInspektør(person)
    private lateinit var hendelse: ArbeidstakerHendelse

    @BeforeEach
    internal fun opprettPerson() {
        person = Person("12345", UNG_PERSON_FNR_2018)
    }

    @Test
    fun `simulering er OK`() {
        håndterYtelser()
        person.håndter(simulering())
        assertTilstand(TilstandType.AVVENTER_GODKJENNING)
    }

    @Test
    fun `simulering er ikke OK`() {
        håndterYtelser()
        person.håndter(simulering(false))
        assertTilstand(TilstandType.AVVENTER_SIMULERING)
    }

    private fun assertTilstand(expectedTilstand: TilstandType) {
        assertEquals(
            expectedTilstand,
            inspektør.sisteTilstand(0)
        )
    }

    private fun håndterYtelser() {
        person.håndter(sykmelding())
        person.håndter(søknad())
        person.håndter(inntektsmelding())
        person.håndter(vilkårsgrunnlag())
        person.håndter(ytelser())
    }

    private fun ytelser(
        vedtaksperiodeId: UUID = inspektør.vedtaksperiodeId(0),
        utbetalinger: List<Utbetalingshistorikk.Periode> = emptyList(),
        foreldrepengeYtelse: Periode? = null,
        svangerskapYtelse: Periode? = null
    ) = Aktivitetslogg().let {
        Ytelser(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId = "aktørId",
            fødselsnummer = UNG_PERSON_FNR_2018,
            organisasjonsnummer = orgnummer,
            vedtaksperiodeId = vedtaksperiodeId.toString(),
            utbetalingshistorikk = Utbetalingshistorikk(
                utbetalinger = utbetalinger,
                inntektshistorikk = emptyList(),
                graderingsliste = emptyList(),
                maksDato = null,
                aktivitetslogg = it
            ),
            foreldrepermisjon = Foreldrepermisjon(
                foreldrepengeytelse = foreldrepengeYtelse,
                svangerskapsytelse = svangerskapYtelse,
                aktivitetslogg = it
            ),
            aktivitetslogg = it
        ).apply {
            hendelse = this
        }
    }
    private fun sykmelding() =
        Sykmelding(
            meldingsreferanseId = UUID.randomUUID(),
            fnr = UNG_PERSON_FNR_2018,
            aktørId = "aktørId",
            orgnummer = orgnummer,
            sykeperioder = listOf(Triple(førsteSykedag, sisteSykedag, 100))
        ).apply {
            hendelse = this
        }

    private fun søknad() =
        Søknad(
            meldingsreferanseId = UUID.randomUUID(),
            fnr = UNG_PERSON_FNR_2018,
            aktørId = "aktørId",
            orgnummer = orgnummer,
            perioder = listOf(Søknad.Periode.Sykdom(førsteSykedag,  sisteSykedag, 100, null)),
            harAndreInntektskilder = false,
            sendtTilNAV = sisteSykedag.atStartOfDay()
        ).apply {
            hendelse = this
        }

    private fun inntektsmelding() =
        Inntektsmelding(
            meldingsreferanseId = UUID.randomUUID(),
            refusjon = Inntektsmelding.Refusjon(null, 31000.0, emptyList()),
            orgnummer = orgnummer,
            fødselsnummer = UNG_PERSON_FNR_2018,
            aktørId = "aktørId",
            førsteFraværsdag = førsteSykedag,
            beregnetInntekt = 31000.0,
            arbeidsgiverperioder = listOf(Periode(førsteSykedag, førsteSykedag.plusDays(16))),
            ferieperioder = emptyList()
        ).apply {
            hendelse = this
        }

    private fun vilkårsgrunnlag() =
        Vilkårsgrunnlag(
            vedtaksperiodeId = inspektør.vedtaksperiodeId(0).toString(),
            aktørId = "aktørId",
            fødselsnummer = UNG_PERSON_FNR_2018,
            orgnummer = orgnummer,
            inntektsmåneder = (1..12).map {
                Vilkårsgrunnlag.Måned(
                    YearMonth.of(2018, it), listOf(31000.0)
                )
            },
            erEgenAnsatt = false,
            arbeidsforhold = listOf(Vilkårsgrunnlag.Arbeidsforhold(orgnummer, 1.januar(2017)))
        ).apply {
            hendelse = this
        }

    private fun simulering(simuleringOK: Boolean = true) =
        Simulering(
            vedtaksperiodeId = inspektør.vedtaksperiodeId(0).toString(),
            aktørId = "aktørId",
            fødselsnummer = UNG_PERSON_FNR_2018,
            orgnummer = orgnummer,
            simuleringOK = simuleringOK,
            melding = "",
            simuleringResultat = null
        ).apply {
            hendelse = this
        }
}