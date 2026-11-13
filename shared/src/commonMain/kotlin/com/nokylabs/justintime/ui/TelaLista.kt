package com.nokylabs.justintime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nokylabs.justintime.dominio.Item
import com.nokylabs.justintime.dominio.Motivo
import com.nokylabs.justintime.dominio.Status
import com.nokylabs.justintime.dominio.Tarefas
import com.nokylabs.justintime.dominio.TipoPrazo
import com.nokylabs.ui.NokyBadge
import com.nokylabs.ui.NokyBadgeKind
import com.nokylabs.ui.NokyButton
import com.nokylabs.ui.NokyButtonKind
import com.nokylabs.ui.NokyCard
import com.nokylabs.ui.NokyIcons
import com.nokylabs.ui.NokyNotice
import com.nokylabs.ui.NokySectionLabel
import com.nokylabs.ui.NokySegmented
import com.nokylabs.ui.NokyTheme
import com.nokylabs.ui.nokyEntrance
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

private const val UMA_HORA = 3600L
private const val UM_DIA = 86_400L

/** Cores de urgência (iguais às do protótipo): a barra usa a cor da tarefa, se tiver. */
@Composable
fun corDaBarra(item: Item, segundosRestantes: Long): Color {
    val c = NokyTheme.colors
    return when {
        item.tarefa.cor != 0 -> Color(item.tarefa.cor)
        item.status == Status.AGUARDANDO || segundosRestantes <= 0 -> c.accent
        segundosRestantes > UM_DIA -> c.textMuted
        segundosRestantes > UMA_HORA -> c.warning
        else -> c.danger
    }
}

@Composable
fun TelaLista(
    tarefas: Tarefas,
    tique: Boolean,
    avisoDaVersao: String?,
    aoCriar: () -> Unit,
    aoEditar: (tarefaId: String) -> Unit,
) {
    val c = NokyTheme.colors
    val t = NokyTheme.type
    val agora = rememberAgora(tarefas, tique)
    var aba by remember { mutableIntStateOf(0) }
    val abertas by remember { tarefas.abertas() }.collectAsState(tarefas.abertasAgora())
    val historico by remember { tarefas.historico() }.collectAsState(emptyList())

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text("Just in Time", style = t.h1)
                    Text(Formatos.dataLonga(agora.date).replaceFirstChar { it.uppercase() }, style = t.secondary)
                    Spacer(Modifier.height(14.dp))
                    NokySegmented(listOf("Abertas (${abertas.size})", "Histórico"), aba, { aba = it })
                }
            }
            if (avisoDaVersao != null) item { NokyNotice(avisoDaVersao, modifier = Modifier.fillMaxWidth()) }

            if (aba == 0) {
                if (abertas.isEmpty()) item { Vazio("Nenhuma tarefa aberta", "Toque em \"Nova tarefa\" para começar.") }
                val aguardando = abertas.filter { it.status == Status.AGUARDANDO }
                val ativas = abertas.filter { it.status == Status.ATIVA }
                if (aguardando.isNotEmpty()) {
                    item { NokySectionLabel("Aguardando resposta") }
                    items(aguardando, key = { it.ocorrencia.id }) { item ->
                        CartaoAguardando(item, agora, tarefas, Modifier.nokyEntrance(0))
                    }
                }
                ativas.firstOrNull()?.let { proxima ->
                    item { NokySectionLabel("Próxima", Modifier.padding(top = 6.dp)) }
                    item(key = proxima.ocorrencia.id) { CartaoProxima(proxima, agora, tarefas, { aoEditar(proxima.tarefa.id) }, Modifier.nokyEntrance(1)) }
                }
                val depois = ativas.drop(1)
                if (depois.isNotEmpty()) {
                    item { NokySectionLabel("Depois", Modifier.padding(top = 6.dp)) }
                    items(depois, key = { it.ocorrencia.id }) { item ->
                        LinhaTarefa(item, agora, tarefas, { aoEditar(item.tarefa.id) }, Modifier.nokyEntrance(2).padding(start = 12.dp))
                    }
                }
            } else {
                if (historico.isEmpty()) item { Vazio("Nada no histórico ainda", "As tarefas respondidas aparecem aqui.") }
                items(historico, key = { it.ocorrencia.id }) { item -> LinhaHistorico(item, agora) }
            }
        }

        // Ação principal da tela (uma só, como manda o Kit)
        NokyButton(
            "Nova tarefa", aoCriar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
            icon = NokyIcons.Plus,
        )
    }
}

@Composable
private fun Vazio(titulo: String, texto: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⏳", fontSize = 40.sp)
        Spacer(Modifier.height(10.dp))
        Text(titulo, style = NokyTheme.type.h3)
        Text(texto, style = NokyTheme.type.secondary)
    }
}

@Composable
private fun Barra(cor: Color, altura: Int) =
    Box(Modifier.width(5.dp).height(altura.dp).clip(RoundedCornerShape(50)).background(cor))

