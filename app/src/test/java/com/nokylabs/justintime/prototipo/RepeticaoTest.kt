package com.nokylabs.justintime.prototipo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class RepeticaoTest {

    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)

    @Before fun fuso() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
        fmt.timeZone = TimeZone.getDefault()
    }

    private fun t(s: String) = fmt.parse(s)!!.time
    private fun s(ms: Long?) = ms?.let { fmt.format(it) }

    @Test fun naoRepete() = assertNull(Repeticao().proximaApos(t("2026-11-13 08:00"), t("2026-11-13 09:00")))

    @Test fun todoDia() {
        val r = Repeticao(Repeticao.Tipo.INTERVALO, 1, Repeticao.Unidade.DIA)
        assertEquals("2026-11-14 08:00", s(r.proximaApos(t("2026-11-13 08:00"), t("2026-11-13 08:00"))))
        assertEquals("2026-11-20 08:00", s(r.proximaApos(t("2026-11-13 08:00"), t("2026-11-19 09:00"))))
    }

    @Test fun ancoraNoFuturo() {
        val r = Repeticao(Repeticao.Tipo.INTERVALO, 1, Repeticao.Unidade.DIA)
        assertEquals("2026-12-01 08:00", s(r.proximaApos(t("2026-12-01 08:00"), t("2026-11-13 08:00"))))
    }

    @Test fun quinzenal() {
        val r = Repeticao(Repeticao.Tipo.INTERVALO, 2, Repeticao.Unidade.SEMANA)
        assertEquals("2026-11-27 18:00", s(r.proximaApos(t("2026-11-13 18:00"), t("2026-11-13 18:00"))))
        assertEquals("2026-12-11 18:00", s(r.proximaApos(t("2026-11-13 18:00"), t("2026-11-28 10:00"))))
    }

    @Test fun mensalNoDia31SemDeriva() {
        val r = Repeticao(Repeticao.Tipo.INTERVALO, 1, Repeticao.Unidade.MES)
        val ancora = t("2027-01-31 10:00")
        assertEquals("2027-02-28 10:00", s(r.proximaApos(ancora, t("2027-01-31 10:00"))))
        // Depois de fevereiro, volta para 31 (não fica preso no 28)
        assertEquals("2027-03-31 10:00", s(r.proximaApos(ancora, t("2027-02-28 10:00"))))
    }

    @Test fun aniversarioAnual() {
        val r = Repeticao(Repeticao.Tipo.INTERVALO, 1, Repeticao.Unidade.ANO)
        assertEquals("2027-10-20 00:00", s(r.proximaApos(t("2026-10-20 00:00"), t("2026-11-13 05:00"))))
        // 29/02 em ano não bissexto cai em 28/02
        assertEquals("2029-02-28 09:00", s(r.proximaApos(t("2028-02-29 09:00"), t("2028-03-01 00:00"))))
    }

    @Test fun saltoLongoNaoPulaOcorrencias() {
        val r = Repeticao(Repeticao.Tipo.INTERVALO, 1, Repeticao.Unidade.MES)
        assertEquals("2036-11-15 12:00", s(r.proximaApos(t("2026-11-15 12:00"), t("2036-11-01 00:00"))))
    }

    @Test fun horariosSemanais() {
        // seg de manhã, qui à noite, sáb à tarde
        val r = Repeticao(Repeticao.Tipo.SEMANAL, horarios = listOf(
            Horario(Calendar.MONDAY, 8, 0), Horario(Calendar.THURSDAY, 20, 0), Horario(Calendar.SATURDAY, 14, 0)))
        // 13/11/2026 é sexta-feira
        assertEquals("2026-11-14 14:00", s(r.proximaApos(0, t("2026-11-13 05:00"))))
        assertEquals("2026-11-16 08:00", s(r.proximaApos(0, t("2026-11-14 14:00"))))
        assertEquals("2026-11-19 20:00", s(r.proximaApos(0, t("2026-11-16 08:00"))))
        assertEquals("2026-11-21 14:00", s(r.proximaApos(0, t("2026-11-19 20:00"))))
        assertEquals("seg 08:00, qui 20:00, sáb 14:00", r.descricao())
    }

    @Test fun mesmoDiaMaisTarde() {
        val r = Repeticao(Repeticao.Tipo.SEMANAL, horarios = listOf(Horario(Calendar.FRIDAY, 8, 0), Horario(Calendar.FRIDAY, 18, 0)))
        assertEquals("2026-11-13 18:00", s(r.proximaApos(0, t("2026-11-13 09:00"))))
        assertEquals("2026-11-20 08:00", s(r.proximaApos(0, t("2026-11-13 18:00"))))
    }

    @Test fun codificacao() {
        val a = Repeticao(Repeticao.Tipo.SEMANAL, horarios = listOf(Horario(2, 8, 0), Horario(5, 20, 30)))
        assertEquals(a, Repeticao.decodificar(a.codificar()))
        val b = Repeticao(Repeticao.Tipo.INTERVALO, 3, Repeticao.Unidade.ANO)
        assertEquals(b, Repeticao.decodificar(b.codificar()))
        assertEquals(Repeticao(), Repeticao.decodificar("lixo|x"))
        assertEquals("A cada 15 dias", Repeticao(Repeticao.Tipo.INTERVALO, 2, Repeticao.Unidade.SEMANA).descricao())
    }
}
