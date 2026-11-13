package com.nokylabs.justintime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.nokylabs.justintime.dominio.Agendador
import com.nokylabs.justintime.dominio.DadosTarefa
import com.nokylabs.justintime.dominio.Horario
import com.nokylabs.justintime.dominio.Item
import com.nokylabs.justintime.dominio.Relogio
import com.nokylabs.justintime.dominio.Repeticao
import com.nokylabs.justintime.dominio.Tarefas
import com.nokylabs.justintime.ui.JustInTimeApp
import com.nokylabs.justintime.ui.Tela
import com.nokylabs.ui.NokyAnim
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/** Relógio parado num instante (para as capturas). */
class RelogioParado(var local: LocalDateTime) : Relogio {
    override val fuso = TimeZone.of("America/Sao_Paulo")
    override fun agora(): Instant = local.toInstant(fuso)
}

private object SemAlarme : Agendador {
    override fun agendar(item: Item, quando: Instant) {}
    override fun cancelar(ocorrenciaId: String) {}
}

/** Tarefas de exemplo (sexta, 13/11/2026, 10:00). */
fun exemplos(): Pair<Tarefas, RelogioParado> {
    val relogio = RelogioParado(LocalDateTime.parse("2026-11-13T09:00"))
    val t = Tarefas(bancoNoPc(), SemAlarme, relogio)
    val banco = t.criar(DadosTarefa("Ligar para o banco", "📞", prazo = LocalDateTime.parse("2026-11-13T09:30")))
    relogio.local = LocalDateTime.parse("2026-11-13T09:30")
    t.prazoChegou(banco.ocorrencia.id)
    val feita1 = t.criar(DadosTarefa("Café com a equipe", "☕", prazo = LocalDateTime.parse("2026-11-13T09:40")))
    val feita2 = t.criar(DadosTarefa("Corrigir provas", "📝", prazo = LocalDateTime.parse("2026-11-13T09:45")))
    relogio.local = LocalDateTime.parse("2026-11-13T10:00:00")
    t.responder(feita1.ocorrencia.id, feita = true)
    t.responder(feita2.ocorrencia.id, feita = true)
    t.criar(DadosTarefa("Entregar notas do 2º bimestre", "📚", prazo = LocalDateTime.parse("2026-11-13T12:41:07")))
    t.criar(DadosTarefa("Remédio", "💊", cor = 0xFF43C59E.toInt(), prazo = LocalDateTime.parse("2026-11-13T20:00"),
        repeticao = Repeticao.Intervalo(1, Repeticao.Unidade.DIA)))
    t.criar(DadosTarefa("Academia", "🏋️", cor = 0xFF8E5CF7.toInt(), prazo = LocalDateTime.parse("2026-11-14T14:00"),
        repeticao = Repeticao.Semanal(listOf(Horario(DayOfWeek.MONDAY, LocalTime(8, 0)),
            Horario(DayOfWeek.THURSDAY, LocalTime(20, 0)), Horario(DayOfWeek.SATURDAY, LocalTime(14, 0))))))
    t.criar(DadosTarefa("Aniversário da Ana", "🎂", cor = 0xFFF0629B.toInt(), prazo = LocalDateTime.parse("2026-11-21T09:00"),
        repeticao = Repeticao.Intervalo(1, Repeticao.Unidade.ANO)))
    return t to relogio
}

private fun capturar(arquivo: File, dark: Boolean, tela: (Tarefas) -> Tela) {
    val (tarefas, _) = exemplos()
    capturarConteudo(arquivo) { JustInTimeApp(tarefas, dark = dark, telaInicial = tela(tarefas), tique = false) }
}

@OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
private fun capturarConteudo(arquivo: File, conteudo: @androidx.compose.runtime.Composable () -> Unit) {
    val escala = 2f
    val (l, a) = 412 to 915
    val cena = ImageComposeScene((l * escala).toInt(), (a * escala).toInt(), Density(escala)) {
        Box(Modifier.size(l.dp, a.dp)) { conteudo() }
    }
    var tempo = 0L
    repeat(15) { cena.render(tempo); tempo += 100_000_000L; Thread.sleep(120) }
    arquivo.writeBytes(cena.render(tempo).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    cena.close()
}

/** ./gradlew :shared:capturar → imagens em capturas/ */
fun main(args: Array<String>) {
    val pasta = File(args.firstOrNull() ?: "capturas").apply { mkdirs() }
    NokyAnim.enabled = false
    capturar(File(pasta, "lista-escuro.png"), true) { Tela.Lista }
    capturar(File(pasta, "lista-claro.png"), false) { Tela.Lista }
    capturar(File(pasta, "editor-escuro.png"), true) { t ->
        Tela.Editor(t.abertasAgora().first { it.tarefa.titulo == "Academia" }.tarefa.id)
    }
    capturar(File(pasta, "configuracoes-escuro.png"), true) { Tela.Configuracoes }
    val (tarefas, _) = exemplos()
    val pendente = tarefas.abertasAgora().first { it.tarefa.titulo == "Ligar para o banco" }
    capturarConteudo(File(pasta, "alarme.png")) {
        com.nokylabs.ui.NokyTheme(accent = com.nokylabs.ui.NokyColors.JustInTime, dark = true) {
            com.nokylabs.justintime.ui.TelaAlarme(tarefas, pendente, aoTerminar = {})
        }
    }
    println("Imagens em ${pasta.absolutePath}")
}
