package no.nav.helse.person

import no.nav.helse.hendelser.*
import no.nav.helse.hendelser.Utbetalingshistorikk.Inntektsopplysning.Companion.lagreInntekter
import no.nav.helse.person.Inntektshistorikk.Inntektsopplysning
import no.nav.helse.testhelpers.*
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.*

internal class InntektshistorikkTest {

    private lateinit var historikk: Inntektshistorikk
    private val inspektør get() = Inntektsinspektør(historikk)

    private companion object {
        const val UNG_PERSON_FNR_2018 = "12020052345"
        const val AKTØRID = "42"
        const val ORGNUMMER = "987654321"
        val INNTEKT = 31000.00.månedlig
    }

    @BeforeEach
    fun setup() {
        historikk = Inntektshistorikk()
    }

    @Test
    fun `Inntekt fra inntektsmelding blir lagt til i inntektshistorikk`() {
        inntektsmelding().addInntekt(historikk, 1.januar)
        assertEquals(1, inspektør.inntektTeller.size)
        assertEquals(1, inspektør.inntektTeller.first())
    }

    @Test
    fun `Inntekt fra inntektsmelding brukes til å beregne sykepengegrunnlaget`() {
        inntektsmelding(førsteFraværsdag = 1.januar).addInntekt(historikk, 1.januar)
        assertEquals(1, inspektør.inntektTeller.size)
        assertEquals(1, inspektør.inntektTeller.first())
        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(1.januar(2018)))
    }

    @Test
    fun `Inntekt fra andre inntektsmelding overskriver ikke inntekt fra første, gitt samme første fraværsdag`() {
        inntektsmelding(førsteFraværsdag = 1.januar, beregnetInntekt = 30000.månedlig).addInntekt(historikk, 1.januar)
        inntektsmelding(førsteFraværsdag = 1.januar, beregnetInntekt = 29000.månedlig).addInntekt(historikk, 1.januar)
        inntektsmelding(førsteFraværsdag = 1.februar, beregnetInntekt = 31000.månedlig).addInntekt(historikk, 1.februar)
        assertEquals(30000.månedlig, historikk.grunnlagForSykepengegrunnlag(1.januar(2018)))
        assertEquals(31000.månedlig, historikk.grunnlagForSykepengegrunnlag(1.februar(2018)))
    }

    @Test
    fun `Inntekt fra inntektsmelding brukes ikke til å beregne sykepengegrunnlaget på annen dato`() {
        inntektsmelding(førsteFraværsdag = 1.januar).addInntekt(historikk, 1.januar)
        assertEquals(1, inspektør.inntektTeller.size)
        assertEquals(1, inspektør.inntektTeller.first())
        assertNull(historikk.grunnlagForSykepengegrunnlag(2.januar))
    }

    @Test
    fun `Inntekt fra infotrygd brukes til å beregne sykepengegrunnlaget`() {
        listOf(Utbetalingshistorikk.Inntektsopplysning(1.januar, INNTEKT, ORGNUMMER, true))
            .lagreInntekter(historikk, UUID.randomUUID())
        assertEquals(1, inspektør.inntektTeller.size)
        assertEquals(1, inspektør.inntektTeller.first())
        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(1.januar(2018)))
    }

    @Test
    fun `Bruker inntekt fra infotrygd fremfor inntekt fra inntektsmelding for å beregne sykepengegrunnlaget`() {
        inntektsmelding(beregnetInntekt = 20000.månedlig).addInntekt(historikk, 1.januar)
        listOf(Utbetalingshistorikk.Inntektsopplysning(1.januar, 25000.månedlig, ORGNUMMER, true))
            .lagreInntekter(historikk, UUID.randomUUID())
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(2, inspektør.inntektTeller.first())
        assertEquals(1, inspektør.inntektTeller.last())
        assertEquals(25000.månedlig, historikk.grunnlagForSykepengegrunnlag(1.januar(2018)))
    }

    @Test
    fun `Bruker inntekt fra infotrygd fremfor inntekt fra skatt for å beregne sykepengegrunnlaget - skatt kommer først`() {
        inntektperioder {
            1.desember(2016) til 1.desember(2017) inntekter {
                ORGNUMMER inntekt INNTEKT
            }
            1.desember(2016) til 1.september(2017) inntekter {
                ORGNUMMER inntekt INNTEKT
            }
        }.forEach { it.lagreInntekter(historikk, 1.januar, UUID.randomUUID()) }
        listOf(Utbetalingshistorikk.Inntektsopplysning(1.januar, 25000.månedlig, ORGNUMMER, true))
            .lagreInntekter(historikk, UUID.randomUUID())
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(24, inspektør.inntektTeller.first())
        assertEquals(23, inspektør.inntektTeller.last())
        assertEquals(25000.månedlig, historikk.grunnlagForSykepengegrunnlag(1.januar(2018)))
    }

    @Test
    fun `Bruker inntekt fra infotrygd fremfor inntekt fra skatt for å beregne sykepengegrunnlaget - skatt kommer sist`() {
        listOf(Utbetalingshistorikk.Inntektsopplysning(1.januar, 25000.månedlig, ORGNUMMER, true))
            .lagreInntekter(historikk, UUID.randomUUID())
        inntektperioder {
            1.desember(2016) til 1.desember(2017) inntekter {
                ORGNUMMER inntekt INNTEKT
            }
            1.desember(2016) til 1.september(2017) inntekter {
                ORGNUMMER inntekt INNTEKT
            }
        }.forEach { it.lagreInntekter(historikk, 1.januar, UUID.randomUUID()) }
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(24, inspektør.inntektTeller.first())
        assertEquals(1, inspektør.inntektTeller.last())
        assertEquals(25000.månedlig, historikk.grunnlagForSykepengegrunnlag(1.januar(2018)))
    }

    @Test
    fun `intrikat test for sammenligningsgrunnlag der første fraværsdag er 31 desember`() {
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
        }.forEach { it.lagreInntekter(historikk, 31.desember(2017), UUID.randomUUID()) }
        inntektperioder {
            inntektsgrunnlag = Inntektsvurdering.Inntektsgrunnlag.SYKEPENGEGRUNNLAG
            1.desember(2016) til 1.desember(2017) inntekter {
                ORGNUMMER inntekt 15000

            }
        }.forEach { it.lagreInntekter(historikk, 31.desember(2017), UUID.randomUUID()) }
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(30, inspektør.inntektTeller.first())
        assertEquals(17, inspektør.inntektTeller.last())
        assertEquals(254000.årlig, historikk.grunnlagForSammenligningsgrunnlag(31.desember(2017)))
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
        assertEquals(258000.årlig, historikk.grunnlagForSammenligningsgrunnlag(1.januar))
    }

    @Test
    fun `intrikat test for sykepengegrunnlag der første fraværsdag er 31 desember`() {
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
        }.forEach { it.lagreInntekter(historikk, 31.desember(2017), UUID.randomUUID()) }
        inntektperioder {
            inntektsgrunnlag = Inntektsvurdering.Inntektsgrunnlag.SAMMENLIGNINGSGRUNNLAG
            1.desember(2016) til 1.desember(2017) inntekter {
                ORGNUMMER inntekt 15000

            }
        }.forEach { it.lagreInntekter(historikk, 31.desember(2017), UUID.randomUUID()) }
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(30, inspektør.inntektTeller.first())
        assertEquals(17, inspektør.inntektTeller.last())
        assertEquals(256000.årlig, historikk.grunnlagForSykepengegrunnlag(31.desember(2017)))
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
        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(1.januar))
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
        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(1.januar(2018)))
        assertNull(historikk.grunnlagForSykepengegrunnlag(15.januar(2018)))
    }

    @Test
    fun `Senere inntekter for samme dato overskriver eksisterende inntekter`() {
        inntektperioder {
            1.desember(2016) til 1.desember(2017) inntekter {
                ORGNUMMER inntekt INNTEKT
            }
        }.forEach { it.lagreInntekter(historikk, 1.januar(2018), UUID.randomUUID()) }
        Thread.sleep(10) // Nødvendig for konsistent resultat på windows
        inntektperioder {
            1.desember(2016) til 1.august(2017) inntekter {
                ORGNUMMER inntekt INNTEKT
            }
        }.forEach { it.lagreInntekter(historikk, 1.januar(2018), UUID.randomUUID()) }
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(9, inspektør.inntektTeller.first())
        assertEquals(13, inspektør.inntektTeller.last())
        assertNull(historikk.grunnlagForSykepengegrunnlag(1.januar(2018)))
    }

    @Test
    fun `Inntekt fra skatt skal bare brukes en gang`() {
        repeat(3) { _ ->
            val meldingsreferanseId = UUID.randomUUID()
            inntektperioder {
                (1.desember(2016) til 1.desember(2017)) inntekter {
                    ORGNUMMER inntekt INNTEKT
                }
                1.desember(2016) til 1.august(2017) inntekter {
                    ORGNUMMER inntekt INNTEKT
                }
            }.forEach { it.lagreInntekter(historikk, 1.januar, meldingsreferanseId) }
            Thread.sleep(10) // Nødvendig for konsistent resultat på windows
        }

        assertEquals(3, inspektør.inntektTeller.size)
        inspektør.inntektTeller.forEach {
            assertEquals(22, it)
        }
        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(1.januar(2018)))
    }

    @Test
    fun `Inntekt fra skatt skal bare brukes én gang i beregning av sammenligningsgrunnlag`() {
        repeat(3) { _ ->
            val meldingsreferanseId = UUID.randomUUID()
            inntektperioder {
                inntektsgrunnlag = Inntektsvurdering.Inntektsgrunnlag.SAMMENLIGNINGSGRUNNLAG
                1.desember(2016) til 1.desember(2017) inntekter {
                    ORGNUMMER inntekt INNTEKT
                }
            }.forEach { it.lagreInntekter(historikk, 1.januar, meldingsreferanseId) }
        }
        assertEquals(13, inspektør.inntektTeller.first())
        assertEquals(INNTEKT, historikk.grunnlagForSammenligningsgrunnlag(1.januar(2018)))
    }

    @Test
    fun `Inntekt for annen dato og samme kilde erstatter ikke eksisterende`() {
        inntektsmelding(førsteFraværsdag = 1.januar).addInntekt(historikk, 1.januar)
        inntektsmelding(
            førsteFraværsdag = 2.januar,
            arbeidsgiverperioder = listOf(2.januar til 17.januar)
        ).addInntekt(historikk, 1.januar)
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(2, inspektør.inntektTeller.first())
        assertEquals(1, inspektør.inntektTeller.last())
    }

    @Test
    fun `Inntekt for samme dato og annen kilde erstatter ikke eksisterende`() {
        inntektsmelding().addInntekt(historikk, 1.januar)
        listOf(Utbetalingshistorikk.Inntektsopplysning(1.januar, INNTEKT, ORGNUMMER, true))
            .lagreInntekter(historikk, UUID.randomUUID())
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(2, inspektør.inntektTeller.first())
        assertEquals(1, inspektør.inntektTeller.last())
    }

    @Test
    fun `Finner nærmeste inntekt fra Infotrygd, hvis det ikke finnes inntekt for skjæringstidspunkt`() {
        listOf(
            Utbetalingshistorikk.Inntektsopplysning(10.januar, 30000.månedlig, ORGNUMMER, true),
            Utbetalingshistorikk.Inntektsopplysning(5.januar, 25000.månedlig, ORGNUMMER, true)
        )
            .lagreInntekter(historikk, UUID.randomUUID())
        assertEquals(30000.månedlig, historikk.grunnlagForSykepengegrunnlag(1.januar, 11.januar))
        assertEquals(25000.månedlig, historikk.grunnlagForSykepengegrunnlag(1.januar, 9.januar))
        assertNull(historikk.grunnlagForSykepengegrunnlag(1.januar, 4.januar))
    }

    @Test
    fun `Kopier inntektsopplysning fra inntektsmelding`() {
        inntektsmelding(førsteFraværsdag = 1.januar).addInntekt(historikk, 1.januar)

        assertTrue(historikk.opprettReferanse(1.januar, 10.januar, UUID.randomUUID()))

        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(1.januar))
        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(10.januar))

        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(2, inspektør.inntektTeller.first())
        assertEquals(1, inspektør.inntektTeller.last())
    }

    @Test
    fun `Kopierer ikke inntektsmeldinginntekt med annen dato`() {
        inntektsmelding(førsteFraværsdag = 1.januar).addInntekt(historikk, 1.januar)

        assertFalse(historikk.opprettReferanse(5.januar, 10.januar, UUID.randomUUID()))

        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(1.januar))
        assertNull(historikk.grunnlagForSykepengegrunnlag(10.januar))

        assertEquals(1, inspektør.inntektTeller.size)
        assertEquals(1, inspektør.inntektTeller.first())
    }

    @Test
    fun `Oppretter ikke nytt innslag hvis ingen inntekter kopieres`() {
        assertFalse(historikk.opprettReferanse(1.januar, 10.januar, UUID.randomUUID()))

        assertNull(historikk.grunnlagForSykepengegrunnlag(1.januar))
        assertNull(historikk.grunnlagForSykepengegrunnlag(10.januar))

        assertTrue(inspektør.inntektTeller.isEmpty())
    }

    @Test
    fun `Kopier inntektsopplysning fra infotrygd`() {
        listOf(Utbetalingshistorikk.Inntektsopplysning(1.januar, INNTEKT, ORGNUMMER, true))
                .lagreInntekter(historikk, UUID.randomUUID())

        assertTrue(historikk.opprettReferanse(1.januar, 10.januar, UUID.randomUUID()))

        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(1.januar))
        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(10.januar))

        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(2, inspektør.inntektTeller.first())
        assertEquals(1, inspektør.inntektTeller.last())
    }

    @Test
    fun `Kopierer ikke infotrygdinntekt med annen dato`() {
        listOf(Utbetalingshistorikk.Inntektsopplysning(1.januar, INNTEKT, ORGNUMMER, true))
                .lagreInntekter(historikk, UUID.randomUUID())

        assertFalse(historikk.opprettReferanse(5.januar, 10.januar, UUID.randomUUID()))

        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(1.januar))
        assertNull(historikk.grunnlagForSykepengegrunnlag(10.januar))

        assertEquals(1, inspektør.inntektTeller.size)
        assertEquals(1, inspektør.inntektTeller.first())
    }

    @Test
    fun `Kopier inntektsopplysning fra saksbehandler`() {
        historikk {
            addSaksbehandler(1.januar, UUID.randomUUID(), INNTEKT)
        }

        assertTrue(historikk.opprettReferanse(1.januar, 10.januar, UUID.randomUUID()))

        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(1.januar))
        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(10.januar))

        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(2, inspektør.inntektTeller.first())
        assertEquals(1, inspektør.inntektTeller.last())
    }

    @Test
    fun `Kopierer ikke saksbehandlerinntekt med annen dato`() {
        historikk {
            addSaksbehandler(1.januar, UUID.randomUUID(), INNTEKT)
        }

        assertFalse(historikk.opprettReferanse(5.januar, 10.januar, UUID.randomUUID()))

        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(1.januar))
        assertNull(historikk.grunnlagForSykepengegrunnlag(10.januar))

        assertEquals(1, inspektør.inntektTeller.size)
        assertEquals(1, inspektør.inntektTeller.first())
    }

    @Test
    fun `Kopier inntektsopplysningskopi`() {
        historikk {
            addSaksbehandler(1.januar, UUID.randomUUID(), INNTEKT)
        }

        assertTrue(historikk.opprettReferanse(1.januar, 10.januar, UUID.randomUUID()))
        assertTrue(historikk.opprettReferanse(10.januar, 20.januar, UUID.randomUUID()))

        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(1.januar))
        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(10.januar))
        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(20.januar))

        assertEquals(3, inspektør.inntektTeller.size)
        assertEquals(3, inspektør.inntektTeller[0])
        assertEquals(2, inspektør.inntektTeller[1])
        assertEquals(1, inspektør.inntektTeller[2])
    }

    @Test
    fun `Kopierer ikke inntektsopplysningskopiinntekt med annen dato`() {
        historikk {
            addSaksbehandler(1.januar, UUID.randomUUID(), INNTEKT)
        }

        assertTrue(historikk.opprettReferanse(1.januar, 10.januar, UUID.randomUUID()))
        assertFalse(historikk.opprettReferanse(15.januar, 20.januar, UUID.randomUUID()))

        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(1.januar))
        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(10.januar))
        assertNull(historikk.grunnlagForSykepengegrunnlag(20.januar))

        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(2, inspektør.inntektTeller.first())
        assertEquals(1, inspektør.inntektTeller.last())
    }

    @Test
    fun `Kopierer ikke skatteopplysninger`() {
        inntektperioder {
            inntektsgrunnlag = Inntektsvurdering.Inntektsgrunnlag.SYKEPENGEGRUNNLAG
            1.oktober(2017) til 1.desember(2017) inntekter {
                ORGNUMMER inntekt INNTEKT
            }
        }.forEach { it.lagreInntekter(historikk, 1.januar, UUID.randomUUID()) }

        assertFalse(historikk.opprettReferanse(1.januar, 10.januar, UUID.randomUUID()))

        assertEquals(INNTEKT, historikk.grunnlagForSykepengegrunnlag(1.januar))
        assertNull(historikk.grunnlagForSykepengegrunnlag(10.januar))

        assertEquals(1, inspektør.inntektTeller.size)
        assertEquals(3, inspektør.inntektTeller.first())
    }

    private class Inntektsinspektør(historikk: Inntektshistorikk) : InntekthistorikkVisitor {
        var inntektTeller = mutableListOf<Int>()

        init {
            historikk.accept(this)
        }

        override fun preVisitInntekthistorikk(inntektshistorikk: Inntektshistorikk) {
            inntektTeller.clear()
        }

        override fun preVisitInnslag(innslag: Inntektshistorikk.Innslag, id: UUID) {
            inntektTeller.add(0)
        }

        override fun visitInntekt(
            inntektsopplysning: Inntektsopplysning,
            id: UUID,
            fom: LocalDate,
            tidsstempel: LocalDateTime
        ) {
            inntektTeller.add(inntektTeller.removeLast() + 1)
        }

        override fun visitInntektSkatt(
            id: UUID,
            fom: LocalDate,
            måned: YearMonth,
            tidsstempel: LocalDateTime
        ) {
            inntektTeller.add(inntektTeller.removeLast() + 1)
        }

        override fun visitInntektSaksbehandler(
            id: UUID,
            fom: LocalDate,
            tidsstempel: LocalDateTime
        ) {
            inntektTeller.add(inntektTeller.removeLast() + 1)
        }

        override fun visitSaksbehandler(
            saksbehandler: Inntektshistorikk.Saksbehandler,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            tidsstempel: LocalDateTime
        ) {
            inntektTeller.add(inntektTeller.removeLast() + 1)
        }

        override fun visitInntektsmelding(
            inntektsmelding: Inntektshistorikk.Inntektsmelding,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            tidsstempel: LocalDateTime
        ) {
            inntektTeller.add(inntektTeller.removeLast() + 1)
        }

        override fun visitInfotrygd(
            infotrygd: Inntektshistorikk.Infotrygd,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            tidsstempel: LocalDateTime
        ) {
            inntektTeller.add(inntektTeller.removeLast() + 1)
        }

        override fun visitSkattSykepengegrunnlag(
            sykepengegrunnlag: Inntektshistorikk.Skatt.Sykepengegrunnlag,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            måned: YearMonth,
            type: Inntektshistorikk.Skatt.Inntekttype,
            fordel: String,
            beskrivelse: String,
            tidsstempel: LocalDateTime
        ) {
            inntektTeller.add(inntektTeller.removeLast() + 1)
        }

        override fun visitSkattSammenligningsgrunnlag(
            sammenligningsgrunnlag: Inntektshistorikk.Skatt.Sammenligningsgrunnlag,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            måned: YearMonth,
            type: Inntektshistorikk.Skatt.Inntekttype,
            fordel: String,
            beskrivelse: String,
            tidsstempel: LocalDateTime
        ) {
            inntektTeller.add(inntektTeller.removeLast() + 1)
        }
    }

    private fun inntektsmelding(
        beregnetInntekt: Inntekt = INNTEKT,
        førsteFraværsdag: LocalDate = 1.januar,
        arbeidsgiverperioder: List<Periode> = listOf(1.januar til 16.januar)
    ) = Inntektsmelding(
        meldingsreferanseId = UUID.randomUUID(),
        refusjon = Inntektsmelding.Refusjon(null, INNTEKT, emptyList()),
        orgnummer = ORGNUMMER,
        fødselsnummer = UNG_PERSON_FNR_2018,
        aktørId = AKTØRID,
        førsteFraværsdag = førsteFraværsdag,
        beregnetInntekt = beregnetInntekt,
        arbeidsgiverperioder = arbeidsgiverperioder,
        ferieperioder = emptyList(),
        arbeidsforholdId = null,
        begrunnelseForReduksjonEllerIkkeUtbetalt = null
    )
}
