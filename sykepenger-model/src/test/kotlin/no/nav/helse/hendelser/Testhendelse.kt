package no.nav.helse.hendelser

import no.nav.helse.sykdomstidslinje.ConcreteSykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.sykdomstidslinje.dag.Dag
import java.time.LocalDateTime
import java.util.UUID

internal class Testhendelse(
    private val rapportertdato: LocalDateTime = LocalDateTime.of(2019, 9, 16, 10, 45),
    private val hendelsetype: Dag.NøkkelHendelseType = Dag.NøkkelHendelseType.Søknad
) :
    SykdomstidslinjeHendelse(UUID.randomUUID(), Hendelsestype.SendtSøknad) {
    override fun nøkkelHendelseType(): Dag.NøkkelHendelseType = hendelsetype

    override fun sykdomstidslinje(): ConcreteSykdomstidslinje {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun aktørId(): String {
        TODO("not implemented")
    }

    override fun fødselsnummer(): String {
        TODO("not implemented")
    }

    override fun organisasjonsnummer(): String {
        TODO("not implemented")
    }

    override fun rapportertdato(): LocalDateTime {
        return rapportertdato
    }

    override fun toJson(): String {
        TODO("not implemented")
    }

    override fun equals(other: Any?): Boolean = this === other
}
