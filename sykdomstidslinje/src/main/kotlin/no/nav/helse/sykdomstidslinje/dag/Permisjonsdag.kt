package no.nav.helse.sykdomstidslinje.dag

import no.nav.helse.hendelse.Sykdomshendelse
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeVisitor
import java.time.LocalDate

class Permisjonsdag internal constructor(gjelder: LocalDate, hendelse: Sykdomshendelse): Dag(gjelder, hendelse) {
    override fun accept(visitor: SykdomstidslinjeVisitor) {
        visitor.visitPermisjonsdag(this)
    }

    override fun antallSykedagerHvorViIkkeTellerMedHelg() = 0

    override fun antallSykedagerHvorViTellerMedHelg() = 0

    override fun toString() = formatter.format(dagen) + "\tPermisjonsdag"

    override fun dagType() = JsonDagType.PERMISJONSDAG

    override fun nøkkel(): Nøkkel =
        when(hendelse.hendelsetype()){
            Sykdomshendelse.Type.SendtSøknadMottatt -> Nøkkel.Le_A
            else -> throw RuntimeException("Hendelse er ikke støttet")
        }
}
