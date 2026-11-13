package com.nokylabs.justintime.dominio

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Hoje ou esta semana (segunda a domingo). */
enum class Periodo(val rotulo: String) { DIA("Hoje"), SEMANA("Semana") }

/**
 * O contador de entregas (ideia da Taynara): quanto foi entregue e quanto não foi no período,
 * quanto ainda vem pela frente e a sequência de dias sem nenhuma tarefa não entregue.
 *
 * Conta pelo PRAZO da ocorrência (o atual, já com adiamentos): "Feita" é entregue, mesmo depois de
 * adiar; "Não feita" é não entregue; ativa ou aguardando resposta é "pela frente".
 */
data class Placar(
    val periodo: Periodo,
    val inicio: LocalDate,
    /** Dia seguinte ao último dia do período. */
    val fim: LocalDate,
    val entregues: Int,
    val naoEntregues: Int,
    val pelaFrente: Int,
    /** Dias seguidos (até hoje) com entregas e nenhuma tarefa não entregue. */
    val sequencia: Int,
) {
    val respondidas get() = entregues + naoEntregues
    val total get() = respondidas + pelaFrente
    /** Entregues sobre respondidas (null se nada foi respondido ainda). */
    val taxa: Double? get() = if (respondidas == 0) null else entregues.toDouble() / respondidas
}

/** Primeiro e "depois do último" dia do período que contém [hoje]. */
fun Periodo.intervalo(hoje: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
    Periodo.DIA -> hoje to hoje.plus(1, DateTimeUnit.DAY)
    Periodo.SEMANA -> {
        val segunda = hoje.minus(hoje.dayOfWeek.ordinal, DateTimeUnit.DAY) // MONDAY.ordinal = 0
        segunda to segunda.plus(7, DateTimeUnit.DAY)
    }
}

/**
 * Sequência de dias: anda para trás a partir de hoje. Dia com alguma "não feita" interrompe;
 * dia com entregas soma 1; dia sem nada respondido não soma nem interrompe (fim de semana livre não
 * quebra a sequência). Hoje só entra se já tiver entrega.
 */
fun calcularSequencia(hoje: LocalDate, respondidasPorDia: Map<LocalDate, List<Status>>, limiteDias: Int = 400): Int {
    var dias = 0
    var d = hoje
    repeat(limiteDias) {
        val doDia = respondidasPorDia[d].orEmpty()
        if (Status.NAO_FEITA in doDia) return dias
        if (Status.FEITA in doDia) dias++
        d = d.minus(1, DateTimeUnit.DAY)
    }
    return dias
}

private val MEIA_NOITE = LocalTime(0, 0)
internal fun LocalDate.comeco() = LocalDateTime(this, MEIA_NOITE)
