package com.nokylabs.justintime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nokylabs.justintime.dominio.Config
import com.nokylabs.justintime.dominio.DadosTarefa
import com.nokylabs.justintime.dominio.Tarefas
import com.nokylabs.justintime.dominio.formatado
import com.nokylabs.ui.NokyButton
import com.nokylabs.ui.NokyButtonKind
import com.nokylabs.ui.NokyCard
import com.nokylabs.ui.NokyChip
import com.nokylabs.ui.NokyCreditsCard
import com.nokylabs.ui.NokyIconButton
import com.nokylabs.ui.NokyIcons
import com.nokylabs.ui.NokyNotice
import com.nokylabs.ui.NokyNoticeKind
import com.nokylabs.ui.NokySectionLabel
import com.nokylabs.ui.NokyTheme
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaConfiguracoes(
    tarefas: Tarefas,
    plataforma: Plataforma,
    atualizacao: Int,
    aoFechar: () -> Unit,
) {
    val c = NokyTheme.colors
    val t = NokyTheme.type
    var config by remember { mutableStateOf(tarefas.config()) }
    var editandoHora by remember { mutableStateOf<String?>(null) } // "inicio", "fim", "resumo"
    var mensagem by remember { mutableStateOf<String?>(null) }
    fun salvar(nova: Config) { config = nova; tarefas.salvarConfig(nova) }
    // `atualizacao` muda quando a pessoa volta das configurações do sistema: relê as permissões
    val permissoes = remember(atualizacao) { plataforma.permissoes() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            NokyIconButton(NokyIcons.ChevronLeft, "Voltar", aoFechar)
            Text("Configurações", style = t.h2, modifier = Modifier.padding(start = 14.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // Permissões
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NokySectionLabel("Permissões")
                val faltando = permissoes.count { !it.ok }
                if (faltando > 0) NokyNotice(
                    "Falta${if (faltando > 1) "m" else ""} $faltando permiss${if (faltando > 1) "ões" else "ão"} para os alarmes funcionarem do jeito certo.",
                    NokyNoticeKind.Warning, Modifier.fillMaxWidth(),
                )
                NokyCard(Modifier.fillMaxWidth()) {
                    permissoes.forEachIndexed { i, p ->
                        if (i > 0) Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (p.ok) NokyIcons.Check else NokyIcons.Alert, null, Modifier.size(20.dp),
                                tint = if (p.ok) c.success else c.warning,
                            )
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(p.tipo.nome, style = t.h3)
                                Text(p.tipo.explicacao, style = t.muted)
                            }
                            if (!p.ok) NokyButton("Liberar", { plataforma.liberar(p.tipo) }, kind = NokyButtonKind.Soft)
                        }
                    }
                }
            }

            // Horário silencioso
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NokySectionLabel("Horário silencioso")
                Text("Nesse horário, alarmes e avisos chegam sem som (menos as tarefas marcadas com \"tocar mesmo no silêncio\"). " +
                    "Quando ele acaba, o app lembra o que ficou pendente.", style = t.secondary)
                NokyChip(if (config.silencioAtivo) "Ligado" else "Desligado", config.silencioAtivo,
                    { salvar(config.copy(silencioAtivo = !config.silencioAtivo)) })
                if (config.silencioAtivo) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Das", style = t.body)
                    NokyButton(config.silencioInicio.formatado(), { editandoHora = "inicio" }, kind = NokyButtonKind.Secondary)
                    Text("às", style = t.body)
                    NokyButton(config.silencioFim.formatado(), { editandoHora = "fim" }, kind = NokyButtonKind.Secondary)
                }
            }

            // Resumo do dia
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NokySectionLabel("Resumo do dia")
                Text("Lembra das tarefas de dia inteiro que ainda estão abertas, antes do dia acabar.", style = t.secondary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NokyChip(if (config.resumoDoDiaAtivo) "Ligado" else "Desligado", config.resumoDoDiaAtivo,
                        { salvar(config.copy(resumoDoDiaAtivo = !config.resumoDoDiaAtivo)) })
                    if (config.resumoDoDiaAtivo) {
                        Text("às", style = t.body)
                        NokyButton(config.resumoDoDia.formatado(), { editandoHora = "resumo" }, kind = NokyButtonKind.Secondary)
                    }
                }
            }

            // Diagnóstico
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NokySectionLabel("Diagnóstico")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NokyButton("Testar alarme (30 s)", {
                        tarefas.criar(DadosTarefa("Teste de alarme", "🔔", prazo = tarefas.daquiA(30)))
                        mensagem = "Alarme de teste em 30 segundos. Pode bloquear o celular!"
                    }, kind = NokyButtonKind.Soft, icon = NokyIcons.Clock)
                    NokyButton("Reportar bug", { plataforma.reportar(montarRelatorio(plataforma, tarefas)) },
                        kind = NokyButtonKind.Secondary, icon = NokyIcons.External)
                    NokyButton("Simular erro", { plataforma.simularErro() }, kind = NokyButtonKind.Ghost)
                }
                mensagem?.let { NokyNotice(it, modifier = Modifier.fillMaxWidth()) }
            }

            // Sobre
            Column {
                NokySectionLabel("Sobre")
                NokyCreditsCard("Just in Time", "v${plataforma.versao} · ${plataforma.aparelho}", Modifier.width(260.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editandoHora?.let { qual ->
        val inicial = when (qual) { "inicio" -> config.silencioInicio; "fim" -> config.silencioFim; else -> config.resumoDoDia }
        DialogoHora(
            titulo = when (qual) { "inicio" -> "Silêncio começa às"; "fim" -> "Silêncio termina às"; else -> "Resumo do dia às" },
            inicial = inicial,
            aoEscolher = { h ->
                salvar(when (qual) {
                    "inicio" -> config.copy(silencioInicio = h)
                    "fim" -> config.copy(silencioFim = h)
                    else -> config.copy(resumoDoDia = h)
                })
                editandoHora = null
            },
            aoFechar = { editandoHora = null },
        )
    }
}

/** Relógio do Material 3, com os botões do Kit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoHora(titulo: String, inicial: LocalTime, aoEscolher: (LocalTime) -> Unit, aoFechar: () -> Unit) {
    val estado = rememberTimePickerState(inicial.hour, inicial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = aoFechar,
        containerColor = NokyTheme.colors.surface,
        title = { Text(titulo, style = NokyTheme.type.h2) },
        text = { TimePicker(estado) },
        confirmButton = { NokyButton("OK", { aoEscolher(LocalTime(estado.hour, estado.minute)) }) },
        dismissButton = { NokyButton("Cancelar", aoFechar, kind = NokyButtonKind.Ghost) },
    )
}

/** Texto do relatório de bugs: versão, aparelho, erros e um retrato das tarefas abertas (sem os nomes). */
fun montarRelatorio(plataforma: Plataforma, tarefas: Tarefas): String = buildString {
    appendLine("Relatório de bug — Just in Time ${plataforma.versao}")
    appendLine("Aparelho: ${plataforma.aparelho}")
    appendLine("Permissões: " + plataforma.permissoes().joinToString { "${it.tipo.name}=${if (it.ok) "ok" else "falta"}" })
    val abertas = tarefas.abertasAgora()
    appendLine("Tarefas abertas: ${abertas.size} (${abertas.count { it.status.name == "AGUARDANDO" }} aguardando resposta)")
    appendLine()
    val erros = plataforma.errosPendentes()
    if (erros.isEmpty()) appendLine("Nenhum erro registrado. (Descreva aqui o que aconteceu.)")
    else { appendLine("=== Erros ==="); erros.forEach { appendLine(it); appendLine() } }
}
