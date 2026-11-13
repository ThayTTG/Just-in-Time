package com.nokylabs.justintime

import com.nokylabs.justintime.dominio.DadosTarefa
import com.nokylabs.justintime.dominio.Periodo
import com.nokylabs.justintime.dominio.Status
import com.nokylabs.justintime.dominio.Tarefas
import com.nokylabs.justintime.dominio.calcularSequencia
import com.nokylabs.justintime.dominio.intervalo
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun t(s: String) = LocalDateTime.parse(s)
private fun d(s: String) = LocalDate.parse(s)

class PlacarTest {
    private lateinit var relogio: RelogioDeTeste
    private lateinit var tarefas: Tarefas

    @BeforeTest fun preparar() {
        relogio = RelogioDeTeste(t("2026-11-11T08:00")) // quarta
        tarefas = Tarefas(bancoNoPc(), AgendadorDeTeste(), relogio)
    }

    private fun tarefa(nome: String, prazo: String) = tarefas.criar(DadosTarefa(nome, prazo = t(prazo))).ocorrencia.id

    @Test fun semana_vai_de_segunda_a_domingo() {
        assertEquals(d("2026-11-09") to d("2026-11-16"), Periodo.SEMANA.intervalo(d("2026-11-13"))) // sexta
        assertEquals(d("2026-11-09") to d("2026-11-16"), Periodo.SEMANA.intervalo(d("2026-11-09"))) // segunda
        assertEquals(d("2026-11-09") to d("2026-11-16"), Periodo.SEMANA.intervalo(d("2026-11-15"))) // domingo
        assertEquals(d("2026-11-13") to d("2026-11-14"), Periodo.DIA.intervalo(d("2026-11-13")))
    }

    @Test fun conta_entregues_nao_entregues_e_pela_frente() {
        val a = tarefa("A", "2026-11-13T09:00")
        val b = tarefa("B", "2026-11-13T10:00")
        val c = tarefa("C", "2026-11-13T11:00")
        tarefa("D", "2026-11-13T18:00")            // fica pela frente
        val ontem = tarefa("Quarta", "2026-11-11T20:00") // outro dia, mesma semana
        tarefa("Outra semana", "2026-11-17T10:00")
        relogio.local = t("2026-11-13T12:00")
        tarefas.responder(a, feita = true)
        tarefas.responder(b, feita = true)
        tarefas.responder(c, feita = false)
        tarefas.responder(ontem, feita = true)

        val dia = tarefas.placar(Periodo.DIA)
        assertEquals(Triple(2, 1, 1), Triple(dia.entregues, dia.naoEntregues, dia.pelaFrente))
        assertEquals(2.0 / 3, dia.taxa)
        val semana = tarefas.placar(Periodo.SEMANA)
        assertEquals(Triple(3, 1, 1), Triple(semana.entregues, semana.naoEntregues, semana.pelaFrente))
    }

    @Test fun adiada_e_feita_conta_como_entregue() {
        val id = tarefa("Relatório", "2026-11-13T09:00")
        relogio.local = t("2026-11-13T09:00")
        tarefas.prazoChegou(id)
        tarefas.adiarEm(id, 3600)
        tarefas.responder(id, feita = true)
        assertEquals(1, tarefas.placar(Periodo.DIA).entregues)
    }

    @Test fun sem_respostas_a_taxa_fica_vazia() = assertNull(tarefas.placar(Periodo.DIA).taxa)

    @Test fun sequencia_de_dias() {
        val F = Status.FEITA; val N = Status.NAO_FEITA
        val hoje = d("2026-11-13")
        val dias = mapOf(
            d("2026-11-13") to listOf(F),
            d("2026-11-12") to listOf(F, F),
            // 11/11 sem nada: não soma nem quebra
            d("2026-11-10") to listOf(F),
            d("2026-11-09") to listOf(F, N), // aqui quebra
            d("2026-11-08") to listOf(F),
        )
        assertEquals(3, calcularSequencia(hoje, dias))
        // Hoje ainda sem respostas: conta até ontem
        assertEquals(2, calcularSequencia(hoje, dias - d("2026-11-13")))
        // Uma "não feita" hoje zera
        assertEquals(0, calcularSequencia(hoje, dias + (d("2026-11-13") to listOf(F, N))))
    }

    @Test fun periodo_escolhido_fica_guardado() {
        assertEquals(Periodo.DIA, tarefas.periodoDoPlacar())
        tarefas.escolherPeriodo(Periodo.SEMANA)
        assertEquals(Periodo.SEMANA, tarefas.periodoDoPlacar())
    }
}
