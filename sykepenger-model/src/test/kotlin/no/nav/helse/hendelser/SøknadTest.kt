package no.nav.helse.hendelser

import no.nav.helse.hendelser.Søknad.Søknadsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.*
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.sykdomstidslinje.NyDag.*
import no.nav.helse.sykdomstidslinje.dag.Arbeidsdag
import no.nav.helse.sykdomstidslinje.dag.FriskHelgedag
import no.nav.helse.sykdomstidslinje.dag.SykHelgedag
import no.nav.helse.sykdomstidslinje.dag.Sykedag
import no.nav.helse.testhelpers.desember
import no.nav.helse.testhelpers.januar
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.*

internal class SøknadTest {

    private companion object {
        private const val UNG_PERSON_FNR_2018 = "12020052345"
        private val EN_PERIODE = Periode(1.januar, 31.januar)
    }

    private lateinit var søknad: Søknad
    private lateinit var aktivitetslogg: Aktivitetslogg

    @BeforeEach
    internal fun setup() {
        aktivitetslogg = Aktivitetslogg()
    }

    @Test
    internal fun `søknad med bare sykdom`() {
        søknad(Sykdom(1.januar,  10.januar, 100))
        assertFalse(søknad.valider(EN_PERIODE).hasErrors())
        assertEquals(10, søknad.sykdomstidslinje().length())

        assertEquals(10, søknad.nySykdomstidslinje().count())
    }

    @Test
    internal fun `søknad med ferie`() {
        søknad(Sykdom(1.januar,  10.januar, 100), Ferie(2.januar, 4.januar))
        assertFalse(søknad.valider(EN_PERIODE).hasErrors())
        assertEquals(10, søknad.sykdomstidslinje().length())

        assertEquals(10, søknad.nySykdomstidslinje().count())
    }

    @Test
    internal fun `søknad med utdanning`() {
        søknad(Sykdom(1.januar,  10.januar, 100), Utdanning(5.januar, 10.januar))
        assertTrue(søknad.valider(EN_PERIODE).hasErrors())
        assertEquals(10, søknad.sykdomstidslinje().length())

        assertEquals(10, søknad.nySykdomstidslinje().count())
    }

    @Test
    internal fun `søknad med papirsykmelding`() {
        søknad(Sykdom(1.januar,  10.januar, 100), Papirsykmelding(11.januar, 16.januar))
        assertTrue(søknad.valider(EN_PERIODE).hasErrors())
        assertEquals(16, søknad.sykdomstidslinje().length())

        assertEquals(16, søknad.nySykdomstidslinje().count())
        assertEquals(6, søknad.nySykdomstidslinje().filterIsInstance<ProblemDag>().size)
    }

    @Test
    internal fun `sykdomsgrad under 100 støttes`() {
        søknad(Sykdom(1.januar, 10.januar, 50))
        assertFalse(søknad.valider(EN_PERIODE).hasErrors())
    }

    @Test
    internal fun `sykdom faktiskgrad under 100 støttes`() {
        søknad(Sykdom(1.januar, 10.januar, 100, 50))
        assertFalse(søknad.valider(EN_PERIODE).hasErrors())
    }

    @Test
    internal fun `ferie ligger utenfor sykdomsvindu`() {
        søknad(Sykdom(1.januar, 10.januar, 100), Ferie(2.januar, 16.januar))
        assertTrue(søknad.valider(EN_PERIODE).hasErrors())
    }

    @Test
    internal fun `utdanning ligger utenfor sykdomsvindu`() {
        søknad(Sykdom(1.januar, 10.januar, 100), Utdanning(16.januar, 17.januar))
        assertTrue(søknad.valider(EN_PERIODE).hasErrors())
    }

    @Test
    internal fun `permisjon ligger utenfor sykdomsvindu`() {
        søknad(Sykdom(1.januar, 10.januar, 100), Permisjon(2.januar, 16.januar))
        assertTrue(søknad.valider(EN_PERIODE).hasErrors())
    }

    @Test
    internal fun `arbeidag ligger utenfor sykdomsvindu`() {
        søknad(Sykdom(1.januar, 10.januar, 100), Arbeid(2.januar, 16.januar))
        assertTrue(søknad.valider(EN_PERIODE).hasErrors())
    }

    @Test
    internal fun `egenmelding ligger utenfor sykdomsvindu`() {
        søknad(Sykdom(5.januar, 12.januar, 100), Egenmelding(2.januar, 3.januar))
        assertFalse(søknad.valider(EN_PERIODE).hasErrors())
        assertEquals(11, søknad.sykdomstidslinje().length())

        assertEquals(11, søknad.nySykdomstidslinje().count())
        assertEquals(6, søknad.nySykdomstidslinje().filterIsInstance<NySykedag>().size)
        assertEquals(2, søknad.nySykdomstidslinje().filterIsInstance<NySykHelgedag>().size)
        assertEquals(2, søknad.nySykdomstidslinje().filterIsInstance<NyArbeidsgiverdag>().size)
    }

