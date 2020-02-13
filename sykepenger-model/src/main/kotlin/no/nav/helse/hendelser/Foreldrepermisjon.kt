package no.nav.helse.hendelser

import no.nav.helse.person.Aktivitetslogger

class Foreldrepermisjon(
    private val foreldrepengeytelse: Periode?,
    private val svangerskapsytelse: Periode?,
    private val aktivitetslogger: Aktivitetslogger
) {

    internal fun overlapper(sykdomsperiode: Periode): Boolean {
        if (foreldrepengeytelse == null && svangerskapsytelse == null) {
            aktivitetslogger.info("Bruker har ingen foreldrepenge- eller svangerskapsytelser")
            return false
        }
        return listOfNotNull(foreldrepengeytelse, svangerskapsytelse)
                .any { ytelse -> ytelse.overlapperMed(sykdomsperiode) }
    }

    internal fun kopierAktiviteterTil(aktivitetslogger: Aktivitetslogger) {
        aktivitetslogger.addAll(this.aktivitetslogger, "Foreldrepengeytelser")
    }

}