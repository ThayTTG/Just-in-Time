package com.nokylabs.justintime.android

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import com.nokylabs.justintime.ui.Permissao
import com.nokylabs.justintime.ui.Plataforma
import com.nokylabs.justintime.ui.TipoPermissao

/** Permissões, relatório de bugs e versão, do jeito do Android. */
class PlataformaAndroid(
    private val activity: ComponentActivity,
    private val pedirNotificacoes: ActivityResultLauncher<String>,
) : Plataforma {

    override val versao: String = runCatching {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    override val aparelho: String = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    override fun permissoes(): List<Permissao> {
        val agendador = AgendadorAndroid(activity)
        val bateria = activity.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(activity.packageName)
        val notificacoes = activity.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()
        return listOf(
            Permissao(TipoPermissao.NOTIFICACOES, notificacoes),
            Permissao(TipoPermissao.ALARMES_EXATOS, agendador.podeAgendarExato()),
            Permissao(TipoPermissao.TELA_CHEIA, Notificacoes.podeTelaCheia(activity)),
            Permissao(TipoPermissao.SOBRE_OUTROS_APPS, Settings.canDrawOverlays(activity)),
            Permissao(TipoPermissao.BATERIA, bateria),
        )
    }

    override fun liberar(tipo: TipoPermissao) {
        val pacote = Uri.parse("package:${activity.packageName}")
        when (tipo) {
            TipoPermissao.NOTIFICACOES ->
                if (Build.VERSION.SDK_INT >= 33) pedirNotificacoes.launch(Manifest.permission.POST_NOTIFICATIONS)
                else abrir(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
            TipoPermissao.ALARMES_EXATOS ->
                if (Build.VERSION.SDK_INT >= 31) abrir(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pacote))
            TipoPermissao.TELA_CHEIA ->
                if (Build.VERSION.SDK_INT >= 34) abrir(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pacote))
            TipoPermissao.SOBRE_OUTROS_APPS -> abrir(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pacote))
            TipoPermissao.BATERIA -> abrir(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pacote))
        }
    }

    private fun abrir(i: Intent) {
        try { activity.startActivity(i) } catch (_: Exception) {
            // Alguns fabricantes não têm a tela específica: abre os detalhes do app
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))
        }
    }

    override fun errosPendentes() = Erros.pendentes(activity)
    override fun limparErros() = Erros.limpar(activity)

    override fun reportar(texto: String) {
        val envio = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Just in Time $versao — relatório de bug")
            .putExtra(Intent.EXTRA_TEXT, texto)
        activity.startActivity(Intent.createChooser(envio, "Reportar bug por..."))
    }

    override fun copiar(texto: String) {
        activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Just in Time", texto))
        Toast.makeText(activity, "Relatório copiado", Toast.LENGTH_SHORT).show()
    }

    override fun simularErro() {
        activity.sendBroadcast(Intent(activity, AlarmeReceiver::class.java).setAction(AlarmeReceiver.SIMULAR_ERRO))
        Toast.makeText(activity, "Erro simulado: deve chegar uma notificação", Toast.LENGTH_SHORT).show()
    }
}
