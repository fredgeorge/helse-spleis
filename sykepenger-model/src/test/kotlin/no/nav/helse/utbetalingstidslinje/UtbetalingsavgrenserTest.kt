package no.nav.helse.utbetalingstidslinje

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class UtbetalingsavgrenserTest {

    companion object {
        private const val UNG_PERSON_FNR_2018 = "12020052345"
        private const val PERSON_67_ÅR_FNR_2018 = "01015112345"
    }

    @Test
    fun `riktig antall dager`() {
        val tidslinje = tidslinjeOf(10.AP, 10.N)
        assertEquals(0, tidslinje.utbetalingsavgrenser(UNG_PERSON_FNR_2018).size)
    }

    @Test
    fun `stopper betaling etter 248 dager`() {
        val tidslinje = tidslinjeOf(249.N)
        assertEquals(1, tidslinje.utbetalingsavgrenser(UNG_PERSON_FNR_2018).size)
        assertEquals(listOf(6.september), tidslinje.utbetalingsavgrenser(UNG_PERSON_FNR_2018))
    }

    private fun tidslinjeOf(
        vararg dagPairs: Pair<Int, ArbeidsgiverUtbetalingstidslinje.(Double, LocalDate) -> Unit>
    ) = ArbeidsgiverUtbetalingstidslinje().apply {
        var startDato = LocalDate.of(2018, 1, 1)
        for ((antallDager, utbetalingsdag) in dagPairs) {
            val sluttDato = startDato.plusDays(antallDager.toLong())
            startDato.datesUntil(sluttDato).forEach {
                this.utbetalingsdag(1200.0, it)
            }
            startDato = sluttDato
        }
    }

    private fun ArbeidsgiverUtbetalingstidslinje.utbetalingsavgrenser(fnr: String) =
        Utbetalingsavgrenser(
            this,
            AlderRegler(fnr,
                LocalDate.of(2018,1,1),
                LocalDate.of(2019, 12, 31)
            )).ubetalteDager()
    private val Int.AP get() = Pair(this, ArbeidsgiverUtbetalingstidslinje::addArbeidsgiverperiodedag)
    private val Int.N get() = Pair(this, ArbeidsgiverUtbetalingstidslinje::addNAVdag)
    private val Int.A get() = Pair(this, ArbeidsgiverUtbetalingstidslinje::addArbeidsdag)
    private val Int.F get() = Pair(this, ArbeidsgiverUtbetalingstidslinje::addFridag)

    private val Int.januar get() = this.januar(2018)
    private fun Int.januar(år: Int ) = LocalDate.of(år, 1, this)
    private val Int.september get() = this.september(2018)
    private fun Int.september(år: Int ) = LocalDate.of(år, 9, this)
}