/** A próxima tarefa, em destaque: contagem grande na cor do app. */
@Composable
private fun CartaoProxima(item: Item, agora: LocalDateTime, tarefas: Tarefas, aoTocar: () -> Unit, modifier: Modifier) {
    val t = NokyTheme.type
    val c = NokyTheme.colors
    val seg = Formatos.segundosAte(item.prazo, agora, tarefas)
    NokyCard(modifier.fillMaxWidth(), onClick = aoTocar) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Barra(corDaBarra(item, seg), 46)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(item.rotulo, style = t.h2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    Formatos.prazo(item.prazo, agora, item.tarefa.tipoPrazo == TipoPrazo.DIA_INTEIRO) +
                        if (item.tarefa.repeticao.repete) " · 🔁 ${item.tarefa.repeticao.descricao()}" else "",
                    style = t.secondary,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                Formatos.restante(item.prazo, agora, tarefas),
                style = t.h1.copy(fontSize = 40.sp, color = if (seg in 1..UMA_HORA) c.danger else c.accentText).merge(t.num),
                modifier = Modifier.weight(1f),
            )
            if (item.ocorrencia.adiamentos > 0) NokyBadge("adiada ${item.ocorrencia.adiamentos}x", NokyBadgeKind.Neutral)
        }
    }
}

/** As tarefas seguintes: menores e recuadas (o destaque é da próxima). */
@Composable
private fun LinhaTarefa(item: Item, agora: LocalDateTime, tarefas: Tarefas, aoTocar: () -> Unit, modifier: Modifier) {
    val t = NokyTheme.type
    val seg = Formatos.segundosAte(item.prazo, agora, tarefas)
    NokyCard(modifier.fillMaxWidth(), onClick = aoTocar, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Barra(corDaBarra(item, seg), 30)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(item.rotulo, style = t.h3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Formatos.prazo(item.prazo, agora, item.tarefa.tipoPrazo == TipoPrazo.DIA_INTEIRO), style = t.muted)
            }
            Text(Formatos.restante(item.prazo, agora, tarefas), style = t.body.merge(t.num).copy(color = NokyTheme.colors.textSecondary))
        }
    }
}

/** Prazo chegou: a pessoa responde aqui mesmo. */
@Composable
private fun CartaoAguardando(item: Item, agora: LocalDateTime, tarefas: Tarefas, modifier: Modifier) {
    val t = NokyTheme.type
    var escolhendo by remember { mutableIntStateOf(0) } // 0 = respostas, 1 = motivos, 2 = adiar
    NokyCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Barra(corDaBarra(item, 0), 40)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(item.rotulo, style = t.h3)
                Text("O prazo era ${Formatos.prazo(item.prazo, agora, item.tarefa.tipoPrazo == TipoPrazo.DIA_INTEIRO)}. Você fez?", style = t.secondary)
            }
        }
        Spacer(Modifier.height(12.dp))
        val id = item.ocorrencia.id
        when (escolhendo) {
            0 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NokyButton("Feito", { tarefas.responder(id, feita = true) }, icon = NokyIcons.Check)
                NokyButton("Não feito", { escolhendo = 1 }, kind = NokyButtonKind.Danger)
                NokyButton("Adiar", { escolhendo = 2 }, kind = NokyButtonKind.Secondary)
            }
            1 -> Column {
                Text("Quer dizer o motivo? (opcional)", style = t.muted)
                Spacer(Modifier.height(6.dp))
                FlowBotoes {
                    Motivo.entries.forEach { m -> NokyButton(m.rotulo, { tarefas.responder(id, feita = false, motivo = m) }, kind = NokyButtonKind.Soft) }
                    NokyButton("Pular", { tarefas.responder(id, feita = false) }, kind = NokyButtonKind.Ghost)
                }
            }
            else -> {
                val base = tarefas.agoraLocal()
                FlowBotoes {
                    NokyButton("+15 min", { tarefas.adiarEm(id, 15 * 60) }, kind = NokyButtonKind.Soft)
                    NokyButton("+1 h", { tarefas.adiarEm(id, 3600) }, kind = NokyButtonKind.Soft)
                    NokyButton("Amanhã", { tarefas.adiar(id, LocalDateTime(base.date.plus(1, DateTimeUnit.DAY), item.prazo.time)) }, kind = NokyButtonKind.Soft)
                    NokyButton("Voltar", { escolhendo = 0 }, kind = NokyButtonKind.Ghost)
                }
            }
        }
    }
}

@Composable
private fun LinhaHistorico(item: Item, agora: LocalDateTime) {
    val t = NokyTheme.type
    val feita = item.status == Status.FEITA
    NokyCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.rotulo, style = t.h3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Formatos.prazo(item.prazo, agora) + (item.ocorrencia.motivo?.let { " · ${it.rotulo}" } ?: ""), style = t.muted)
            }
            NokyBadge(if (feita) "Feita" else "Não feita", if (feita) NokyBadgeKind.Success else NokyBadgeKind.Danger)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowBotoes(conteudo: @Composable () -> Unit) =
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { conteudo() }
