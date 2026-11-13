package com.nokylabs.justintime

import com.nokylabs.justintime.dominio.Agendador
import com.nokylabs.justintime.dominio.DadosTarefa
import com.nokylabs.justintime.dominio.Item
import com.nokylabs.justintime.dominio.RegraViolada
import com.nokylabs.justintime.dominio.Relogio
import com.nokylabs.justintime.dominio.Repeticao
import com.nokylabs.justintime.dominio.Status
import com.nokylabs.justintime.dominio.Tarefas
import com.nokylabs.justintime.dominio.TipoPrazo
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Relógio que só anda quando o teste manda. */
class RelogioDeTeste(var local: LocalDateTime) : Relogio {
    override val fuso = TimeZone.of("America/Sao_Paulo")
    override fun agora(): Instant = local.toInstant(fuso)
}

/** Agendador que só anota o que foi pedido. */
class AgendadorDeTeste : Agendador {
    val agendados = mutableMapOf<String, Instant>()
    override fun agendar(item: Item, quando: Instant) { agendados[item.ocorrencia.id] = quando }
    override fun cancelar(ocorrenciaId: String) { agendados.remove(ocorrenciaId) }
}

private fun t(s: String) = LocalDateTime.parse(s)

class TarefasTest {
    private lateinit var relogio: RelogioDeTeste
    private lateinit var agendador: AgendadorDeTeste
    private lateinit var tarefas: Tarefas

    @BeforeTest fun preparar() {
        relogio = RelogioDeTeste(t("2026-11-13T10:00")) // sexta-feira
        agendador = AgendadorDeTeste()
        tarefas = Tarefas(bancoNoPc(), agendador, relogio)
    }

    @Test fun criar_agenda_o_alarme() {
        val item = tarefas.criar(DadosTarefa("Entregar notas", prazo = t("2026-11-13T18:00")))
        assertEquals(Status.ATIVA, item.status)
        assertEquals(tarefas.instante(t("2026-11-13T18:00")), agendador.agendados[item.ocorrencia.id])
        assertEquals(1, tarefas.abertasAgora().size)
    }

    @Test fun nao_cria_no_passado_nem_sem_nome() {
        assertFailsWith<RegraViolada> { tarefas.criar(DadosTarefa("X", prazo = t("2026-11-13T09:00"))) }
        assertFailsWith<RegraViolada> { tarefas.criar(DadosTarefa("  ", prazo = t("2026-11-13T19:00"))) }
    }

    @Test fun dia_inteiro_vence_23h59() {
        val item = tarefas.criar(DadosTarefa("Pagar conta", tipoPrazo = TipoPrazo.DIA_INTEIRO, prazo = t("2026-11-14T00:00")))
        assertEquals(t("2026-11-14T23:59"), item.prazo)
    }

    @Test fun prazo_chegou_vira_aguardando_e_repeticao_gera_a_proxima() {
        val item = tarefas.criar(DadosTarefa("Remédio", prazo = t("2026-11-13T20:00"),
            repeticao = Repeticao.Intervalo(1, Repeticao.Unidade.DIA)))
        relogio.local = t("2026-11-13T20:00:01")
        val disparado = tarefas.prazoChegou(item.ocorrencia.id)!!
        assertEquals(Status.AGUARDANDO, disparado.status)
        val abertas = tarefas.abertasAgora()
        assertEquals(2, abertas.size)
        assertEquals(Status.AGUARDANDO, abertas[0].status) // aguardando vem primeiro
        assertEquals(t("2026-11-14T20:00"), abertas[1].prazo)
        assertTrue(agendador.agendados.containsKey(abertas[1].ocorrencia.id))
        // tocar de novo não duplica
        assertNull(tarefas.prazoChegou(item.ocorrencia.id))
        assertEquals(2, tarefas.abertasAgora().size)
    }

    @Test fun adiar_nao_empurra_a_repeticao() {
        val item = tarefas.criar(DadosTarefa("Academia", prazo = t("2026-11-13T18:00"),
            repeticao = Repeticao.Intervalo(1, Repeticao.Unidade.DIA)))
        tarefas.adiar(item.ocorrencia.id, t("2026-11-13T19:30"))
        relogio.local = t("2026-11-13T19:30")
        tarefas.prazoChegou(item.ocorrencia.id)
        assertEquals(t("2026-11-14T18:00"), tarefas.abertasAgora().last().prazo) // continua 18:00
    }

    @Test fun responder_fecha_e_cancela_o_alarme() {
        val item = tarefas.criar(DadosTarefa("Ligar pro banco", prazo = t("2026-11-13T15:00")))
        tarefas.responder(item.ocorrencia.id, feita = true)
        assertEquals(0, tarefas.abertasAgora().size)
        assertTrue(agendador.agendados.isEmpty())
        assertEquals(Status.FEITA, tarefas.item(item.ocorrencia.id)!!.status)
    }

    @Test fun conflitos_por_nome_e_por_horario() {
        tarefas.criar(DadosTarefa("Reunião", prazo = t("2026-11-13T16:00")))
        val porNome = tarefas.conflitos("  reunião ", t("2026-11-14T09:00"))
        assertEquals(listOf("mesmo nome"), porNome.map { it.motivo })
        val porHorario = tarefas.conflitos("Outra coisa", t("2026-11-13T16:00:30"))
        assertEquals(listOf("mesmo horário"), porHorario.map { it.motivo })
        assertTrue(tarefas.conflitos("Outra coisa", t("2026-11-13T16:01")).isEmpty())
    }

