package com.nokylabs.justintime.dominio

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/** Um horário fixo da semana: "segunda às 08:00". */
data class Horario(val dia: DayOfWeek, val hora: LocalTime) {
    fun descricao() = "${Repeticao.diaCurto(dia)} ${hora.formatado()}"
}

/**
 * Regra de repetição de uma tarefa. Três tipos:
 *  - Nenhuma
 *  - Intervalo: a cada N dias / semanas / meses / anos (quinzenal = 2 semanas, aniversário = 1 ano)
 *  - Semanal: dias e horários específicos ("seg 08:00, qui 20:00, sáb 14:00")
 *
 * Tudo em **hora local**, como um despertador: 18:00 continua 18:00 se o fuso mudar.
 * As próximas datas saem sempre da ÂNCORA (o primeiro prazo): adiar uma ocorrência não
 * empurra as seguintes, e "todo dia 31" volta a ser 31 depois de passar por fevereiro.
 */
sealed interface Repeticao {
    data object Nenhuma : Repeticao
    data class Intervalo(val n: Int, val unidade: Unidade) : Repeticao
    data class Semanal(val horarios: List<Horario>) : Repeticao

    enum class Unidade(val singular: String, val plural: String) {
        DIA("dia", "dias"), SEMANA("semana", "semanas"), MES("mês", "meses"), ANO("ano", "anos")
    }

    val repete get() = this != Nenhuma

    fun descricao(): String = when (this) {
        Nenhuma -> "Não repete"
        is Intervalo -> when {
            n == 1 && unidade == Unidade.DIA -> "Todo dia"
            n == 1 && unidade == Unidade.SEMANA -> "Toda semana"
            n == 2 && unidade == Unidade.SEMANA -> "A cada 15 dias"
            n == 1 && unidade == Unidade.MES -> "Todo mês"
            n == 1 && unidade == Unidade.ANO -> "Todo ano"
            else -> "A cada $n ${if (n == 1) unidade.singular else unidade.plural}"
        }
        is Semanal -> ordenados().joinToString(", ") { it.descricao() }.ifEmpty { "Nenhum horário" }
    }

    /**
     * O primeiro prazo depois de [depoisDe].
     * @param ancora o primeiro prazo da série (usado por Intervalo).
     * @return null quando não repete.
     */
    fun proximaApos(ancora: LocalDateTime, depoisDe: LocalDateTime): LocalDateTime? = when (this) {
        Nenhuma -> null
        is Intervalo -> proximaIntervalo(ancora, depoisDe)
        is Semanal -> proximaSemanal(depoisDe)
    }

    /** "INTERVALO|2|SEMANA" ou "SEMANAL|MONDAY-08:00;THURSDAY-20:00" (vazio = não repete). */
    fun codificar(): String = when (this) {
        Nenhuma -> ""
        is Intervalo -> "INTERVALO|$n|${unidade.name}"
        is Semanal -> "SEMANAL|" + horarios.joinToString(";") { "${it.dia.name}-${it.hora.formatado()}" }
    }

    companion object {
        private val DIAS_CURTOS = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")
        private val DIAS_LONGOS = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")
        fun diaCurto(d: DayOfWeek) = DIAS_CURTOS[d.ordinal]
        fun diaLongo(d: DayOfWeek) = DIAS_LONGOS[d.ordinal]

        fun decodificar(s: String?): Repeticao {
            if (s.isNullOrBlank()) return Nenhuma
            return runCatching {
                val p = s.split("|")
                when (p[0]) {
                    "INTERVALO" -> Intervalo(p[1].toInt(), Unidade.valueOf(p[2]))
                    "SEMANAL" -> Semanal(p.getOrElse(1) { "" }.split(";").filter { it.isNotBlank() }.map { h ->
                        val (dia, hora) = h.split("-")
                        Horario(DayOfWeek.valueOf(dia), LocalTime.parse(hora))
                    })
                    else -> Nenhuma
                }
            }.getOrDefault(Nenhuma)
        }
    }
}

private fun Repeticao.Semanal.ordenados() = horarios.sortedWith(compareBy({ it.dia.ordinal }, { it.hora }))

private fun Repeticao.Intervalo.proximaIntervalo(ancora: LocalDateTime, depoisDe: LocalDateTime): LocalDateTime? {
    if (n < 1) return null
    if (ancora > depoisDe) return ancora
    // Salto inicial para não percorrer anos de repetições uma a uma. Subestima de propósito
    // (mês de 31 dias, ano de 366), então nunca passa do ponto.
    val diasPassados = (depoisDe.date.toEpochDays() - ancora.date.toEpochDays()).toLong()
    val diasPorPasso = n.toLong() * when (unidade) {
        Repeticao.Unidade.DIA -> 1; Repeticao.Unidade.SEMANA -> 7; Repeticao.Unidade.MES -> 31; Repeticao.Unidade.ANO -> 366
    }
    var k = maxOf(1L, diasPassados / diasPorPasso - 1)
    while (k < 1_000_000) {
        val passos = (k * n).toInt()
        val data = when (unidade) {
            Repeticao.Unidade.DIA -> ancora.date.plus(passos, DateTimeUnit.DAY)
            Repeticao.Unidade.SEMANA -> ancora.date.plus(passos * 7, DateTimeUnit.DAY)
            Repeticao.Unidade.MES -> ancora.date.plus(passos, DateTimeUnit.MONTH) // 31/01 + 1 mês = 28/02
            Repeticao.Unidade.ANO -> ancora.date.plus(passos * 12, DateTimeUnit.MONTH)
        }
        val candidata = LocalDateTime(data, ancora.time)
        if (candidata > depoisDe) return candidata
        k++
    }
    return null
}

private fun Repeticao.Semanal.proximaSemanal(depoisDe: LocalDateTime): LocalDateTime? {
    if (horarios.isEmpty()) return null
    for (offset in 0..7) {
        val data = depoisDe.date.plus(offset, DateTimeUnit.DAY)
        val candidata = horarios.filter { it.dia == data.dayOfWeek }
            .map { LocalDateTime(data, it.hora) }
            .filter { it > depoisDe }
            .minOrNull()
        if (candidata != null) return candidata
    }
    return null
}

/** "08:00" */
fun LocalTime.formatado(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
