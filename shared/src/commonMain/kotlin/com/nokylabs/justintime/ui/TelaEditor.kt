package com.nokylabs.justintime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nokylabs.justintime.dominio.Conflito
import com.nokylabs.justintime.dominio.DadosTarefa
import com.nokylabs.justintime.dominio.Horario
import com.nokylabs.justintime.dominio.RegraViolada
import com.nokylabs.justintime.dominio.Repeticao
import com.nokylabs.justintime.dominio.Repeticao.Unidade
import com.nokylabs.justintime.dominio.Tarefas
import com.nokylabs.justintime.dominio.TipoPrazo
import com.nokylabs.justintime.dominio.formatado
import com.nokylabs.justintime.dominio.minutosPorExtenso
import com.nokylabs.ui.NokyButton
import com.nokylabs.ui.NokyButtonKind
import com.nokylabs.ui.NokyCard
import com.nokylabs.ui.NokyChip
import com.nokylabs.ui.NokyIconButton
import com.nokylabs.ui.NokyIcons
import com.nokylabs.ui.NokyNotice
import com.nokylabs.ui.NokyNoticeKind
import com.nokylabs.ui.NokySectionLabel
import com.nokylabs.ui.NokySegmented
import com.nokylabs.ui.NokyTextField
import com.nokylabs.ui.NokyTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/** Cores das tarefas (roxo primeiro) e emojis de atalho. Iguais às do protótipo. */
object Paleta {
    val CORES = listOf(
        0xFF8E5CF7.toInt(), 0xFFC39BFF.toInt(), 0xFF42A5F5.toInt(), 0xFF43C59E.toInt(),
        0xFFFFC94A.toInt(), 0xFFFF8A3D.toInt(), 0xFFF0629B.toInt(), 0xFFEF5350.toInt(),
    )
    val EMOJIS = listOf("📚", "💼", "🏠", "🎂", "💊", "🏋️", "🛒", "🎮", "📞", "✈️", "🧹", "💡", "🎓", "❤️", "🐾", "💰")
}

/** Atalhos de aviso antes do prazo, em minutos (dá para adicionar outros valores). */
private val AVISOS_PRONTOS = listOf(1440, 60, 15)

