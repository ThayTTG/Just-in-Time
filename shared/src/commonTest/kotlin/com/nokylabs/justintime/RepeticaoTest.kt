package com.nokylabs.justintime

import com.nokylabs.justintime.dominio.Horario
import com.nokylabs.justintime.dominio.Repeticao
import com.nokylabs.justintime.dominio.Repeticao.Unidade
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** As mesmas regras testadas no protótipo (v0.0.3), agora em hora local. */
class RepeticaoTest {
    private fun t(s: String) = LocalDateTime.parse(s)
    private val qualquer = t("2000-01-01T00:00")

    @Test fun naoRepete() = assertNull(Repeticao.Nenhuma.proximaApos(t("2026-11-13T08:00"), t("2026-11-13T09:00")))

    @Test fun todoDia() {
        val r = Repeticao.Intervalo(1, Unidade.DIA)
        assertEquals(t("2026-11-14T08:00"), r.proximaApos(t("2026-11-13T08:00"), t("2026-11-13T08:00")))
        assertEquals(t("2026-11-20T08:00"), r.proximaApos(t("2026-11-13T08:00"), t("2026-11-19T09:00")))
    }

    @Test fun ancoraNoFuturo() =
        assertEquals(t("2026-12-01T08:00"), Repeticao.Intervalo(1, Unidade.DIA).proximaApos(t("2026-12-01T08:00"), t("2026-11-13T08:00")))

    @Test fun quinzenal() {
        val r = Repeticao.Intervalo(2, Unidade.SEMANA)
        assertEquals(t("2026-11-27T18:00"), r.proximaApos(t("2026-11-13T18:00"), t("2026-11-13T18:00")))
        assertEquals(t("2026-12-11T18:00"), r.proximaApos(t("2026-11-13T18:00"), t("2026-11-28T10:00")))
    }

    @Test fun mensalNoDia31SemDeriva() {
        val r = Repeticao.Intervalo(1, Unidade.MES)
        assertEquals(t("2027-02-28T10:00"), r.proximaApos(t("2027-01-31T10:00"), t("2027-01-31T10:00")))
        assertEquals(t("2027-03-31T10:00"), r.proximaApos(t("2027-01-31T10:00"), t("2027-02-28T10:00")))
    }

    @Test fun aniversarioAnual() {
        val r = Repeticao.Intervalo(1, Unidade.ANO)
        assertEquals(t("2027-10-20T00:00"), r.proximaApos(t("2026-10-20T00:00"), t("2026-11-13T05:00")))
        assertEquals(t("2029-02-28T09:00"), r.proximaApos(t("2028-02-29T09:00"), t("2028-03-01T00:00")))
    }

    @Test fun saltoLongoNaoPulaOcorrencias() =
        assertEquals(t("2036-11-15T12:00"), Repeticao.Intervalo(1, Unidade.MES).proximaApos(t("2026-11-15T12:00"), t("2036-11-01T00:00")))

    @Test fun horariosSemanais() {
        val r = Repeticao.Semanal(listOf(
            Horario(DayOfWeek.MONDAY, LocalTime(8, 0)), Horario(DayOfWeek.THURSDAY, LocalTime(20, 0)),
            Horario(DayOfWeek.SATURDAY, LocalTime(14, 0))))
        assertEquals(t("2026-11-14T14:00"), r.proximaApos(qualquer, t("2026-11-13T05:00"))) // 13/11 é sexta
        assertEquals(t("2026-11-16T08:00"), r.proximaApos(qualquer, t("2026-11-14T14:00")))
        assertEquals(t("2026-11-19T20:00"), r.proximaApos(qualquer, t("2026-11-16T08:00")))
        assertEquals(t("2026-11-21T14:00"), r.proximaApos(qualquer, t("2026-11-19T20:00")))
        assertEquals("seg 08:00, qui 20:00, sáb 14:00", r.descricao())
    }

    @Test fun mesmoDiaMaisTarde() {
        val r = Repeticao.Semanal(listOf(Horario(DayOfWeek.FRIDAY, LocalTime(8, 0)), Horario(DayOfWeek.FRIDAY, LocalTime(18, 0))))
        assertEquals(t("2026-11-13T18:00"), r.proximaApos(qualquer, t("2026-11-13T09:00")))
        assertEquals(t("2026-11-20T08:00"), r.proximaApos(qualquer, t("2026-11-13T18:00")))
    }

    @Test fun codificacao() {
        val a = Repeticao.Semanal(listOf(Horario(DayOfWeek.MONDAY, LocalTime(8, 0)), Horario(DayOfWeek.THURSDAY, LocalTime(20, 30))))
        assertEquals(a, Repeticao.decodificar(a.codificar()))
        val b = Repeticao.Intervalo(3, Unidade.ANO)
        assertEquals(b, Repeticao.decodificar(b.codificar()))
        assertEquals(Repeticao.Nenhuma, Repeticao.decodificar("lixo|x"))
        assertEquals("A cada 15 dias", Repeticao.Intervalo(2, Unidade.SEMANA).descricao())
    }
}
