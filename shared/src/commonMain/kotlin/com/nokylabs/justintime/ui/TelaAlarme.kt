package com.nokylabs.justintime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nokylabs.justintime.dominio.Item
import com.nokylabs.justintime.dominio.Motivo
import com.nokylabs.justintime.dominio.Tarefas
import com.nokylabs.justintime.dominio.formatado
import com.nokylabs.ui.NokyButton
import com.nokylabs.ui.NokyButtonKind
import com.nokylabs.ui.NokyIcons
import com.nokylabs.ui.NokyTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

/**
 * A tela que aparece quando o prazo chega (por cima do bloqueio ou do que estiver aberto).
 * [aoTerminar] fecha a tela depois de qualquer resposta.
 */
@Composable
fun TelaAlarme(tarefas: Tarefas, item: Item, aoTerminar: () -> Unit) {
    val c = NokyTheme.colors
    val t = NokyTheme.type
    var passo by remember { mutableIntStateOf(0) } // 0 = respostas, 1 = motivo, 2 = adiar
    val id = item.ocorrencia.id
    val corDaTarefa = if (item.tarefa.cor != 0) Color(item.tarefa.cor) else c.accent
    val atraso = item.ocorrencia.disparouEm?.let { (it - tarefas.instante(item.prazo).toEpochMilliseconds()) / 1000.0 }

    Box(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(corDaTarefa.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Text(item.tarefa.emoji.ifBlank { "⏰" }, fontSize = 46.sp)
            }
            Spacer(Modifier.height(20.dp))
            Text("O PRAZO CHEGOU", style = t.caps.copy(color = corDaTarefa))
            Spacer(Modifier.height(6.dp))
            Text(item.tarefa.titulo, style = t.h1.copy(fontSize = 30.sp), textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("Prazo: ${Formatos.prazo(item.prazo, tarefas.agoraLocal())}", style = t.secondary)
            if (atraso != null && atraso > 0) Text("Tocou com ${formatarAtraso(atraso)} s de atraso", style = t.muted)
            Spacer(Modifier.height(30.dp))
            Text(when (passo) { 1 -> "Quer dizer o motivo?"; 2 -> "Adiar para quando?"; else -> "Você fez?" }, style = t.h2)
            Spacer(Modifier.height(16.dp))

            val largo = Modifier.fillMaxWidth().height(52.dp)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (passo) {
                    0 -> {
                        NokyButton("Feito", { tarefas.responder(id, feita = true); aoTerminar() }, largo, icon = NokyIcons.Check)
                        NokyButton("Não feito", { passo = 1 }, largo, kind = NokyButtonKind.Danger, icon = NokyIcons.Close)
                        NokyButton("Adiar", { passo = 2 }, largo, kind = NokyButtonKind.Secondary, icon = NokyIcons.Clock)
                    }
                    1 -> {
                        Motivo.entries.chunked(2).forEach { par ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                par.forEach { m ->
                                    NokyButton(m.rotulo, { tarefas.responder(id, feita = false, motivo = m); aoTerminar() },
                                        Modifier.weight(1f).height(48.dp), kind = NokyButtonKind.Soft)
                                }
                            }
                        }
                        NokyButton("Pular", { tarefas.responder(id, feita = false); aoTerminar() }, largo, kind = NokyButtonKind.Ghost)
                    }
                    else -> {
                        val agora = tarefas.agoraLocal()
                        val amanha = LocalDateTime(agora.date.plus(1, DateTimeUnit.DAY), item.prazo.time)
                        NokyButton("Daqui a 15 min", { tarefas.adiarEm(id, 15 * 60); aoTerminar() }, largo, kind = NokyButtonKind.Soft)
                        NokyButton("Daqui a 1 hora", { tarefas.adiarEm(id, 3600); aoTerminar() }, largo, kind = NokyButtonKind.Soft)
                        NokyButton("Amanhã às ${amanha.time.formatado()}", { tarefas.adiar(id, amanha); aoTerminar() }, largo, kind = NokyButtonKind.Soft)
                        NokyButton("Voltar", { passo = 0 }, largo, kind = NokyButtonKind.Ghost)
                    }
                }
            }
        }
    }
}

private fun formatarAtraso(s: Double): String {
    val decimos = (s * 10).toLong()
    return "${decimos / 10},${decimos % 10}"
}