/** Atalhos de repetição (os mais usados). */
private val PRESETS = listOf(
    "Não repete" to Repeticao.Nenhuma,
    "Todo dia" to Repeticao.Intervalo(1, Unidade.DIA),
    "Toda semana" to Repeticao.Intervalo(1, Unidade.SEMANA),
    "A cada 15 dias" to Repeticao.Intervalo(2, Unidade.SEMANA),
    "Todo mês" to Repeticao.Intervalo(1, Unidade.MES),
    "Todo ano" to Repeticao.Intervalo(1, Unidade.ANO),
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TelaEditor(
    tarefas: Tarefas,
    tarefaId: String?,
    aoFechar: () -> Unit,
    aoEditarOutra: (String) -> Unit,
) {
    val c = NokyTheme.colors
    val t = NokyTheme.type
    // Estado inicial: a tarefa (ao editar) ou uma nova para a próxima hora cheia
    val original = remember(tarefaId) { tarefaId?.let { id -> tarefas.ocorrenciaAtual(id)?.let { tarefas.item(it.id) } } }
    var titulo by remember(tarefaId) { mutableStateOf(original?.tarefa?.titulo ?: "") }
    var emoji by remember(tarefaId) { mutableStateOf(original?.tarefa?.emoji ?: "") }
    var cor by remember(tarefaId) { mutableIntStateOf(original?.tarefa?.cor ?: 0) }
    var tipo by remember(tarefaId) { mutableStateOf(original?.tarefa?.tipoPrazo ?: TipoPrazo.EXATO) }
    var prazo by remember(tarefaId) { mutableStateOf(original?.prazo ?: Formatos.proximaHoraCheia(tarefas.agoraLocal())) }
    var repeticao by remember(tarefaId) { mutableStateOf(original?.tarefa?.repeticao ?: Repeticao.Nenhuma) }
    var avisos by remember(tarefaId) { mutableStateOf(original?.tarefa?.avisos?.toSet() ?: emptySet()) }
    var tocarNoSilencio by remember(tarefaId) { mutableStateOf(original?.tarefa?.tocarNoSilencio ?: false) }
    var erro by remember { mutableStateOf<String?>(null) }
    var conflitos by remember { mutableStateOf<List<Conflito>>(emptyList()) }
    var escolhendoAviso by remember { mutableStateOf(false) }
    var confirmandoExclusao by remember { mutableStateOf(false) }
    var novoHorarioDia by remember { mutableStateOf<DayOfWeek?>(null) }
    val agora = tarefas.agoraLocal()
    val porHorarios = repeticao is Repeticao.Semanal

    fun dados() = DadosTarefa(
        titulo = titulo, emoji = emoji.trim(), cor = cor, tipoPrazo = tipo,
        prazo = if (porHorarios) (repeticao.proximaApos(agora, agora) ?: prazo) else prazo,
        repeticao = repeticao, avisos = avisos.sorted().reversed(), tocarNoSilencio = tocarNoSilencio,
    )

    fun salvar(ignorarConflitos: Boolean) {
        erro = null
        val d = dados()
        if (!ignorarConflitos) {
            val achados = tarefas.conflitos(d.titulo, d.prazoEfetivo, ignorarTarefaId = tarefaId)
            if (achados.isNotEmpty()) { conflitos = achados; return }
        }
        try {
            if (tarefaId == null) tarefas.criar(d) else tarefas.editar(tarefaId, d)
            aoFechar()
        } catch (e: RegraViolada) { erro = e.message }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Barra de cima
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            NokyIconButton(NokyIcons.ChevronLeft, "Voltar", aoFechar)
            Text(if (tarefaId == null) "Nova tarefa" else "Editar tarefa", style = t.h2, modifier = Modifier.padding(start = 14.dp).weight(1f))
            if (tarefaId != null) NokyIconButton(NokyIcons.Trash, "Excluir tarefa", { confirmandoExclusao = true }, tint = c.danger)
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Nome + emoji
            Secao("O que precisa ser feito?") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CampoEmoji(emoji) { emoji = it }
                    NokyTextField(titulo, { titulo = it }, Modifier.weight(1f), placeholder = "Ex.: Entregar as notas")
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Paleta.EMOJIS.forEach { e ->
                        Text(e, fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { emoji = if (emoji == e) "" else e }.padding(6.dp))
                    }
                }
            }

            // Cor
            Secao("Cor (opcional)") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BolinhaCor(0, cor == 0) { cor = 0 }
                    Paleta.CORES.forEach { valor -> BolinhaCor(valor, cor == valor) { cor = valor } }
                }
            }

            // Prazo
            Secao("Prazo de entrega") {
                if (porHorarios) {
                    NokyNotice("O prazo segue os dias e horários da repetição: a primeira vez será ${repeticao.proximaApos(agora, agora)?.let { Formatos.prazo(it, agora) } ?: "—"}.")
                } else {
                    NokySegmented(listOf("Horário exato", "Dia inteiro"), if (tipo == TipoPrazo.EXATO) 0 else 1,
                        { tipo = if (it == 0) TipoPrazo.EXATO else TipoPrazo.DIA_INTEIRO })
                    Spacer(Modifier.height(10.dp))
                    SeletorPrazo(prazo, tipo == TipoPrazo.DIA_INTEIRO, agora) { prazo = it; erro = null }
                    // Avisa na hora (antes de salvar) se o horário escolhido já passou
                    val efetivo = dados().prazoEfetivo
                    val mudou = original == null || efetivo != original.prazo
                    if (mudou) tarefas.problemaNoPrazo(efetivo)?.let {
                        Spacer(Modifier.height(10.dp))
                        NokyNotice(it, NokyNoticeKind.Danger, Modifier.fillMaxWidth())
                    }
                }
            }

            // Repetição
            Secao("Repetição") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { (nome, r) -> NokyChip(nome, repeticao == r, { repeticao = r }) }
                    NokyChip("Personalizado", repeticao is Repeticao.Intervalo && PRESETS.none { it.second == repeticao },
                        { repeticao = Repeticao.Intervalo(3, Unidade.DIA) })
                    NokyChip("Dias e horários", porHorarios, { if (!porHorarios) repeticao = Repeticao.Semanal(emptyList()) })
                }
                val r = repeticao
                if (r is Repeticao.Intervalo && PRESETS.none { it.second == r }) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("A cada", style = t.body)
                        NokyIconButton(NokyIcons.ChevronLeft, "Menos", { if (r.n > 1) repeticao = r.copy(n = r.n - 1) })
                        Text("${r.n}", style = t.h2.merge(t.num), modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                        NokyIconButton(NokyIcons.ChevronRight, "Mais", { if (r.n < 99) repeticao = r.copy(n = r.n + 1) })
                    }
                    Spacer(Modifier.height(8.dp))
                    NokySegmented(Unidade.entries.map { it.plural }, r.unidade.ordinal, { repeticao = r.copy(unidade = Unidade.entries[it]) })
                }
                if (r is Repeticao.Semanal) {
                    Spacer(Modifier.height(10.dp))
                    EditorHorarios(r, { repeticao = it }, aoAdicionarNoDia = { novoHorarioDia = it })
                }
            }

            // Avisos antes do prazo
            Secao("Avisar antes") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Atalhos + os valores personalizados já escolhidos, do maior para o menor
                    (AVISOS_PRONTOS + avisos).distinct().sortedDescending().forEach { min ->
                        NokyChip(minutosPorExtenso(min), min in avisos, {
                            avisos = if (min in avisos) avisos - min else avisos + min
                        })
                    }
                    NokyChip("+ Outro", false, { escolhendoAviso = true })
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (tipo == TipoPrazo.DIA_INTEIRO && !porHorarios) "Em tarefas de dia inteiro os avisos não tocam: o lembrete é o resumo do dia."
                    else if (avisos.isEmpty()) "Nenhum aviso: só o alarme na hora do prazo."
                    else "Avisos: " + avisos.sortedDescending().joinToString(", ") { minutosPorExtenso(it) } + " antes.",
                    style = t.muted,
                )
            }

            // Silêncio
            Secao("Horário silencioso") {
                NokyChip("🔔 Tocar mesmo no horário silencioso", tocarNoSilencio, { tocarNoSilencio = !tocarNoSilencio })
            }

            erro?.let { NokyNotice(it, NokyNoticeKind.Danger, Modifier.fillMaxWidth()) }
            Spacer(Modifier.height(8.dp))
        }

        // Ação principal
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
            NokyButton(if (tarefaId == null) "Agendar tarefa" else "Salvar alterações", { salvar(false) },
                Modifier.fillMaxWidth(), icon = NokyIcons.Check)
        }
    }

    // ---------- Diálogos ----------

    if (conflitos.isNotEmpty()) {
        val criando = tarefaId == null
        val unica = conflitos.singleOrNull()
        AlertDialog(
            onDismissRequest = { conflitos = emptyList() },
            containerColor = c.surface,
            title = { Text("Já existe algo parecido", style = t.h2) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    conflitos.forEach { k ->
                        NokyCard(Modifier.fillMaxWidth()) {
                            Text(k.item.rotulo, style = t.h3)
                            Text("${k.motivo} · ${Formatos.prazo(k.item.prazo, agora)}", style = t.secondary)
                        }
                    }
                    Text(if (criando) "Quer criar mesmo assim?" else "Quer salvar mesmo assim?", style = t.body)
                }
            },
            confirmButton = {
                NokyButton(if (criando) "Criar mesmo assim" else "Salvar mesmo assim", { conflitos = emptyList(); salvar(true) })
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (criando && unica != null)
                        NokyButton("Editar a existente", { conflitos = emptyList(); aoEditarOutra(unica.item.tarefa.id) }, kind = NokyButtonKind.Soft)
                    NokyButton("Ajustar", { conflitos = emptyList() }, kind = NokyButtonKind.Ghost)
                }
            },
        )
    }

    if (confirmandoExclusao && tarefaId != null) {
        AlertDialog(
            onDismissRequest = { confirmandoExclusao = false },
            containerColor = c.surface,
            title = { Text("Excluir a tarefa?", style = t.h2) },
            text = { Text("A tarefa, as repetições e o histórico dela serão apagados de vez.", style = t.secondary) },
            confirmButton = { NokyButton("Apagar de vez", { tarefas.excluir(tarefaId); aoFechar() }, kind = NokyButtonKind.Danger, icon = NokyIcons.Trash) },
            dismissButton = { NokyButton("Cancelar", { confirmandoExclusao = false }, kind = NokyButtonKind.Ghost) },
        )
    }

    novoHorarioDia?.let { dia ->
        DialogoRelogio(LocalTime(9, 0), "${Repeticao.diaLongo(dia)}: que horas?", aoEscolher = { hora ->
            val r = repeticao
            if (r is Repeticao.Semanal) repeticao = r.comHorario(Horario(dia, hora))
        }, aoFechar = { novoHorarioDia = null })
    }

    if (escolhendoAviso) DialogoAvisoPersonalizado({ avisos = avisos + it }, { escolhendoAviso = false })
}

