package com.nokylabs.justintime.android

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nokylabs.justintime.dominio.Item
import com.nokylabs.justintime.ui.TelaAlarme
import com.nokylabs.ui.NokyColors
import com.nokylabs.ui.NokyTheme

/** Tela de alarme: aparece por cima do bloqueio (e do que estiver aberto, com a permissão). */
class AlarmeActivity : ComponentActivity() {

    companion object {
        private const val ID = "ocorrencia"
        fun intent(c: Context, ocorrenciaId: String): Intent =
            Intent(c, AlarmeActivity::class.java).putExtra(ID, ocorrenciaId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }

    private var item by mutableStateOf<Item?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        carregar(intent)
        setContent {
            NokyTheme(accent = NokyColors.JustInTime, dark = true) {
                item?.let { TelaAlarme(tarefas, it, aoTerminar = { finish() }) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        carregar(intent)
    }

    private fun carregar(intent: Intent) {
        val i = intent.getStringExtra(ID)?.let { tarefas.item(it) }
        if (i == null || !i.status.aberta) { finish(); return }
        item = i
    }
}
