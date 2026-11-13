package com.nokylabs.justintime.prototipo

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter

/** Application: o primeiro código a rodar. Instala o "pega-erros" antes de tudo. */
class JitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Relatorio.instalar(this)
    }
}

/**
 * Relatório de bugs.
 *
 * 1. Falha que fecha o app → é gravada antes de o processo morrer; na próxima
 *    abertura, o app mostra "Ops, algo deu errado" com o botão Reportar bug.
 * 2. Erro em segundo plano (alarme, widget...) → é gravado e uma notificação avisa.
 *
 * "Reportar" abre o compartilhamento do Android (e-mail, WhatsApp, Discord...) com o
 * relatório pronto. Envio 100% automático exige um servidor: fica para depois.
 */
object Relatorio {

    private const val CANAL_ERROS = "erros"
    private const val ID_NOTIF_ERRO = 2_000_000

    fun instalar(app: Application) {
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, erro ->
            try {
                registrar(app, "Falha que fechou o app (thread ${thread.name})", erro, avisar = false)
            } catch (_: Throwable) { /* nunca deixar o pega-erros causar outro erro */ }
            anterior?.uncaughtException(thread, erro)
        }
    }

    /** Executa um bloco e, se der erro, registra em vez de derrubar o app. */
    inline fun proteger(c: Context, onde: String, bloco: () -> Unit) {
        try { bloco() } catch (e: Throwable) { registrar(c, onde, e, avisar = true) }
    }

    fun registrar(c: Context, onde: String, erro: Throwable, avisar: Boolean) {
        val pilha = StringWriter().also { erro.printStackTrace(PrintWriter(it)) }.toString()
        val texto = "[${Store.hora(System.currentTimeMillis())}] $onde\n${pilha.take(3500)}"
        Store.adicionarErro(c, texto)
        Store.log(c, "❌ Erro: $onde — ${erro.javaClass.simpleName}: ${erro.message}")
        if (avisar) notificar(c)
    }

    private fun notificar(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL_ERROS, "Avisos de erro", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val abrir = PendingIntent.getActivity(
            c, 7, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(c, CANAL_ERROS)
            .setSmallIcon(R.drawable.ic_stat_alarme)
            .setContentTitle("O Just in Time encontrou um erro")
            .setContentText("Toque para reportar o bug e ajudar a corrigir.")
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        nm.notify(ID_NOTIF_ERRO, n)
    }

    fun versao(c: Context): String = try {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }

    /** Texto completo do relatório: aparelho, versão, erros e as últimas linhas do log. */
    fun montarTexto(c: Context): String = buildString {
        appendLine("Relatório de bug — Just in Time ${versao(c)}")
        appendLine("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("=== Erros ===")
        Store.errosPendentes(c).forEach { appendLine(it); appendLine() }
        appendLine("=== Últimas 30 linhas do log ===")
        Store.logs(c).take(30).forEach { appendLine(it) }
    }

    fun intentCompartilhar(c: Context): Intent {
        val envio = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Just in Time ${versao(c)} — relatório de bug")
            .putExtra(Intent.EXTRA_TEXT, montarTexto(c))
        return Intent.createChooser(envio, "Reportar bug por...")
    }
}