    @Test fun editar_nome_mantem_prazo_vencido_e_editar_prazo_reabre() {
        val item = tarefas.criar(DadosTarefa("Relatório", prazo = t("2026-11-13T11:00")))
        relogio.local = t("2026-11-13T11:00")
        tarefas.prazoChegou(item.ocorrencia.id)
        // só o nome: continua aguardando, mesmo com o prazo no passado
        val renomeada = tarefas.editar(item.tarefa.id, DadosTarefa("Relatório final", prazo = t("2026-11-13T11:00")))
        assertEquals(Status.AGUARDANDO, renomeada.status)
        assertEquals("Relatório final", renomeada.tarefa.titulo)
        // prazo novo: reabre
        val reaberta = tarefas.editar(item.tarefa.id, DadosTarefa("Relatório final", prazo = t("2026-11-13T17:00")))
        assertEquals(Status.ATIVA, reaberta.status)
        assertTrue(agendador.agendados.containsKey(reaberta.ocorrencia.id))
    }

    @Test fun reconciliar_marca_os_que_passaram_com_o_celular_desligado() {
        tarefas.criar(DadosTarefa("A", prazo = t("2026-11-13T12:00")))
        tarefas.criar(DadosTarefa("B", prazo = t("2026-11-13T20:00")))
        agendador.agendados.clear() // celular reiniciou: o sistema esqueceu os alarmes
        relogio.local = t("2026-11-13T13:00")
        assertEquals(1, tarefas.reconciliar())
        assertEquals(listOf(Status.AGUARDANDO, Status.ATIVA), tarefas.abertasAgora().map { it.status })
        assertEquals(1, agendador.agendados.size)
    }

    @Test fun excluir_apaga_tudo() {
        val item = tarefas.criar(DadosTarefa("Apagar", prazo = t("2026-11-13T22:00")))
        tarefas.excluir(item.tarefa.id)
        assertTrue(tarefas.abertasAgora().isEmpty())
        assertTrue(agendador.agendados.isEmpty())
    }

    @Test fun finalizar_antes_mantem_a_repeticao() {
        val item = tarefas.criar(DadosTarefa("Regar plantas", prazo = t("2026-11-13T18:00"),
            repeticao = Repeticao.Intervalo(1, Repeticao.Unidade.DIA)))
        tarefas.finalizar(item.ocorrencia.id)
        assertEquals(Status.FEITA, tarefas.item(item.ocorrencia.id)!!.status)
        val abertas = tarefas.abertasAgora()
        assertEquals(listOf(t("2026-11-14T18:00")), abertas.map { it.prazo })
        assertEquals(setOf(abertas[0].ocorrencia.id), agendador.agendados.keys)
        // o alarme antigo, se tocasse mesmo assim, não faz nada
        relogio.local = t("2026-11-13T18:00")
        assertNull(tarefas.prazoChegou(item.ocorrencia.id))
        assertEquals(1, tarefas.abertasAgora().size)
    }

    @Test fun finalizar_sem_repeticao_so_fecha() {
        val item = tarefas.criar(DadosTarefa("Enviar e-mail", prazo = t("2026-11-13T18:00")))
        tarefas.finalizar(item.ocorrencia.id)
        assertTrue(tarefas.abertasAgora().isEmpty())
        assertTrue(agendador.agendados.isEmpty())
    }

    @Test fun ajustar_para_antes_e_depois() {
        val item = tarefas.criar(DadosTarefa("Prova", prazo = t("2026-11-14T18:00")))
        val id = item.ocorrencia.id
        tarefas.ajustar(id, t("2026-11-14T09:00")) // antes: não é adiamento
        assertEquals(t("2026-11-14T09:00"), tarefas.item(id)!!.prazo)
        assertEquals(0, tarefas.item(id)!!.ocorrencia.adiamentos)
        assertEquals(tarefas.instante(t("2026-11-14T09:00")), agendador.agendados[id])
        tarefas.ajustar(id, t("2026-11-15T10:00")) // depois: conta
        assertEquals(1, tarefas.item(id)!!.ocorrencia.adiamentos)
        assertEquals(t("2026-11-15T10:00"), tarefas.abertasAgora().single().prazo)
    }

    @Test fun ajustar_recusa_horario_que_ja_passou() {
        val item = tarefas.criar(DadosTarefa("Prova", prazo = t("2026-11-14T18:00")))
        assertFailsWith<RegraViolada> { tarefas.ajustar(item.ocorrencia.id, t("2026-11-13T09:59")) }
        assertFailsWith<RegraViolada> { tarefas.ajustar(item.ocorrencia.id, t("2026-11-13T10:00")) }
        assertEquals(t("2026-11-14T18:00"), tarefas.item(item.ocorrencia.id)!!.prazo)
        assertEquals("Esse horário já passou. Escolha outro.", tarefas.problemaNoPrazo(t("2026-11-12T23:00")))
        assertNull(tarefas.problemaNoPrazo(t("2026-11-13T10:01")))
    }

    @Test fun ajustar_nao_mexe_na_serie() {
        val item = tarefas.criar(DadosTarefa("Academia", prazo = t("2026-11-13T18:00"),
            repeticao = Repeticao.Intervalo(1, Repeticao.Unidade.DIA)))
        tarefas.ajustar(item.ocorrencia.id, t("2026-11-13T12:00"))
        relogio.local = t("2026-11-13T12:00")
        tarefas.prazoChegou(item.ocorrencia.id)
        assertEquals(t("2026-11-14T18:00"), tarefas.abertasAgora().last().prazo)
    }
}
