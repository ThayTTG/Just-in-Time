package com.nokylabs.justintime.android

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Relatório de bugs (o mesmo do protótipo):
 * - falha que fecha o app → gravada antes do processo morrer; na próxima abertura, aparece "Ops, algo deu errado";
 * - erro em segundo plano (alarme, lembrete) → gravado + uma notificação avisa.
 */
object Erros {
    private const val PREFS = "erros"
    private const val ID_NOTIFICACAO = 2_000_000

    fun instalar(app: Application) {
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, erro ->
            runCatching { registrar(app, "Falha que fechou o app (${thread.name})", erro, avisar = false) }
            anterior?.uncaughtException(thread, erro)
        }
    }

    /** Roda o bloco; se der erro, registra e avisa em vez de sumir em silêncio. */
    inline fun proteger(c: Context, onde: String, bloco: () -> Unit) {
        try { bloco() } catch (e: Throwable) { registrar(c, onde, e, avisar = true) }
    }

    fun registrar(c: Context, onde: String, erro: Throwable, avisar: Boolean) {
        val pilha = StringWriter().also { erro.printStackTrace(PrintWriter(it)) }.toString()
        val hora = SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "BR")).format(Date())
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lista = JSONArray(prefs.getString("lista", "[]"))
        if (lista.length() < 10) lista.put("[$hora] $onde\n${pilha.take(3500)}")
        prefs.edit().putString("lista", lista.toString()).commit() // commit: o processo pode morrer logo depois
        if (avisar) notificar(c)
    }

    fun pendentes(c: Context): List<String> {
        val lista = JSONArray(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lista", "[]"))
        return List(lista.length()) { lista.getString(it) }
    }

    fun limpar(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("lista", "[]").apply()

    private fun notificar(c: Context) {
        val abrir = PendingIntent.getActivity(
            c, 7, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE
        )
        c.getSystemService(NotificationManager::class.java).notify(
            ID_NOTIFICACAO,
            Notification.Builder(c, Notificacoes.CANAL_ERROS)
                .setSmallIcon(R.drawable.ic_notificacao)
                .setContentTitle("O Just in Time encontrou um erro")
                .setContentText("Toque para reportar o bug e ajudar a corrigir.")
                .setContentIntent(abrir).setAutoCancel(true).build()
        )
    }
}
