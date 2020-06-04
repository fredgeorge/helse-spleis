package no.nav.helse.serde.reflection

import no.nav.helse.serde.UtbetalingstidslinjeData.TypeData
import no.nav.helse.serde.reflection.ReflectInstance.Companion.get
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Økonomi
import java.time.LocalDate

internal class UtbetalingsdagReflect(
    utbetalingsdag: Utbetalingstidslinje.Utbetalingsdag,
    private val type: TypeData
) {
    private val dato: LocalDate = utbetalingsdag["dato"]
    private val økonomi: Økonomi = utbetalingsdag["økonomi"]

    internal fun toMap() = mutableMapOf<String, Any?>(
        "type" to type,
        "dato" to dato
    ).also { it.putAll(økonomi.toMap()) }
}

internal class UtbetalingsdagMedGradReflect(
    utbetalingsdag: Utbetalingstidslinje.Utbetalingsdag,
    private val type: TypeData
) {
    private val dato: LocalDate = utbetalingsdag["dato"]
    private val økonomi: Økonomi = utbetalingsdag["økonomi"]

    internal fun toMap() = mutableMapOf<String, Any?>(
        "type" to type,
        "dato" to dato
    ).also { it.putAll(økonomi.toMap()) }
}


internal class NavDagReflect(
    utbetalingsdag: Utbetalingstidslinje.Utbetalingsdag,
    private val type: TypeData
) {
    private val dato: LocalDate = utbetalingsdag["dato"]
    private val økonomi: Økonomi = utbetalingsdag["økonomi"]

    internal fun toMap() = mutableMapOf<String, Any?>(
        "type" to type,
        "dato" to dato
    ).also { it.putAll(økonomi.toMap()) }
}

internal class AvvistdagReflect(avvistdag: Utbetalingstidslinje.Utbetalingsdag.AvvistDag) {
    private val dato: LocalDate = avvistdag["dato"]
    private val økonomi: Økonomi = avvistdag["økonomi"]
    private val begrunnelse: Begrunnelse = avvistdag["begrunnelse"]

    internal fun toMap() = mutableMapOf<String, Any?>(
        "type" to TypeData.AvvistDag,
        "dato" to dato,
        "begrunnelse" to begrunnelse.name
    ).also { it.putAll(økonomi.toMap()) }
}
