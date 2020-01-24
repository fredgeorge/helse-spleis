package no.nav.helse.spleis.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.helse.person.Person
import no.nav.helse.person.PersonObserver
import no.nav.helse.spleis.PostgresProbe
import javax.sql.DataSource

class LagrePersonDao(private val dataSource: DataSource,
                     private val probe: PostgresProbe = PostgresProbe
): PersonObserver {

    override fun personEndret(personEndretEvent: PersonObserver.PersonEndretEvent) {
        lagrePerson(personEndretEvent.aktørId, personEndretEvent.fødselsnummer, personEndretEvent.memento)
    }

    private fun lagrePerson(aktørId: String, fødselsnummer: String, memento: Person.Memento) {
        using(sessionOf(dataSource)) { session ->
            session.run(queryOf("INSERT INTO person (aktor_id, fnr, data) VALUES (?, ?, (to_json(?::json)))",
                aktørId, fødselsnummer, memento.state()).asExecute)
        }.also {
            PostgresProbe.personSkrevetTilDb()
        }
    }

}