private fun Repeticao.Semanal.comHorario(h: Horario) =
    if (h in horarios) this else copy(horarios = (horarios + h).sortedWith(compareBy({ it.dia.ordinal }, { it.hora })))

/** Lista de "dia + horário" com atalhos Manhã / Tarde / Noite. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorHorarios(r: Repeticao.Semanal, aoMudar: (Repeticao) -> Unit, aoAdicionarNoDia: (DayOfWeek) -> Unit) {
    val t = NokyTheme.type
    var dia by remember { mutableStateOf(DayOfWeek.MONDAY) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        r.horarios.forEach { h ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${Repeticao.diaLongo(h.dia)} às ${h.hora.formatado()}", style = t.body, modifier = Modifier.weight(1f))
                NokyIconButton(NokyIcons.Close, "Remover", { aoMudar(r.copy(horarios = r.horarios - h)) })
            }
        }
        if (r.horarios.isEmpty()) Text("Escolha um dia e um período para adicionar.", style = t.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { d -> NokyChip(Repeticao.diaCurto(d), dia == d, { dia = d }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Manhã (08:00)" to LocalTime(8, 0), "Tarde (14:00)" to LocalTime(14, 0), "Noite (20:00)" to LocalTime(20, 0)).forEach { (nome, hora) ->
                NokyButton("+ $nome", { aoMudar(r.comHorario(Horario(dia, hora))) }, kind = NokyButtonKind.Soft)
            }
            NokyButton("+ Outro horário", { aoAdicionarNoDia(dia) }, kind = NokyButtonKind.Ghost)
        }
    }
}

/** Um bloco do formulário: cartão com o título da seção em cima. */
@Composable
private fun Secao(titulo: String, conteudo: @Composable () -> Unit) {
    NokyCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        NokySectionLabel(titulo)
        conteudo()
    }
}

