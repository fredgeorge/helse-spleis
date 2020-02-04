package no.nav.helse.hendelser

import no.nav.helse.person.Aktivitetslogger
import no.nav.helse.person.ArbeidstakerHendelse
import no.nav.helse.person.ArbeidstakerHendelse.Hendelsestype.Ytelser
import no.nav.helse.person.IAktivitetslogger
import no.nav.helse.person.PersonVisitor
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.time.LocalDateTime
import java.util.UUID

internal class ValidationTest {

    private lateinit var aktivitetslogger: Aktivitetslogger

    @BeforeEach
    internal fun setup() {
        aktivitetslogger = Aktivitetslogger()
    }

    @Test
    internal fun success() {
        var success = false
        Validation(TestHendelse(aktivitetslogger)).also {
            it.onError { fail("Uventet kall") }
            it.onSuccess { success = true }
        }
        assertTrue(success)
    }

    @Test
    internal fun failure() {
        var failure = false
        Validation(TestHendelse(aktivitetslogger)).also {
            it.onError { failure = true }
            it.valider { FailureBlock() }
            it.onSuccess { fail("Uventet kall") }
        }
        assertTrue(failure)
    }

    @Test internal fun `when does constructor run`() {
        var variableChanged = false
        Validation(TestHendelse(aktivitetslogger)).also {
            it.onError { fail("we failed") }
            it.valider { ReturnValues().also { variableChanged = it.variable()}}
        }
        assertTrue(variableChanged)
    }

    private class ReturnValues : Valideringssteg {
        private var variable = false
        init {
            variable = true
        }
        override fun isValid() = true
        internal fun variable() = variable
        override fun feilmelding(): String {
            fail("Uventet kall")
        }
    }

    private inner class FailureBlock : Valideringssteg {
        override fun isValid() = false
        override fun feilmelding() = "feilmelding"
    }

    private inner class TestHendelse(aktivitetslogger: Aktivitetslogger) :
        ArbeidstakerHendelse(UUID.randomUUID(), Ytelser, aktivitetslogger),
        IAktivitetslogger by aktivitetslogger {
        override fun rapportertdato(): LocalDateTime {
            fail("Uventet kall")
        }

        override fun aktørId(): String {
            fail("Uventet kall")
        }

        override fun fødselsnummer(): String {
            fail("Uventet kall")
        }

        override fun organisasjonsnummer(): String {
            fail("Uventet kall")
        }

        override fun accept(visitor: PersonVisitor) {
            fail("Uventet kall")
        }
    }
}