package com.nokylabs.justintime.prototipo

import java.util.Calendar

/** Um horário fixo da semana: "segunda às 08:00". diaSemana usa Calendar.SUNDAY..SATURDAY. */
data class Horario(val diaSemana: Int, val hora: Int, val minuto: Int) {
    fun descricao() = "${Repeticao.DIAS_CURTOS[diaSemana - 1]} %02d:%02d".format(hora, minuto)
}

/**
 * Regra de repetição de uma tarefa. Três tipos:
 *  - NENHUMA
 *  - INTERVALO: a cada N dias / semanas / meses / anos (quinzenal = 2 semanas, aniversário = 1 ano)
 *  - SEMANAL: dias e horários específicos ("seg 08:00, qui 20:00, sáb 14:00")
 *
 * Código Kotlin puro (sem Android), para poder ser testado no computador.
 * As próximas datas são sempre calculadas a partir da ÂNCORA (o primeiro prazo), como um
 * despertador: adiar uma ocorrência não empurra as seguintes, e "todo dia 31" não vira
 * "todo dia 30" depois de passar por um mês curto.
 */
data class Repeticao(
    val tipo: Tipo = Tipo.NENHUMA,
    val n: Int = 1,
    val unidade: Unidade = Unidade.DIA,
    val horarios: List<Horario> = emptyList(),
) {
    enum class Tipo { NENHUMA, INTERVALO, SEMANAL }
    enum class Unidade(val campo: Int, val singular: String, val plural: String) {
        DIA(Calendar.DAY_OF_MONTH, "dia", "dias"),
        SEMANA(Calendar.WEEK_OF_YEAR, "semana", "semanas"),
        MES(Calendar.MONTH, "mês", "meses"),
        ANO(Calendar.YEAR, "ano", "anos"),
    }

    val repete get() = tipo != Tipo.NENHUMA

    fun descricao(): String = when (tipo) {
        Tipo.NENHUMA -> "Não repete"
        Tipo.INTERVALO -> when {
            n == 1 && unidade == Unidade.DIA -> "Todo dia"
            n == 1 && unidade == Unidade.SEMANA -> "Toda semana"
            n == 2 && unidade == Unidade.SEMANA -> "A cada 15 dias"
            n == 1 && unidade == Unidade.MES -> "Todo mês"
            n == 1 && unidade == Unidade.ANO -> "Todo ano"
            else -> "A cada $n ${if (n == 1) unidade.singular else unidade.plural}"
        }
        Tipo.SEMANAL -> horariosOrdenados().joinToString(", ") { it.descricao() }.ifEmpty { "Nenhum horário" }
    }

    fun horariosOrdenados() = horarios.sortedWith(compareBy({ (it.diaSemana + 5) % 7 }, { it.hora }, { it.minuto }))

    /**
     * O primeiro prazo depois de [depoisDe].
     * @param ancora o prazo original da série (para INTERVALO).
     * @return null quando não repete (ou SEMANAL sem horários).
     */
    fun proximaApos(ancora: Long, depoisDe: Long): Long? = when (tipo) {
        Tipo.NENHUMA -> null
        Tipo.INTERVALO -> proximaIntervalo(ancora, depoisDe)
        Tipo.SEMANAL -> proximaSemanal(depoisDe)
    }

    private fun proximaIntervalo(ancora: Long, depoisDe: Long): Long? {
        if (n < 1) return null
        if (ancora > depoisDe) return ancora
        // Salto inicial para não percorrer anos de repetições uma a uma. Usa a duração MÁXIMA
        // de cada unidade (dia de 25 h, mês de 31 dias, ano de 366), então nunca passa do ponto.
        val passoAproxMs = n * when (unidade) {
            Unidade.DIA -> 25 * 3_600_000L
            Unidade.SEMANA -> 7 * 25 * 3_600_000L
            Unidade.MES -> 31 * 25 * 3_600_000L
            Unidade.ANO -> 366 * 25 * 3_600_000L
        }
        var k = maxOf(1L, (depoisDe - ancora) / passoAproxMs - 2)
        while (k < 1_000_000) {
            val c = Calendar.getInstance().apply {
                timeInMillis = ancora
                add(unidade.campo, (k * n).toInt()) // sempre a partir da âncora: sem "deriva"
            }
            if (c.timeInMillis > depoisDe) return c.timeInMillis
            k++
        }
        return null
    }

    private fun proximaSemanal(depoisDe: Long): Long? {
        if (horarios.isEmpty()) return null
        var melhor: Long? = null
        for (offset in 0..7) {
            for (h in horarios) {
                val c = Calendar.getInstance().apply {
                    timeInMillis = depoisDe
                    add(Calendar.DAY_OF_MONTH, offset)
                    set(Calendar.HOUR_OF_DAY, h.hora); set(Calendar.MINUTE, h.minuto)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                if (c.get(Calendar.DAY_OF_WEEK) != h.diaSemana) continue
                val t = c.timeInMillis
                if (t > depoisDe && (melhor == null || t < melhor)) melhor = t
            }
            if (melhor != null) return melhor
        }
        return melhor
    }

    /** Formato de texto simples para guardar: "INTERVALO|2|SEMANA" ou "SEMANAL|2-8-0;5-20-0". */
    fun codificar(): String = when (tipo) {
        Tipo.NENHUMA -> ""
        Tipo.INTERVALO -> "INTERVALO|$n|${unidade.name}"
        Tipo.SEMANAL -> "SEMANAL|" + horarios.joinToString(";") { "${it.diaSemana}-${it.hora}-${it.minuto}" }
    }

    companion object {
        val DIAS_CURTOS = listOf("dom", "seg", "ter", "qua", "qui", "sex", "sáb")
        val DIAS_LONGOS = listOf("Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado")

        fun decodificar(s: String?): Repeticao {
            if (s.isNullOrBlank()) return Repeticao()
            return try {
                val p = s.split("|")
                when (p[0]) {
                    "INTERVALO" -> Repeticao(Tipo.INTERVALO, p[1].toInt(), Unidade.valueOf(p[2]))
                    "SEMANAL" -> Repeticao(Tipo.SEMANAL, horarios = p.getOrElse(1) { "" }
                        .split(";").filter { it.isNotBlank() }
                        .map { h -> h.split("-").let { Horario(it[0].toInt(), it[1].toInt(), it[2].toInt()) } })
                    else -> Repeticao()
                }
            } catch (_: Exception) { Repeticao() }
        }
    }
}
