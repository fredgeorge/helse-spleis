package no.nav.helse.serde.api.dto

import no.nav.helse.serde.api.SykdomstidslinjedagDTO
import no.nav.helse.serde.api.UtbetalingstidslinjedagDTO

data class Sykdomshistorikk2(
    var hendelsetidslinje: MutableList<SykdomstidslinjedagDTO> = mutableListOf(),
    var beregnettidslinje: MutableList<SykdomstidslinjedagDTO> = mutableListOf(),
    var utbetalinger: MutableList<UtbetalingDTO> = mutableListOf()
) {
    data class UtbetalingDTO(
        val utbetalingstidslinje: List<UtbetalingstidslinjedagDTO>
    )
}
