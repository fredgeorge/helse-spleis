package no.nav.helse.økonomi

import no.nav.helse.testhelpers.januar
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ØkonomiTest {

    @Test fun `kan ikke sette dagsats mer enn en gang`() {
        assertThrows<IllegalStateException>{ 25.prosent.sykdomsgrad.inntekt(1200).inntekt(800) }
    }

    @Test fun singelsykegrad() {
        assertEquals(
            75.prosent,
            listOf(
                75.prosent.sykdomsgrad.inntekt(1200)
            ).sykdomsgrad()
        )
    }

    @Test fun `to arbeidsgivere`() {
        assertEquals(
            38.prosent,
            listOf(
                50.prosent.sykdomsgrad.inntekt(1200),
                20.prosent.sykdomsgrad.inntekt(800)
            ).sykdomsgrad().also {
                assertFalse(it.erUnderGrensen())
            }
        )
    }

    @Test fun `flere arbeidsgivere`() {
        assertEquals(
            49.prosent,
            listOf(
                50.prosent.sykdomsgrad.inntekt(1200),
                20.prosent.sykdomsgrad.inntekt(800),
                60.prosent.sykdomsgrad.inntekt(2000)
            ).sykdomsgrad()
        )
    }

    @Test fun `låst økonomi fungerer som arbeidsdag`() {
        assertEquals(
            19.prosent,
            listOf(
                50.prosent.sykdomsgrad.inntekt(1200),
                20.prosent.sykdomsgrad.inntekt(800),
                60.prosent.sykdomsgrad.inntekt(2000).lås()
            ).sykdomsgrad().also {
                assertTrue(it.erUnderGrensen())
            }
        )
    }

    @Test fun `kan låse igjen hvis allerede låst`() {
        assertDoesNotThrow { 50.prosent.sykdomsgrad.inntekt(1200).lås().lås()}
    }

    @Test fun `kan ikke låse uten dekningsgrunnlag`() {
        assertThrows<IllegalStateException> { 50.prosent.sykdomsgrad.lås() }
    }

    @Test fun `kan ikke låses etter betaling`() {
        50.prosent.sykdomsgrad.inntekt(1200).also { økonomi ->
            listOf(økonomi).betal(1.januar)
            assertUtbetaling(økonomi, 600, 0)
            assertThrows<IllegalStateException> { økonomi.lås() }
        }
    }

    @Test fun `opplåsing tillater betaling`() {
        50.prosent.sykdomsgrad.inntekt(1200).lås().låsOpp().also { økonomi ->
            listOf(økonomi).betal(1.januar)
            assertUtbetaling(økonomi, 600, 0)
        }
    }

    @Test fun `kan ikke låses opp med mindre den er låst`() {
        assertThrows<IllegalStateException> { 50.prosent.sykdomsgrad.låsOpp() }
        50.prosent.sykdomsgrad.inntekt(1200).also { økonomi ->
            assertThrows<IllegalStateException> { økonomi.låsOpp() }
            listOf(økonomi).betal(1.januar)
            assertUtbetaling(økonomi, 600, 0)
            assertThrows<IllegalStateException> { økonomi.låsOpp() }
        }
    }

    @Test fun `dekningsgrunnlag returns clone`() {
        50.prosent.sykdomsgrad.also { original ->
            assertNotSame(original, original.inntekt(1200))
        }
    }

    @Test fun `betal 0 hvis låst`() {
        50.prosent.sykdomsgrad.inntekt(1200).lås().also { økonomi ->
            listOf(økonomi).betal(1.januar)
            assertUtbetaling(økonomi, 0, 0)
            økonomi.låsOpp()
            listOf(økonomi).betal(1.januar)
            assertUtbetaling(økonomi, 600, 0)
        }
    }

    @Test fun `kan ikke låses etter utbetaling`() {
        50.prosent.sykdomsgrad.inntekt(1200).also { økonomi ->
            listOf(økonomi).betal(1.januar)
            assertThrows<IllegalStateException> { økonomi.lås() }
        }
    }

    @Test fun `toMap uten dekningsgrunnlag`() {
        79.5.prosent.sykdomsgrad.toMap().apply {
            assertEquals(79.5, this["grad"])
            assertEquals(100.0, this["arbeidsgiverBetalingProsent"])
            assertNull(this["dekningsgrunnlag"])
        }
    }

    @Test fun `toMap med dekningsgrunnlag`() {
        79.5.prosent.sykdomsgrad.inntekt(1200.4).toMap().apply {
            assertEquals(79.5, this["grad"])
            assertEquals(100.0, this["arbeidsgiverBetalingProsent"])
            assertEquals(1200.4, this["dekningsgrunnlag"])
        }
    }

    @Test fun `toIntMap med dekningsgrunnlag`() {
        79.5.prosent.sykdomsgrad.inntekt(1200.4).toIntMap().apply {
            assertEquals(80, this["grad"])
            assertEquals(100, this["arbeidsgiverBetalingProsent"])
            assertEquals(1200, this["dekningsgrunnlag"])
        }
    }

    @Test fun `kan beregne betaling bare en gang`() {
        assertThrows<IllegalStateException> {
            listOf(80.prosent.sykdomsgrad.inntekt(1200)).betal(1.januar).betal(1.januar)
        }
    }

    @Test fun `Beregn utbetaling når mindre enn 6G`() {
        80.prosent.sykdomsgrad.inntekt(1200).also {
            listOf(it).betal(1.januar)
            it.toMap().apply {
                assertEquals(80.0, this["grad"])
                assertEquals(100.0, this["arbeidsgiverBetalingProsent"])
                assertEquals(1200.0, this["dekningsgrunnlag"])
                assertEquals(960, this["arbeidsgiverbeløp"])
                assertEquals(0, this["personbeløp"])
            }
            it.toIntMap().apply {
                assertEquals(80, this["grad"])
                assertEquals(100, this["arbeidsgiverBetalingProsent"])
                assertEquals(1200, this["dekningsgrunnlag"])
                assertEquals(960, this["arbeidsgiverbeløp"])
                assertEquals(0, this["personbeløp"])
            }
        }
    }

    @Test fun `arbeidsgiver og person splittes tilsvarer totalt`() {
        Økonomi.sykdomsgrad(100.prosent, 50.prosent).inntekt(999).also {
            listOf(it).betal(1.januar)
            it.toMap().apply {
                assertEquals(100.0, this["grad"])
                assertEquals(50.0, this["arbeidsgiverBetalingProsent"])
                assertEquals(999.0, this["dekningsgrunnlag"])
                assertEquals(500, this["arbeidsgiverbeløp"])
                assertEquals(499, this["personbeløp"])
            }
        }
    }

    @Test fun `tre arbeidsgivere uten grenser`() {
        val a =  Økonomi.sykdomsgrad(50.prosent, 50.prosent).inntekt(600)
        val b =  Økonomi.sykdomsgrad(20.prosent, 100.prosent).inntekt(400)
        val c =  Økonomi.sykdomsgrad(60.prosent, 0.prosent).inntekt(1000)
        listOf(a, b, c).betal(1.januar).also {
            assertEquals(49.prosent, it.sykdomsgrad())
        }
        listOf(a, b, c).forEach {
            assertFalse(it.er6GBegrenset())
        }
        assertUtbetaling(a, 150, 150)
        assertUtbetaling(b, 80, 0)
        assertUtbetaling(c, 0, 600)
    }

    @Test fun `tre arbeidsgivere med persongrense`() {
        val a =  Økonomi.sykdomsgrad(50.prosent, 50.prosent).inntekt(1200)
        val b =  Økonomi.sykdomsgrad(20.prosent, 100.prosent).inntekt(800)
        val c =  Økonomi.sykdomsgrad(60.prosent, 0.prosent).inntekt(2000)
        listOf(a, b, c).betal(1.januar).also {
            assertEquals(49.prosent, it.sykdomsgrad())
            // grense = 1059
        }
        listOf(a, b, c).forEach {
            assertTrue(it.er6GBegrenset())
        }
        assertUtbetaling(a, 300, 120)
        assertUtbetaling(b, 160, 0)
        assertUtbetaling(c, 0, 479)
    }

    @Test fun `tre arbeidsgivere med arbeidsgivere`() {
        val a =  Økonomi.sykdomsgrad(50.prosent, 50.prosent).inntekt(4800)
        val b =  Økonomi.sykdomsgrad(20.prosent, 100.prosent).inntekt(3200)
        val c =  Økonomi.sykdomsgrad(60.prosent, 0.prosent).inntekt(8000)
        listOf(a, b, c).betal(1.januar).also {
            assertEquals(49.prosent, it.sykdomsgrad())
            // grense = 1059
        }
        listOf(a, b, c).forEach {
            assertTrue(it.er6GBegrenset())
        }
        assertUtbetaling(a, 691, 0)  // (1059 / (1200 + 640)) * 1200
        assertUtbetaling(b, 368, 0)
        assertUtbetaling(c, 0, 0)
    }

    @Disabled
    @Test fun `tre arbeidsgivere med arbeidsgivere2`() {  // Alternate algorithm
        val a =  Økonomi.sykdomsgrad(50.prosent, 100.prosent).inntekt(969.23)
        val b =  Økonomi.sykdomsgrad(80.prosent, 90.prosent).inntekt(461.53)
        val c =  Økonomi.sykdomsgrad(20.prosent, 25.prosent).inntekt(1430.76)
        listOf(a, b, c).betal(1.januar(2018)).also {
            assertEquals(40.prosent, it.sykdomsgrad())
        }
        assertUtbetaling(a, 464, 0)
        assertUtbetaling(b, 325, 0)
        assertUtbetaling(c, 72, 0)
    }

    private fun assertUtbetaling(økonomi: Økonomi, expectedArbeidsgiver: Int, expectedPerson: Int) {
        økonomi.toMap().also { map ->
            assertEquals(expectedArbeidsgiver, map["arbeidsgiverbeløp"], "arbeidsgiverbeløp problem")
            assertEquals(expectedPerson, map["personbeløp"], "personbeløp problem")
        }
    }
}

internal val Prosentdel.sykdomsgrad get() = Økonomi.sykdomsgrad(this, 100.prosent)
