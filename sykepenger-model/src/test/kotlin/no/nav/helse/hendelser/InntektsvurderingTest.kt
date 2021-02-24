package no.nav.helse.hendelser

import no.nav.helse.hendelser.Inntektsvurdering.Inntektsgrunnlag.SAMMENLIGNINGSGRUNNLAG
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.Periodetype
import no.nav.helse.testhelpers.desember
import no.nav.helse.testhelpers.inntektperioder
import no.nav.helse.testhelpers.januar
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

internal class InntektsvurderingTest {
    private companion object {
        private const val ORGNR = "123456789"
        private val INNTEKT = 1000.0.månedlig
    }

    private lateinit var aktivitetslogg: Aktivitetslogg

    @Test
    fun `ugyldige verdier`() {
        assertTrue(hasErrors(inntektsvurdering(emptyList()), INGEN, INGEN))
        assertTrue(
            hasErrors(
                inntektsvurdering(
                    inntektperioder {
                        inntektsgrunnlag = SAMMENLIGNINGSGRUNNLAG
                        LocalDate.now() til LocalDate.now() inntekter {
                            ORGNR inntekt INGEN
                        }
                    }
                ), INGEN, INGEN
            )
        )
    }

    @Test
    fun `skal kunne beregne avvik mellom innmeldt lønn fra inntektsmelding og lønn fra inntektskomponenten`() {
        val inntektsvurdering = inntektsvurdering()
        assertTrue(hasErrors(inntektsvurdering, 1250.01.månedlig, INNTEKT))
        assertTrue(hasErrors(inntektsvurdering, 749.99.månedlig, INNTEKT))
        assertFalse(hasErrors(inntektsvurdering, 1000.00.månedlig, INNTEKT))
        assertFalse(hasErrors(inntektsvurdering, 1250.00.månedlig, INNTEKT))
        assertFalse(hasErrors(inntektsvurdering, 750.00.månedlig, INNTEKT))
    }

    @Test
    fun `flere organisasjoner siste 3 måneder gir warning`() {
        val annenInntekt = "etAnnetOrgnr" to INNTEKT
        assertFalse(hasErrors(inntektsvurdering(inntekter(YearMonth.of(2017, 12), annenInntekt)), INNTEKT, INNTEKT))
        assertTrue(aktivitetslogg.hasWarningsOrWorse())
        assertFalse(hasErrors(inntektsvurdering(inntekter(YearMonth.of(2017, 11), annenInntekt)), INNTEKT, INNTEKT))
        assertTrue(aktivitetslogg.hasWarningsOrWorse())
        assertFalse(hasErrors(inntektsvurdering(inntekter(YearMonth.of(2017, 10), annenInntekt)), INNTEKT, INNTEKT))
        assertTrue(aktivitetslogg.hasWarningsOrWorse())
        assertFalse(hasErrors(inntektsvurdering(inntekter(YearMonth.of(2017, 9), annenInntekt)), INNTEKT, INNTEKT))
        assertFalse(aktivitetslogg.hasWarningsOrWorse())
    }

    private fun inntekter(periode: YearMonth, inntekt: Pair<String, Inntekt>) =
        inntektperioder {
            1.januar(2017) til 1.desember(2017) inntekter {
                ORGNR inntekt INNTEKT
            }
            periode.atDay(1) til periode.atDay(1) inntekter {
                inntekt.first inntekt inntekt.second
            }
        }

    private fun hasErrors(inntektsvurdering: Inntektsvurdering, grunnlagForSykepengegrunnlag: Inntekt, sammenligningsgrunnlag: Inntekt): Boolean {
        aktivitetslogg = Aktivitetslogg()
        return inntektsvurdering.valider(
            aktivitetslogg,
            grunnlagForSykepengegrunnlag,
            sammenligningsgrunnlag,
            Periodetype.FØRSTEGANGSBEHANDLING
        ).hasErrorsOrWorse()
    }

    private fun inntektsvurdering(
        inntektsmåneder: List<Inntektsvurdering.ArbeidsgiverInntekt> = inntektperioder {
            1.januar(2017) til 1.desember(2017) inntekter {
                ORGNR inntekt INNTEKT
            }
        }
    ) = Inntektsvurdering(inntektsmåneder)
}