    @Test
    internal fun `egenmelding ligger langt utenfor sykdomsvindu`() {
        søknad(Sykdom(5.januar,  12.januar, 100), Egenmelding(19.desember(2017), 20.desember(2017)))
        assertFalse(søknad.valider(EN_PERIODE).hasErrors()) { aktivitetslogg.toString() }
        assertEquals(8, søknad.sykdomstidslinje().length())

        assertEquals(8, søknad.nySykdomstidslinje().count())
    }

    @Test
    internal fun `søknad med andre inntektskilder`() {
        søknad(Sykdom(5.januar, 12.januar, 100), harAndreInntektskilder = true)
        assertTrue(søknad.valider(EN_PERIODE).hasErrors())
    }

    @Test
    internal fun `søknad uten andre inntektskilder`() {
        søknad(Sykdom(5.januar, 12.januar, 100), harAndreInntektskilder = false)
        assertFalse(søknad.valider(EN_PERIODE).hasErrors())
    }

    @Test
    internal fun `må ha perioder`() {
        assertThrows<Aktivitetslogg.AktivitetException> { søknad() }
    }

    @Test
    internal fun `må ha sykdomsperioder`() {
        assertThrows<Aktivitetslogg.AktivitetException> { søknad(Ferie(2.januar, 16.januar)) }
    }

    @Test
    internal fun `angitt arbeidsgrad kan ikke føre til sykegrad høyere enn graden fra sykmelding`() {
        søknad(Sykdom(1.januar, 31.januar,  20, 21))
        søknad.valider(EN_PERIODE)
        assertTrue(søknad.hasErrors())
    }

    @Test
    internal fun `angitt arbeidsgrad kan føre til lavere sykegrad enn graden fra sykmelding`() {
        søknad(Sykdom(1.januar, 31.januar,  20, 19))
        søknad.valider(EN_PERIODE)
        assertFalse(søknad.hasErrors())
    }

    @Test
    internal fun `angitt arbeidsgrad kan føre til lik sykegrad som graden fra sykmelding`() {
        søknad(Sykdom(1.januar, 31.januar,  20, 20))
        søknad.valider(EN_PERIODE)
        assertFalse(søknad.hasErrors())
    }

    @Test
    internal fun `søknad uten permittering får ikke warning`() {
        søknad(Sykdom(1.januar, 31.januar,  20, 20))
        søknad.valider(EN_PERIODE)
        assertFalse(søknad.hasWarnings())
    }

    @Test
    internal fun `søknad med permittering får warning`() {
        søknad(Sykdom(1.januar, 31.januar,  20, 20), permittert = true)
        søknad.valider(EN_PERIODE)
        assertTrue(søknad.hasWarnings())
    }

    @Test
    internal fun `søknadsturnering for nye dagtyper`() {
        søknad(Arbeid(15.januar, 31.januar), Sykdom(1.januar, 31.januar,  100))

        assertTrue(søknad.sykdomstidslinje()[1.januar] is Sykedag.Søknad)
        assertTrue(søknad.sykdomstidslinje()[14.januar] is SykHelgedag.Søknad)
        assertTrue(søknad.sykdomstidslinje()[15.januar] is Arbeidsdag.Søknad)
        assertTrue(søknad.sykdomstidslinje()[21.januar] is FriskHelgedag.Søknad)
        assertTrue(søknad.sykdomstidslinje()[31.januar] is Arbeidsdag.Søknad)

        assertEquals(10, søknad.nySykdomstidslinje().filterIsInstance<NySykedag>().size)
        assertEquals(4, søknad.nySykdomstidslinje().filterIsInstance<NySykHelgedag>().size)
        assertEquals(13, søknad.nySykdomstidslinje().filterIsInstance<NyArbeidsdag>().size)
        assertEquals(4, søknad.nySykdomstidslinje().filterIsInstance<NyFriskHelgedag>().size)
    }

    @Test
    fun `turnering mellom arbeidsgiverdager og sykedager`() {
        søknad(Sykdom(1.januar, 31.januar,  100), Egenmelding(15.januar, 31.januar))

        assertEquals(23, søknad.nySykdomstidslinje().filterIsInstance<NySykedag>().size)
        assertEquals(8, søknad.nySykdomstidslinje().filterIsInstance<NySykHelgedag>().size)
    }

    private fun søknad(vararg perioder: Søknadsperiode, harAndreInntektskilder: Boolean = false, permittert: Boolean = false) {
        søknad = Søknad(
            meldingsreferanseId = UUID.randomUUID(),
            fnr = UNG_PERSON_FNR_2018,
            aktørId = "12345",
            orgnummer = "987654321",
            perioder = listOf(*perioder),
            harAndreInntektskilder = harAndreInntektskilder,
            sendtTilNAV = Søknadsperiode.søknadsperiode(perioder.toList())?.endInclusive?.atStartOfDay() ?: LocalDateTime.now(),
            permittert = permittert
        )
    }
}
