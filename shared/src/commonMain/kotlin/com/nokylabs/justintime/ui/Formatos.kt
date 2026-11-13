package com.nokylabs.justintime.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nokylabs.justintime.dominio.Tarefas
import com.nokylabs.justintime.dominio.formatado
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** Datas e horas em português, do jeito que a pessoa lê. */
object Formatos {
    private val DIAS = listOf("seg.", "ter.", "qua.", "qui.", "sex.", "sáb.", "dom.")
    private val DIAS_LONGOS = listOf("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo")
    private val MESES = listOf("jan.", "fev.", "mar.", "abr.", "mai.", "jun.", "jul.", "ago.", "set.", "out.", "nov.", "dez.")

    fun dd(n: Int) = n.toString().padStart(2, '0')

    /** "sex., 13/11" */
    fun data(d: LocalDate) = "${DIAS[d.dayOfWeek.ordinal]}, ${dd(d.dayOfMonth)}/${dd(d.monthNumber)}"

    /** "sexta, 13 de nov." (cabeçalho) */
    fun dataLonga(d: LocalDate) = "${DIAS_LONGOS[d.dayOfWeek.ordinal]}, ${d.dayOfMonth} de ${MESES[d.monthNumber - 1]}"

    /** "hoje às 18:00", "amanhã às 08:00", "sáb., 14/11 às 20:00" */
    fun prazo(p: LocalDateTime, agora: LocalDateTime, diaInteiro: Boolean = false): String {
        val dias = agora.date.daysUntil(p.date)
        val dia = when (dias) {
            0 -> "hoje"; 1 -> "amanhã"; -1 -> "ontem"
            else -> data(p.date) + if (p.year != agora.year) "/${p.year}" else ""
        }
        return if (diaInteiro) "$dia (dia inteiro)" else "$dia às ${p.time.formatado()}"
    }

    /**
     * Quanto falta: "02:41:07" (menos de 24 h), "em 3 dias", "venceu".
     */
    fun restante(p: LocalDateTime, agora: LocalDateTime, tarefas: Tarefas): String {
        val ms = tarefas.instante(p).toEpochMilliseconds() - tarefas.instante(agora).toEpochMilliseconds()
        if (ms <= 0) return "venceu"
        val s = ms / 1000
        if (s >= 172_800) return "em ${agora.date.daysUntil(p.date)} dias"
        if (s >= 86_400) return "${s / 3600} h"
        return "${dd((s / 3600).toInt())}:${dd(((s % 3600) / 60).toInt())}:${dd((s % 60).toInt())}"
    }

    fun segundosAte(p: LocalDateTime, agora: LocalDateTime, tarefas: Tarefas): Long =
        (tarefas.instante(p).toEpochMilliseconds() - tarefas.instante(agora).toEpochMilliseconds()) / 1000

    fun proximaHoraCheia(agora: LocalDateTime): LocalDateTime {
        val base = LocalDateTime(agora.date, kotlinx.datetime.LocalTime(agora.hour, 0))
        val mais1 = if (agora.hour == 23) LocalDateTime(agora.date.plus(1, DateTimeUnit.DAY), kotlinx.datetime.LocalTime(0, 0))
        else LocalDateTime(agora.date, kotlinx.datetime.LocalTime(agora.hour + 1, 0))
        return if (base > agora) base else mais1
    }
}

/** "Agora", atualizado a cada segundo (para as contagens regressivas da tela). */
@Composable
fun rememberAgora(tarefas: Tarefas, tique: Boolean = true): LocalDateTime {
    var agora by remember { mutableStateOf(tarefas.agoraLocal()) }
    if (tique) LaunchedEffect(Unit) {
        while (true) {
            delay(1000 - (tarefas.instante(tarefas.agoraLocal()).toEpochMilliseconds() % 1000))
            agora = tarefas.agoraLocal()
        }
    }
    return agora
}
