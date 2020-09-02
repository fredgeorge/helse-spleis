package no.nav.helse.person

import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Inntektsvurdering
import no.nav.helse.hendelser.Utbetalingshistorikk
import no.nav.helse.hendelser.til
import no.nav.helse.person.InntektshistorikkVol2.Inntektsopplysning
import no.nav.helse.testhelpers.*
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.*

@ExperimentalStdlibApi
internal class InntektshistorikkVol2Test {

    private lateinit var historikk: InntektshistorikkVol2
    private val inspektør get() = Inntektsinspektør(historikk)

    private companion object {
        const val UNG_PERSON_FNR_2018 = "12020052345"
        const val AKTØRID = "42"
        const val ORGNUMMER = "987654321"
        val INNTEKT = 31000.00.månedlig
    }

    @BeforeEach
    fun setup() {
        historikk = InntektshistorikkVol2()
    }

    @Test
    fun `Inntekt fra inntektsmelding blir lagt til i inntektshistorikk`() {
        inntektsmelding().addInntekt(historikk)
        assertEquals(1, inspektør.inntektTeller.size)
        assertEquals(1, inspektør.inntektTeller.first())
    }

    @Test
    fun `Inntekt fra inntektsmelding brukes til å beregne sykepengegrunnlaget`() {
        inntektsmelding(førsteFraværsdag = 1.januar).addInntekt(historikk)
        assertEquals(1, inspektør.inntektTeller.size)
        assertEquals(1, inspektør.inntektTeller.first())
        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(31.desember(2017)))
    }

    @Test
    fun `Inntekt fra inntektsmelding brukes ikke til å beregne sykepengegrunnlaget på annen dato`() {
        inntektsmelding(førsteFraværsdag = 1.januar).addInntekt(historikk)
        assertEquals(1, inspektør.inntektTeller.size)
        assertEquals(1, inspektør.inntektTeller.first())
        assertEquals(INGEN, historikk.grunnlagForSykepengegrunnlag(1.januar))
    }

    @Test
    fun `Inntekt fra infotrygd brukes til å beregne sykepengegrunnlaget`() {
        utbetalingshistorikk(
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(1.januar, INNTEKT, ORGNUMMER, true)
            )
        ).addInntekter(UUID.randomUUID(), ORGNUMMER, historikk)
        assertEquals(1, inspektør.inntektTeller.size)
        assertEquals(1, inspektør.inntektTeller.first())
        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(31.desember(2017)))
    }

    @Test
    fun `Bruker inntekt fra inntektsmelding fremfor inntekt fra infotrygd for å beregne sykepengegrunnlaget`() {
        inntektsmelding(beregnetInntekt = 20000.månedlig).addInntekt(historikk)
        utbetalingshistorikk(
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(1.januar, 25000.månedlig, ORGNUMMER, true)
            )
        ).addInntekter(UUID.randomUUID(), ORGNUMMER, historikk)
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(2, inspektør.inntektTeller.first())
        assertEquals(1, inspektør.inntektTeller.last())
        assertEquals(20000.månedlig, historikk.grunnlagForSykepengegrunnlag(31.desember(2017)))
    }

    @Test
    fun `Bruker inntekt fra infotrygd fremfor inntekt fra skatt for å beregne sykepengegrunnlaget - skatt kommer først`() {
        inntektperioder {
            1.desember(2016) til 1.desember(2017) inntekter {
                ORGNUMMER inntekt INNTEKT
            }
            1.desember(2016) til 1.august(2017) inntekter {
                ORGNUMMER inntekt INNTEKT
            }
        }.forEach { it.lagreInntekter(historikk, 1.januar, UUID.randomUUID()) }
        utbetalingshistorikk(
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(1.januar, 25000.månedlig, ORGNUMMER, true)
            )
        ).addInntekter(UUID.randomUUID(), ORGNUMMER, historikk)
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(23, inspektør.inntektTeller.first())
        assertEquals(22, inspektør.inntektTeller.last())
        assertEquals(25000.månedlig, historikk.grunnlagForSykepengegrunnlag(31.desember(2017)))
    }

    @Test
    fun `Bruker inntekt fra infotrygd fremfor inntekt fra skatt for å beregne sykepengegrunnlaget - skatt kommer sist`() {
        utbetalingshistorikk(
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(1.januar, 25000.månedlig, ORGNUMMER, true)
            )
        ).addInntekter(UUID.randomUUID(), ORGNUMMER, historikk)
        inntektperioder {
            1.desember(2016) til 1.desember(2017) inntekter {
                ORGNUMMER inntekt INNTEKT
            }
            1.desember(2016) til 1.august(2017) inntekter {
                ORGNUMMER inntekt INNTEKT
            }
        }.forEach { it.lagreInntekter(historikk, 1.januar, UUID.randomUUID()) }
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(23, inspektør.inntektTeller.first())
        assertEquals(1, inspektør.inntektTeller.last())
        assertEquals(25000.månedlig, historikk.grunnlagForSykepengegrunnlag(31.desember(2017)))
    }

    @Test
    fun `intrikat test for sammenligningsgrunnlag der første fraværsdag er 1 januar`() {
        inntektperioder {
            inntektsgrunnlag = Inntektsvurdering.Inntektsgrunnlag.SAMMENLIGNINGSGRUNNLAG
            1.desember(2016) til 1.desember(2016) inntekter {
                ORGNUMMER inntekt 10000
            }
            1.desember(2016) til 1.august(2017) inntekter {
                ORGNUMMER inntekt 20000
            }
            1.oktober(2017) til 1.oktober(2017) inntekter {
                ORGNUMMER inntekt 30000
            }
            1.november(2017) til 1.januar(2018) inntekter {
                ORGNUMMER inntekt 12000
                ORGNUMMER inntekt 22000
            }
        }.forEach { it.lagreInntekter(historikk, 1.januar, UUID.randomUUID()) }
        inntektperioder {
            inntektsgrunnlag = Inntektsvurdering.Inntektsgrunnlag.SYKEPENGEGRUNNLAG
            1.desember(2016) til 1.desember(2017) inntekter {
                ORGNUMMER inntekt 15000

            }
        }.forEach { it.lagreInntekter(historikk, 1.januar, UUID.randomUUID()) }
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(30, inspektør.inntektTeller.first())
        assertEquals(17, inspektør.inntektTeller.last())
        assertEquals(254000.årlig, historikk.grunnlagForSammenligningsgrunnlag(31.desember(2017)))
    }

    @Test
    fun `intrikat test for sammenligningsgrunnlag der første fraværsdag er 2 januar`() {
        inntektperioder {
            inntektsgrunnlag = Inntektsvurdering.Inntektsgrunnlag.SAMMENLIGNINGSGRUNNLAG
            1.desember(2016) til 1.desember(2016) inntekter {
                ORGNUMMER inntekt 10000
            }
            1.desember(2016) til 1.august(2017) inntekter {
                ORGNUMMER inntekt 20000
            }
            1.oktober(2017) til 1.oktober(2017) inntekter {
                ORGNUMMER inntekt 30000
            }
            1.november(2017) til 1.januar(2018) inntekter {
                ORGNUMMER inntekt 12000
                ORGNUMMER inntekt 22000
            }
        }.forEach { it.lagreInntekter(historikk, 2.januar, UUID.randomUUID()) }
        inntektperioder {
            inntektsgrunnlag = Inntektsvurdering.Inntektsgrunnlag.SYKEPENGEGRUNNLAG
            1.desember(2016) til 1.desember(2017) inntekter {
                ORGNUMMER inntekt 15000

            }
        }.forEach { it.lagreInntekter(historikk, 2.januar, UUID.randomUUID()) }
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(30, inspektør.inntektTeller.first())
        assertEquals(17, inspektør.inntektTeller.last())
        assertEquals(258000.årlig, historikk.grunnlagForSammenligningsgrunnlag(1.januar))
    }

    @Test
    fun `intrikat test for sykepengegrunnlag der første fraværsdag er 1 januar`() {
        inntektperioder {
            inntektsgrunnlag = Inntektsvurdering.Inntektsgrunnlag.SYKEPENGEGRUNNLAG
            1.desember(2016) til 1.desember(2016) inntekter {
                ORGNUMMER inntekt 10000
            }
            1.desember(2016) til 1.august(2017) inntekter {
                ORGNUMMER inntekt 20000
            }
            1.oktober(2017) til 1.oktober(2017) inntekter {
                ORGNUMMER inntekt 30000
            }
            1.november(2017) til 1.januar(2018) inntekter {
                ORGNUMMER inntekt 12000
                ORGNUMMER inntekt 22000
            }
        }.forEach { it.lagreInntekter(historikk, 1.januar, UUID.randomUUID()) }
        inntektperioder {
            inntektsgrunnlag = Inntektsvurdering.Inntektsgrunnlag.SAMMENLIGNINGSGRUNNLAG
            1.desember(2016) til 1.desember(2017) inntekter {
                ORGNUMMER inntekt 15000

            }
        }.forEach { it.lagreInntekter(historikk, 1.januar, UUID.randomUUID()) }
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(30, inspektør.inntektTeller.first())
        assertEquals(17, inspektør.inntektTeller.last())
        assertEquals(256000.årlig, historikk.grunnlagForSykepengegrunnlag(31.desember(2017)))
    }

    @Test
    fun `intrikat test for sykepengegrunnlag der første fraværsdag er 2 januar`() {
        inntektperioder {
            inntektsgrunnlag = Inntektsvurdering.Inntektsgrunnlag.SYKEPENGEGRUNNLAG
            1.desember(2016) til 1.desember(2016) inntekter {
                ORGNUMMER inntekt 10000
            }
            1.desember(2016) til 1.august(2017) inntekter {
                ORGNUMMER inntekt 20000
            }
            1.oktober(2017) til 1.oktober(2017) inntekter {
                ORGNUMMER inntekt 30000
            }
            1.november(2017) til 1.januar(2018) inntekter {
                ORGNUMMER inntekt 12000
                ORGNUMMER inntekt 22000
            }
        }.forEach { it.lagreInntekter(historikk, 2.januar, UUID.randomUUID()) }
        inntektperioder {
            inntektsgrunnlag = Inntektsvurdering.Inntektsgrunnlag.SAMMENLIGNINGSGRUNNLAG
            1.desember(2016) til 1.desember(2017) inntekter {
                ORGNUMMER inntekt 15000

            }
        }.forEach { it.lagreInntekter(historikk, 2.januar, UUID.randomUUID()) }
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(30, inspektør.inntektTeller.first())
        assertEquals(17, inspektør.inntektTeller.last())
        assertEquals(392000.årlig, historikk.grunnlagForSykepengegrunnlag(1.januar))
    }

    @Test
    fun `Inntekt fra skatt siste tre måneder brukes til å beregne sykepengegrunnlaget`() {
        inntektperioder {
            1.desember(2016) til 1.desember(2017) inntekter {
                ORGNUMMER inntekt INNTEKT
            }
            1.desember(2016) til 1.august(2017) inntekter {
                ORGNUMMER inntekt INNTEKT
            }
        }.forEach { it.lagreInntekter(historikk, 1.januar, UUID.randomUUID()) }
        assertEquals(1, inspektør.inntektTeller.size)
        assertEquals(22, inspektør.inntektTeller.first())
        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(31.desember(2017)))
    }

    @Test
    fun `Inntekter med forskjellig dato konflikterer ikke`() {
        inntektperioder {
            1.desember(2016) til 1.desember(2017) inntekter {
                ORGNUMMER inntekt INNTEKT
            }
        }.forEach { it.lagreInntekter(historikk, 1.januar(2018), UUID.randomUUID()) }
        inntektperioder {
            1.desember(2016) til 1.august(2017) inntekter {
                ORGNUMMER inntekt INNTEKT
            }
        }.forEach { it.lagreInntekter(historikk, 15.januar(2018), UUID.randomUUID()) }
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(22, inspektør.inntektTeller.first())
        assertEquals(13, inspektør.inntektTeller.last())
        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(31.desember(2017)))
        assertEquals(INGEN, historikk.grunnlagForSykepengegrunnlag(14.januar(2018)))
    }

    @Test
    fun `Senere inntekter for samme dato overskriver eksisterende inntekter`() {
        inntektperioder {
            1.desember(2016) til 1.desember(2017) inntekter {
                ORGNUMMER inntekt INNTEKT
            }
        }.forEach { it.lagreInntekter(historikk, 1.januar(2018), UUID.randomUUID()) }
        inntektperioder {
            1.desember(2016) til 1.august(2017) inntekter {
                ORGNUMMER inntekt INNTEKT
            }
        }.forEach { it.lagreInntekter(historikk, 1.januar(2018), UUID.randomUUID()) }
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(9, inspektør.inntektTeller.first())
        assertEquals(13, inspektør.inntektTeller.last())
        assertEquals(INGEN, historikk.grunnlagForSykepengegrunnlag(31.desember(2017)))
    }

    @Test
    fun `Inntekt fra skatt skal bare brukes en gang`() {
        repeat(3) { i ->
            val meldingsreferanseId = UUID.randomUUID()
            inntektperioder {
                (1.desember(2016) til 1.desember(2017)) inntekter {
                    ORGNUMMER inntekt INNTEKT
                }
                1.desember(2016) til 1.august(2017) inntekter {
                    ORGNUMMER inntekt INNTEKT
                }
            }.forEach { it.lagreInntekter(historikk, 1.januar, meldingsreferanseId) }
        }

        assertEquals(3, inspektør.inntektTeller.size)
        inspektør.inntektTeller.forEach {
            assertEquals(22, it)
        }
        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(31.desember(2017)))
    }

    @Test
    fun `Inntekt fra skatt skal bare brukes én gang i beregning av sammenligningsgrunnlag`() {
        repeat(3) { i ->
            val meldingsreferanseId = UUID.randomUUID()
            inntektperioder {
                inntektsgrunnlag = Inntektsvurdering.Inntektsgrunnlag.SAMMENLIGNINGSGRUNNLAG
                1.desember(2016) til 1.desember(2017) inntekter {
                    ORGNUMMER inntekt INNTEKT
                }
            }.forEach { it.lagreInntekter(historikk, 1.januar, meldingsreferanseId) }
        }
        assertEquals(13, inspektør.inntektTeller.first())
        assertEquals(INNTEKT, historikk.grunnlagForSammenligningsgrunnlag(31.desember(2017)))
    }

    @Test
    fun `Inntekt for annen dato og samme kilde erstatter ikke eksisterende`() {
        inntektsmelding(førsteFraværsdag = 1.januar).addInntekt(historikk)
        inntektsmelding(førsteFraværsdag = 2.januar).addInntekt(historikk)
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(2, inspektør.inntektTeller.first())
        assertEquals(1, inspektør.inntektTeller.last())
    }

    @Test
    fun `Inntekt for samme dato og annen kilde erstatter ikke eksisterende`() {
        inntektsmelding().addInntekt(historikk)
        utbetalingshistorikk(
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(1.januar, INNTEKT, ORGNUMMER, true)
            )
        ).addInntekter(UUID.randomUUID(), ORGNUMMER, historikk)
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(2, inspektør.inntektTeller.first())
        assertEquals(1, inspektør.inntektTeller.last())
    }

    private class Inntektsinspektør(historikk: InntektshistorikkVol2) : InntekthistorikkVisitor {
        var inntektTeller = mutableListOf<Int>()

        init {
            historikk.accept(this)
        }

        override fun preVisitInntekthistorikkVol2(inntektshistorikk: InntektshistorikkVol2) {
            inntektTeller.clear()
        }

        override fun preVisitInnslag(innslag: InntektshistorikkVol2.Innslag) {
            inntektTeller.add(0)
        }

        override fun visitInntektVol2(
            inntektsopplysning: Inntektsopplysning,
            id: UUID,
            kilde: Inntektsopplysning.Kilde,
            fom: LocalDate,
            tidsstempel: LocalDateTime
        ) {
            inntektTeller.add(inntektTeller.removeLast() + 1)
        }

        override fun visitInntektSkattVol2(
            inntektsopplysning: Inntektsopplysning.Skatt,
            id: UUID,
            kilde: Inntektsopplysning.Kilde,
            fom: LocalDate,
            måned: YearMonth,
            tidsstempel: LocalDateTime
        ) {
            inntektTeller.add(inntektTeller.removeLast() + 1)
        }

        override fun visitInntektSaksbehandlerVol2(
            inntektsopplysning: Inntektsopplysning.Saksbehandler,
            id: UUID,
            kilde: Inntektsopplysning.Kilde,
            fom: LocalDate,
            tidsstempel: LocalDateTime
        ) {
            inntektTeller.add(inntektTeller.removeLast() + 1)
        }
    }

    private fun inntektsmelding(
        beregnetInntekt: Inntekt = INNTEKT,
        førsteFraværsdag: LocalDate = 1.januar
    ) = Inntektsmelding(
        meldingsreferanseId = UUID.randomUUID(),
        refusjon = Inntektsmelding.Refusjon(null, INNTEKT, emptyList()),
        orgnummer = ORGNUMMER,
        fødselsnummer = UNG_PERSON_FNR_2018,
        aktørId = AKTØRID,
        førsteFraværsdag = førsteFraværsdag,
        beregnetInntekt = beregnetInntekt,
        arbeidsgiverperioder = listOf(1.januar til 16.januar),
        ferieperioder = emptyList(),
        arbeidsforholdId = null,
        begrunnelseForReduksjonEllerIkkeUtbetalt = null
    )

    private fun utbetalingshistorikk(inntektshistorikk: List<Utbetalingshistorikk.Inntektsopplysning>) =
        Utbetalingshistorikk(
            aktørId = AKTØRID,
            fødselsnummer = UNG_PERSON_FNR_2018,
            organisasjonsnummer = ORGNUMMER,
            vedtaksperiodeId = UUID.randomUUID().toString(),
            utbetalinger = emptyList(),
            inntektshistorikk = inntektshistorikk
        )
}
