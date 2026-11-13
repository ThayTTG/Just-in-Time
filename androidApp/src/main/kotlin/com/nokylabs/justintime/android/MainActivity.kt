package com.nokylabs.justintime.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import com.nokylabs.justintime.ui.JustInTimeApp

class MainActivity : ComponentActivity() {

    /** Muda toda vez que a pessoa volta para o app: as telas releem permissões e erros. */
    private val atualizacao = mutableIntStateOf(0)

    private val pedirNotificacoes = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        atualizacao.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val plataforma = PlataformaAndroid(this, pedirNotificacoes)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) pedirNotificacoes.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            JustInTimeApp(
                tarefas, plataforma, atualizacao.intValue,
                avisoDaVersao = "Versão 0.2.0: alarme completo, avisos, horário silencioso e resumo do dia. " +
                    "Widget, resumo nas notificações e bolha chegam na 0.3.0.",
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Confere se algum prazo passou com o app fechado, refaz os alarmes e relê permissões/erros
        Erros.proteger(this, "Reagendar ao abrir") { tarefas.reconciliar() }
        atualizacao.intValue++
    }
}
