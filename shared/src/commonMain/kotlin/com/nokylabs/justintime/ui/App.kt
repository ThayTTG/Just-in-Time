package com.nokylabs.justintime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
                )
                is Tela.Editor -> TelaEditor(
                    tarefas, t.tarefaId,
                    aoFechar = { tela = Tela.Lista },
                    aoEditarOutra = { tela = Tela.Editor(it) },
                )
            }
        }
    }
}