@Composable
private fun BolinhaCor(valor: Int, marcada: Boolean, aoTocar: () -> Unit) {
    val c = NokyTheme.colors
    Box(
        Modifier.size(32.dp).clip(CircleShape)
            .background(if (valor == 0) Color.Transparent else Color(valor))
            .border(if (marcada) 3.dp else 1.dp, if (marcada) c.text else c.border, CircleShape)
            .clickable(onClick = aoTocar),
        contentAlignment = Alignment.Center,
    ) {
        if (valor == 0) Text("∅", style = NokyTheme.type.secondary)
        else if (marcada) Text("✓", color = Color.White, fontSize = 16.sp)
    }
}

/** Campo pequeno para um emoji (fica só com o primeiro). */
@Composable
private fun CampoEmoji(valor: String, aoMudar: (String) -> Unit) {
    val c = NokyTheme.colors
    Box(
        Modifier.size(46.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(c.surface).border(1.dp, c.border, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            valor, { aoMudar(primeiroGrafema(it)) },
            textStyle = NokyTheme.type.body.copy(fontSize = 22.sp, textAlign = TextAlign.Center),
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        if (valor.isEmpty()) Text("🙂", fontSize = 22.sp, color = c.textMuted.copy(alpha = 0.4f))
    }
}

/**
 * Fica com o primeiro "caractere visível". Simplificado: junta os pedaços de um emoji
 * composto (variações, tons de pele, ZWJ) olhando os códigos.
 */
internal fun primeiroGrafema(s: String): String {
    val texto = s.trim()
    if (texto.isEmpty()) return ""
    val cps = mutableListOf<Int>()
    var i = 0
    while (i < texto.length) {
        val cp = if (texto[i].isHighSurrogate() && i + 1 < texto.length) {
            ((texto[i].code - 0xD800) shl 10) + (texto[i + 1].code - 0xDC00) + 0x10000
        } else texto[i].code
        cps += cp; i += if (cp > 0xFFFF) 2 else 1
    }
    var fim = 1
    while (fim < cps.size) {
        val atual = cps[fim]
        val juntar = atual == 0x200D || atual == 0xFE0F || atual in 0x1F3FB..0x1F3FF || atual == 0x20E3 ||
            cps[fim - 1] == 0x200D
        if (!juntar) break
        fim++
    }
    return buildString { cps.take(fim).forEach { cp ->
        if (cp > 0xFFFF) { val v = cp - 0x10000; append(Char(0xD800 + (v shr 10))); append(Char(0xDC00 + (v and 0x3FF))) }
        else append(Char(cp))
    } }
}

