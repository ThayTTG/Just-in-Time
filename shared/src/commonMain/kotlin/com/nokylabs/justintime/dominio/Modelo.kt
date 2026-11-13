package com.nokylabs.justintime.dominio

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** Prazo com horário exato (toca alarme) ou "dia inteiro" (vence às 23:59, sem alarme). */
enum class TipoPrazo { EXATO, DIA_INTEIRO }

/** Estados de uma ocorrência (o diagrama do documento de arquitetura). */
enum class Status {
    ATIVA, AGUARDANDO, FEITA, NAO_FEITA;

    val aberta get() = this == ATIVA || this == AGUARDANDO
    val rotulo get() = when (this) {
        ATIVA -> "Ativa"; AGUARDANDO -> "Aguardando resposta"; FEITA -> "Feita"; NAO_FEITA -> "Não feita"
    }
}

/** Motivos rápidos de "não feito" (opcional). */
enum class Motivo(val rotulo: String) { ESQUECI("Esqueci"), FALTOU_TEMPO("Faltou tempo"), DESISTI("Desisti"), OUTRO("Outro") }

/** A tarefa e a sua regra. Cada vez que ela vence, nasce uma [Ocorrencia]. */
data class Tarefa(
    val id: String,
    val titulo: String,
    val emoji: String,
    /** 0 = sem cor (a barra mostra a urgência). */
    val cor: Int,
    val tipoPrazo: TipoPrazo,
    /** Primeiro prazo da série: base das repetições. */
    val ancora: LocalDateTime,
    val repeticao: Repeticao,
    /** Avisos antes do prazo, em minutos (ex.: 60 e 15). */
    val avisos: List<Int>,
    val tocarNoSilencio: Boolean,
    val arquivada: Boolean,
) {
    /** "🎂 Aniversário da Ana" */
    val rotulo get() = if (emoji.isBlank()) titulo else "$emoji $titulo"
}

/** Uma vez em que a tarefa vence. É ela que recebe a resposta. */
data class Ocorrencia(
    val id: String,
    val tarefaId: String,
    /** Prazo calculado pela repetição (não muda ao adiar). */
    val prazoOriginal: LocalDateTime,
    /** Prazo atual (já com adiamentos). */
    val prazo: LocalDateTime,
    val status: Status,
    val adiamentos: Int,
    val motivo: Motivo?,
    /** Quando o alarme realmente tocou (epoch ms), para medir atraso. */
    val disparouEm: Long?,
    val respondidaEm: Long?,
)

/** O que as telas, o widget e a bolha mostram: a ocorrência junto com a sua tarefa. */
data class Item(val tarefa: Tarefa, val ocorrencia: Ocorrencia) {
    val rotulo get() = tarefa.rotulo
    val prazo get() = ocorrencia.prazo
    val status get() = ocorrencia.status
}

/** O que a pessoa preenche no formulário (criar ou editar). */
data class DadosTarefa(
    val titulo: String,
    val emoji: String = "",
    val cor: Int = 0,
    val tipoPrazo: TipoPrazo = TipoPrazo.EXATO,
    /** Prazo escolhido. Em DIA_INTEIRO, a hora é ignorada (vale 23:59). */
    val prazo: LocalDateTime,
    val repeticao: Repeticao = Repeticao.Nenhuma,
    val avisos: List<Int> = emptyList(),
    val tocarNoSilencio: Boolean = false,
) {
    /** O prazo de verdade: em "dia inteiro", 23:59 do dia escolhido. */
    val prazoEfetivo: LocalDateTime
        get() = if (tipoPrazo == TipoPrazo.DIA_INTEIRO) LocalDateTime(prazo.date, FIM_DO_DIA) else prazo

    companion object { val FIM_DO_DIA = LocalTime(23, 59) }
}

/** Uma tarefa aberta parecida com a que está sendo criada/editada. */
data class Conflito(val item: Item, val mesmoNome: Boolean, val mesmoHorario: Boolean) {
    val motivo get() = when {
        mesmoNome && mesmoHorario -> "mesmo nome e mesmo horário"
        mesmoNome -> "mesmo nome"
        else -> "mesmo horário"
    }
}
