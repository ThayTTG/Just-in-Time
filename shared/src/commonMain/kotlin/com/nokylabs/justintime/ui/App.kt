package com.nokylabs.justintime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.nokylabs.ui.NokyButton
import com.nokylabs.ui.NokyButtonKind
import com.nokylabs.ui.NokyIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nokylabs.justintime.dominio.Tarefas
import com.nokylabs.ui.NokyColors
import com.nokylabs.ui.NokyTheme

/** As telas do app. */
sealed interface Tela {
    data object Lista : Tela
    /** tarefaId = null: nova tarefa. */
    data class Editor(val tarefaId: String?) : Tela
    data object Configuracoes : Tela
}

/**
 * O app inteiro (igual no Android e no PC). Cada plataforma só entrega o [Tarefas] já montado
 * com o seu banco de dados e o seu agendador.
 *
 * @param tique false = o relógio da tela não anda (usado nas capturas de tela).
 */
@Composable
fun JustInTimeApp(
    tarefas: Tarefas,
    plataforma: Plataforma = Plataforma.Nenhuma,
    /** Mude este número quando a pessoa voltar para o app (ex.: das configurações do sistema). */
    atualizacao: Int = 0,
    dark: Boolean? = null,
    telaInicial: Tela = Tela.Lista,
    tique: Boolean = true,
    avisoDaVersao: String? = null,
) {
    val tema: @Composable (@Composable () -> Unit) -> Unit = { conteudo ->
        if (dark == null) NokyTheme(accent = NokyColors.JustInTime, content = conteudo)
        else NokyTheme(accent = NokyColors.JustInTime, dark = dark, content = conteudo)
    }
    tema {
        var tela by remember { mutableStateOf(telaInicial) }
        Box(Modifier.fillMaxSize().background(NokyTheme.colors.bg)) {
            when (val t = tela) {
                Tela.Lista -> TelaLista(
                    tarefas, tique, avisoDaVersao,
                    aoCriar = { tela = Tela.Editor(null) },
                    aoEditar = { tela = Tela.Editor(it) },
                    aoConfigurar = { tela = Tela.Configuracoes },
                    permissoesFaltando = remember(atualizacao) { plataforma.permissoes().count { !it.ok } },
                )
                Tela.Configuracoes -> TelaConfiguracoes(tarefas, plataforma, atualizacao, aoFechar = { tela = Tela.Lista })
                is Tela.Editor -> TelaEditor(
                    tarefas, t.tarefaId,
                    aoFechar = { tela = Tela.Lista },
                    aoEditarOutra = { tela = Tela.Editor(it) },
                )
            }
        }

        // Relatório de bugs: se algo deu errado desde a última vez, oferece reportar
        var erros by remember(atualizacao) { mutableStateOf(plataforma.errosPendentes()) }
        if (erros.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = {},
                containerColor = NokyTheme.colors.surface,
                title = { Text("Ops, algo deu errado 😕", style = NokyTheme.type.h2) },
                text = {
                    Text("O app registrou ${erros.size} erro${if (erros.size > 1) "s" else ""}. Quer enviar um relatório? " +
                        "Ele leva a versão do app, o modelo do celular e o erro (sem os nomes das suas tarefas).",
                        style = NokyTheme.type.secondary)
                },
                confirmButton = {
                    NokyButton("Reportar bug", {
                        plataforma.reportar(montarRelatorio(plataforma, tarefas)); plataforma.limparErros(); erros = emptyList()
                    }, icon = NokyIcons.External)
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NokyButton("Copiar", {
                            plataforma.copiar(montarRelatorio(plataforma, tarefas)); plataforma.limparErros(); erros = emptyList()
                        }, kind = NokyButtonKind.Soft)
                        NokyButton("Descartar", { plataforma.limparErros(); erros = emptyList() }, kind = NokyButtonKind.Ghost)
                    }
                },
            )
        }
    }
}
