package com.nokylabs.justintime

import com.nokylabs.justintime.dominio.Aviso
import com.nokylabs.justintime.dominio.Config
import com.nokylabs.justintime.dominio.DadosTarefa
import com.nokylabs.justintime.dominio.Lembrete
import com.nokylabs.justintime.dominio.Lembretes
import com.nokylabs.justintime.dominio.Tarefas
import com.nokylabs.justintime.dominio.TipoLembrete
import com.nokylabs.justintime.dominio.TipoPrazo
import com.nokylabs.justintime.dominio.Toque
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun t(s: String) = LocalDateTime.parse(s)

class LembretesTest {
    private lateinit var relogio: RelogioDeTeste
    private lateinit var tarefas: Tarefas
    private lateinit var lembretes: Lembretes

    @BeforeTest fun preparar() {
        relogio = RelogioDeTeste(t("2026-11-13T10:00"))
        tarefas = Tarefas(bancoNoPc(), AgendadorDeTeste(), relogio)
        lembretes = Lembretes(tarefas)
    }

    @Test fun silencio_atravessa_a_meia_noite() {
        val c = Config() // 21:00 → 08:00
        assertTrue(c.emSilencio(LocalTime(21, 0)))
        assertTrue(c.emSilencio(LocalTime(23, 59)))
        assertTrue(c.emSilencio(LocalTime(3, 0)))
        assertFalse(c.emSilencio(LocalTime(8, 0)))
        assertFalse(c.emSilencio(LocalTime(12, 0)))
        assertFalse(c.copy(silencioAtivo = false).emSilencio(LocalTime(23, 0)))
        // horário que não atravessa a meia-noite (13:00 → 14:00, um cochilo)
        assertTrue(Config(silencioInicio = LocalTime(13, 0), silencioFim = LocalTime(14, 0)).emSilencio(LocalTime(13, 30)))
    }

    @Test fun preferencias_sao_guardadas() {
        tarefas.salvarConfig(Config(silencioInicio = LocalTime(22, 30), resumoDoDiaAtivo = false))
        assertEquals(LocalTime(22, 30), tarefas.config().silencioInicio)
        assertFalse(tarefas.config().resumoDoDiaAtivo)
    }

    @Test fun como_tocar() {
        val normal = tarefas.criar(DadosTarefa("Normal", prazo = t("2026-11-13T22:00")))
        val insiste = tarefas.criar(DadosTarefa("Importante", prazo = t("2026-11-13T22:30"), tocarNoSilencio = true))
        val diaInteiro = tarefas.criar(DadosTarefa("Conta", tipoPrazo = TipoPrazo.DIA_INTEIRO, prazo = t("2026-11-13T00:00")))
        assertEquals(Toque.ALTO, lembretes.toque(normal))      // 10:00: fora do silêncio
        assertEquals(Toque.SILENCIOSO, lembretes.toque(diaInteiro))
        relogio.local = t("2026-11-13T22:00")
        assertEquals(Toque.SILENCIOSO, lembretes.toque(normal)) // 22:00: no silêncio
        assertEquals(Toque.ALTO, lembretes.toque(insiste))      // "tocar mesmo no silêncio"
    }

    @Test fun avisos_antes_do_prazo_vem_na_ordem() {
        tarefas.salvarConfig(Config(resumoDoDiaAtivo = false, silencioAtivo = false))
        val item = tarefas.criar(DadosTarefa("Prova", prazo = t("2026-11-13T15:00"), avisos = listOf(60, 15)))
        val primeiro = lembretes.proximo()!!
        assertEquals(Lembrete(TipoLembrete.AVISO, t("2026-11-13T14:00"), item.ocorrencia.id, 60), primeiro)
        val segundo = lembretes.proximo(depoisDe = primeiro.quando)!!
        assertEquals(t("2026-11-13T14:45"), segundo.quando)
        assertNull(lembretes.proximo(depoisDe = segundo.quando)) // depois não há mais nada
        relogio.local = t("2026-11-13T14:00")
        val aviso = lembretes.disparar(primeiro)
        assertIs<Aviso.FaltaPouco>(aviso); assertEquals(60, aviso.minutosAntes)
    }

    @Test fun aviso_de_tarefa_respondida_nao_aparece() {
        val item = tarefas.criar(DadosTarefa("Ligar", prazo = t("2026-11-13T15:00"), avisos = listOf(15)))
        tarefas.responder(item.ocorrencia.id, feita = true)
        assertNull(lembretes.disparar(Lembrete(TipoLembrete.AVISO, t("2026-11-13T14:45"), item.ocorrencia.id, 15)))
    }

    @Test fun resumo_do_dia_so_com_tarefas_de_dia_inteiro_de_hoje() {
        val config = tarefas.config()
        assertEquals(t("2026-11-13T20:30"), lembretes.proximo(config = config.copy(silencioAtivo = false))!!.quando)
        val resumo = Lembrete(TipoLembrete.RESUMO_DO_DIA, t("2026-11-13T20:30"))
        assertNull(lembretes.disparar(resumo)) // ainda não há nada
        tarefas.criar(DadosTarefa("Pagar conta", tipoPrazo = TipoPrazo.DIA_INTEIRO, prazo = t("2026-11-13T00:00")))
        tarefas.criar(DadosTarefa("Amanhã", tipoPrazo = TipoPrazo.DIA_INTEIRO, prazo = t("2026-11-14T00:00")))
        relogio.local = t("2026-11-13T20:30")
        val aviso = lembretes.disparar(resumo)
        assertIs<Aviso.ResumoDoDia>(aviso)
        assertEquals(listOf("Pagar conta"), aviso.itens.map { it.tarefa.titulo })
    }

    @Test fun fim_do_silencio_lembra_das_pendentes() {
        val item = tarefas.criar(DadosTarefa("Noite", prazo = t("2026-11-13T23:00")))
        relogio.local = t("2026-11-13T23:00")
        tarefas.prazoChegou(item.ocorrencia.id)
        assertEquals(t("2026-11-14T08:00"), lembretes.proximo(config = tarefas.config().copy(resumoDoDiaAtivo = false))!!.quando)
        relogio.local = t("2026-11-14T08:00")
        val aviso = lembretes.disparar(Lembrete(TipoLembrete.FIM_DO_SILENCIO, t("2026-11-14T08:00")))
        assertIs<Aviso.Pendentes>(aviso); assertEquals(1, aviso.itens.size)
    }

    @Test fun chave_do_lembrete_ida_e_volta() {
        val l = Lembrete(TipoLembrete.AVISO, t("2026-11-13T14:45"), "abc", 15)
        assertEquals(l, Lembrete.daChave(l.chave))
        val r = Lembrete(TipoLembrete.RESUMO_DO_DIA, t("2026-11-13T20:30"))
        assertEquals(r, Lembrete.daChave(r.chave))
    }
}
