package com.nokylabs.justintime.dominio

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Preferências que mudam como e quando o app toca. */
data class Config(
    /** Horário silencioso: alarmes e avisos viram notificações sem som. */
    val silencioAtivo: Boolean = true,
    val silencioInicio: LocalTime = LocalTime(21, 0),
    val silencioFim: LocalTime = LocalTime(8, 0),
    /** Resumo das tarefas de dia inteiro ainda abertas. */
    val resumoDoDiaAtivo: Boolean = true,
    val resumoDoDia: LocalTime = LocalTime(20, 30),
    /** Notificação fixa com a contagem das próximas tarefas. */
    val resumoNaNotificacao: Boolean = true,
    /** Bolha flutuante com a próxima tarefa (precisa de "aparecer sobre outros apps"). */
    val bolhaAtiva: Boolean = true,
    /** Tamanho da bolha: 1 = normal, de 0,7 a 1,8. */
    val bolhaTamanho: Float = 1f,
) {
    /** Se [hora] cai no horário silencioso (que pode atravessar a meia-noite: 21:00 → 08:00). */
    fun emSilencio(hora: LocalTime): Boolean {
        if (!silencioAtivo || silencioInicio == silencioFim) return false
        return if (silencioInicio < silencioFim) hora >= silencioInicio && hora < silencioFim
        else hora >= silencioInicio || hora < silencioFim
    }
}

/** Como o prazo deve tocar. */
enum class Toque {
    /** Alarme: som insistente e tela de alarme. */
    ALTO,
    /** Notificação sem som (horário silencioso ou tarefa de dia inteiro). */
    SILENCIOSO,
}

/** Os lembretes que não são o prazo em si. */
enum class TipoLembrete { AVISO, RESUMO_DO_DIA, FIM_DO_SILENCIO }

/**
 * Um lembrete agendado. [ocorrenciaId] e [minutosAntes] só existem nos avisos ("faltam 15 min").
 */
data class Lembrete(
    val tipo: TipoLembrete,
    val quando: LocalDateTime,
    val ocorrenciaId: String? = null,
    val minutosAntes: Int? = null,
) {
    /** Texto curto que identifica o lembrete (para não disparar o mesmo duas vezes). */
    val chave get() = "$tipo|$quando|${ocorrenciaId ?: ""}|${minutosAntes ?: ""}"

    companion object {
        fun daChave(chave: String): Lembrete? = runCatching {
            val p = chave.split("|")
            Lembrete(TipoLembrete.valueOf(p[0]), LocalDateTime.parse(p[1]), p[2].ifEmpty { null }, p[3].toIntOrNull())
        }.getOrNull()
    }
}

/** O que mostrar quando um lembrete toca. null = nada a mostrar. */
sealed interface Aviso {
    val silencioso: Boolean
    data class FaltaPouco(val item: Item, val minutosAntes: Int, override val silencioso: Boolean) : Aviso
    data class ResumoDoDia(val itens: List<Item>, override val silencioso: Boolean = false) : Aviso
    data class Pendentes(val itens: List<Item>, override val silencioso: Boolean = false) : Aviso
}

/**
 * Regras dos lembretes, por cima de [Tarefas]. São só contas: cada plataforma decide
 * como tocar (no Android: AlarmManager + notificações).
 */
class Lembretes(private val tarefas: Tarefas) {

    /** Como o prazo de [item] deve tocar agora. */
    fun toque(item: Item, config: Config = tarefas.config()): Toque = when {
        item.tarefa.tipoPrazo == TipoPrazo.DIA_INTEIRO -> Toque.SILENCIOSO
        config.emSilencio(tarefas.agoraLocal().time) && !item.tarefa.tocarNoSilencio -> Toque.SILENCIOSO
        else -> Toque.ALTO
    }

    /**
     * O próximo lembrete depois de [depoisDe] (avisos antes do prazo, resumo do dia, fim do silêncio).
     * Os prazos em si não entram aqui: cada um tem o seu próprio alarme.
     */
    fun proximo(depoisDe: LocalDateTime = tarefas.agoraLocal(), config: Config = tarefas.config()): Lembrete? {
        val candidatos = mutableListOf<Lembrete>()
        for (item in tarefas.abertasAgora()) {
            if (item.status != Status.ATIVA || item.tarefa.tipoPrazo != TipoPrazo.EXATO) continue
            for (min in item.tarefa.avisos) {
                val quando = menosMinutos(item.prazo, min)
                if (quando > depoisDe) candidatos += Lembrete(TipoLembrete.AVISO, quando, item.ocorrencia.id, min)
            }
        }
        if (config.resumoDoDiaAtivo) candidatos += Lembrete(TipoLembrete.RESUMO_DO_DIA, proximaHora(depoisDe, config.resumoDoDia))
        if (config.silencioAtivo && config.silencioInicio != config.silencioFim)
            candidatos += Lembrete(TipoLembrete.FIM_DO_SILENCIO, proximaHora(depoisDe, config.silencioFim))
        return candidatos.minByOrNull { it.quando }
    }

    /** O lembrete tocou: o que mostrar (ou null, se não há nada a dizer). */
    fun disparar(l: Lembrete, config: Config = tarefas.config()): Aviso? {
        val silencio = config.emSilencio(tarefas.agoraLocal().time)
        return when (l.tipo) {
            TipoLembrete.AVISO -> {
                val item = l.ocorrenciaId?.let { tarefas.item(it) } ?: return null
                if (item.status != Status.ATIVA) return null
                Aviso.FaltaPouco(item, l.minutosAntes ?: 0, silencioso = silencio && !item.tarefa.tocarNoSilencio)
            }
            TipoLembrete.RESUMO_DO_DIA -> {
                val hoje = tarefas.agoraLocal().date
                val itens = tarefas.abertasAgora().filter {
                    it.tarefa.tipoPrazo == TipoPrazo.DIA_INTEIRO && it.prazo.date == hoje
                }
                if (itens.isEmpty()) null else Aviso.ResumoDoDia(itens, silencioso = silencio)
            }
            TipoLembrete.FIM_DO_SILENCIO -> {
                val itens = tarefas.abertasAgora().filter { it.status == Status.AGUARDANDO }
                if (itens.isEmpty()) null else Aviso.Pendentes(itens)
            }
        }
    }

    private fun menosMinutos(t: LocalDateTime, min: Int): LocalDateTime {
        val fuso = TimeZone.UTC // conta em minutos "de relógio", sem pular horário de verão
        return t.toInstant(fuso).plus(-min, DateTimeUnit.MINUTE).toLocalDateTime(fuso)
    }

    private fun proximaHora(depoisDe: LocalDateTime, hora: LocalTime): LocalDateTime {
        val hoje = LocalDateTime(depoisDe.date, hora)
        return if (hoje > depoisDe) hoje else LocalDateTime(depoisDe.date.plus(1, DateTimeUnit.DAY), hora)
    }
}
