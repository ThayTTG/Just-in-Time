package com.nokylabs.justintime.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.nokylabs.justintime.ui.JustInTimeApp

class MainActivity : ComponentActivity() {

    private val pedirNotificacoes = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) pedirNotificacoes.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            JustInTimeApp(
                tarefas,
                avisoDaVersao = "Versão de base (0.1.0-alpha.1): tarefas, repetições e alarme com notificação. " +
                    "Tela de alarme, widget, resumo e bolha voltam na próxima etapa. Até lá, o protótipo continua instalado.",
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Confere se algum prazo passou com o app fechado e refaz os alarmes
        tarefas.reconciliar()
    }
}
