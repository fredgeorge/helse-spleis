package no.nav.helse.utbetalingstidslinje

import no.nav.helse.hendelser.til
import no.nav.helse.sykdomstidslinje.erRettFør
import java.time.LocalDate

/**
 *  Forstår opprettelsen av en Utbetalingstidslinje
 */
internal class Arbeidsgiverperiode(førsteDag: LocalDate): Iterable<LocalDate> {
    private val perioder = mutableListOf(
        førsteDag til førsteDag
    )
    private val forrige get() = perioder.last()

    internal fun nyDag(dato: LocalDate) {
        if (forrige.start.erRettFør(dato)) return utvidSiste(dato)
        perioder.add(dato til dato)
    }

    internal fun utvidSiste(dato: LocalDate) {
        perioder[perioder.size - 1] = forrige.oppdaterTom(dato)
    }

    override fun iterator(): Iterator<LocalDate> {
        return object : Iterator<LocalDate> {
            private val periodeIterators = perioder.map { it.iterator() }.iterator()
            private var current: Iterator<LocalDate>? = null

            override fun hasNext(): Boolean {
                val iterator = current
                if (iterator != null && iterator.hasNext()) return true
                if (!periodeIterators.hasNext()) return false
                current = periodeIterators.next()
                return true
            }

            override fun next(): LocalDate {
                return current?.next() ?: throw NoSuchElementException()
            }
        }
    }
}
