package com.nokylabs.justintime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nokylabs.justintime.dominio.Item
import com.nokylabs.justintime.dominio.RegraViolada
import com.nokylabs.justintime.dominio.Tarefas
import com.nokylabs.justintime.dominio.TipoPrazo
import com.nokylabs.justintime.dominio.formatado
import com.nokylabs.justintime.dominio.minutosPorExtenso
import com.nokylabs.ui.NokyButton
import com.nokylabs.ui.NokyButtonKind
import com.nokylabs.ui.NokyIcons
import com.nokylabs.ui.NokyNotice
import com.nokylabs.ui.NokyNoticeKind
import com.nokylabs.ui.NokySegmented
import com.nokylabs.ui.NokyTextField
import com.nokylabs.ui.NokyTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/** Calendário que não deixa escolher dias que já passaram. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoData(inicial: LocalDate, hoje: LocalDate, aoEscolher: (LocalDate) -> Unit, aoFechar: () -> Unit) {
    val hojeMs = hoje.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = maxOf(inicial, hoje).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= hojeMs
            override fun isSelectableYear(year: Int) = year >= hoje.year
        },
    )
    DatePickerDialog(
        onDismissRequest = aoFechar,
        confirmButton = {
            NokyButton("OK", {
                estado.selectedDateMillis?.let { ms -> aoEscolher(Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date) }
                aoFechar()
            })
        },
        dismissButton = { NokyButton("Cancelar", aoFechar, kind = NokyButtonKind.Ghost) },
    ) { DatePicker(estado) }
}

/** Relógio de 24 h num diálogo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoRelogio(inicial: LocalTime, titulo: String = "Que horas?", aoEscolher: (LocalTime) -> Unit, aoFechar: () -> Unit) {
    val estado = rememberTimePickerState(inicial.hour, inicial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = aoFechar,
        containerColor = NokyTheme.colors.surface,
        title = { Text(titulo, style = NokyTheme.type.h2) },
        text = { TimePicker(estado) },
        confirmButton = { NokyButton("OK", { aoEscolher(LocalTime(estado.hour, estado.minute)); aoFechar() }) },
        dismissButton = { NokyButton("Cancelar", aoFechar, kind = NokyButtonKind.Ghost) },
    )
}

/** Botões "📅 data" e "🕒 hora" que abrem os diálogos acima. */
@Composable
fun SeletorPrazo(prazo: LocalDateTime, diaInteiro: Boolean, agora: LocalDateTime, aoMudar: (LocalDateTime) -> Unit) {
    var data by remember { mutableStateOf(false) }
    var hora by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NokyButton("📅  " + Formatos.data(prazo.date) + if (prazo.year != agora.year) "/${prazo.year}" else "",
            { data = true }, kind = NokyButtonKind.Secondary)
        if (!diaInteiro) NokyButton("🕒  " + prazo.time.formatado(), { hora = true }, kind = NokyButtonKind.Secondary)
        else Text("até 23:59", style = NokyTheme.type.secondary, modifier = Modifier.align(Alignment.CenterVertically))
    }
    if (data) DialogoData(prazo.date, agora.date, { aoMudar(LocalDateTime(it, prazo.time)) }, { data = false })
    if (hora) DialogoRelogio(prazo.time, aoEscolher = { aoMudar(LocalDateTime(prazo.date, it)) }, aoFechar = { hora = false })
}

/**
 * "Ajustar": muda o prazo desta vez para antes ou depois do marcado.
 * O botão Salvar só libera com um horário que ainda não passou.
 */
@Composable
fun DialogoAjustar(item: Item, tarefas: Tarefas, aoFechar: () -> Unit) {
    val t = NokyTheme.type
    val diaInteiro = item.tarefa.tipoPrazo == TipoPrazo.DIA_INTEIRO
    var prazo by remember(item.ocorrencia.id) { mutableStateOf(item.prazo) }
    val agora = rememberAgora(tarefas, tique = false)
    val efetivo = if (diaInteiro) LocalDateTime(prazo.date, LocalTime(23, 59)) else prazo
    val problema = tarefas.problemaNoPrazo(efetivo)
    var erro by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = aoFechar,
        containerColor = NokyTheme.colors.surface,
        title = { Text("Ajustar o prazo", style = t.h2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.rotulo, style = t.h3)
                Text("Está marcado para ${Formatos.prazo(item.prazo, agora, diaInteiro)}. Escolha o novo dia e horário, antes ou depois.", style = t.secondary)
                SeletorPrazo(prazo, diaInteiro, agora) { prazo = it; erro = null }
                when {
                    problema != null -> NokyNotice(problema, NokyNoticeKind.Danger, Modifier.fillMaxWidth())
                    efetivo != item.prazo -> Text(
                        (if (efetivo < item.prazo) "⏪ Fica mais cedo: " else "⏩ Fica mais tarde: ") + Formatos.prazo(efetivo, agora, diaInteiro),
                        style = t.body,
                    )
                }
                if (item.tarefa.repeticao.repete) Text("Só esta vez. As próximas repetições continuam no horário de sempre.", style = t.muted)
                erro?.let { NokyNotice(it, NokyNoticeKind.Danger, Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            NokyButton("Salvar", {
                try { tarefas.ajustar(item.ocorrencia.id, efetivo); aoFechar() }
                catch (e: RegraViolada) { erro = e.message }
            }, enabled = problema == null && efetivo != item.prazo, icon = NokyIcons.Check)
        },
        dismissButton = { NokyButton("Cancelar", aoFechar, kind = NokyButtonKind.Ghost) },
    )
}

/** Aviso antes do prazo com valor livre: "30 min", "2 h", "3 dias". */
@Composable
fun DialogoAvisoPersonalizado(aoEscolher: (Int) -> Unit, aoFechar: () -> Unit) {
    val t = NokyTheme.type
    var texto by remember { mutableStateOf("30") }
    var unidade by remember { mutableIntStateOf(0) } // 0 = min, 1 = h, 2 = dias
    val fator = listOf(1, 60, 1440)[unidade]
    val n = texto.trim().toIntOrNull()
    val minutos = n?.let { it * fator }
    val problema = when {
        n == null || n <= 0 -> "Digite um número maior que zero."
        minutos!! > 30 * 1440 -> "O máximo é 30 dias antes."
        else -> null
    }
    AlertDialog(
        onDismissRequest = aoFechar,
        containerColor = NokyTheme.colors.surface,
        title = { Text("Avisar quanto tempo antes?", style = t.h2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NokyTextField(texto, { novo -> texto = novo.filter { it.isDigit() }.take(4) }, Modifier.width(120.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                NokySegmented(listOf("minutos", "horas", "dias"), unidade, { unidade = it })
                if (problema != null) Text(problema, style = t.muted)
                else Text("Vou avisar ${minutosPorExtenso(minutos!!)} antes do prazo.", style = t.secondary)
            }
        },
        confirmButton = { NokyButton("Adicionar", { aoEscolher(minutos!!); aoFechar() }, enabled = problema == null, icon = NokyIcons.Plus) },
        dismissButton = { NokyButton("Cancelar", aoFechar, kind = NokyButtonKind.Ghost) },
    )
}
