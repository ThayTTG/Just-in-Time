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
import com.nokylabs.justintime.ui.Tela

class MainActivity : ComponentActivity() {

    companion object {
        /** Vindo do "+" do widget: abre direto o formulário de nova tarefa. */
        const val NOVA_TAREFA = "nova_tarefa"
    }

    /** Tela pedida de fora (widget); o número muda a cada pedido. */
    private val pedido = mutableIntStateOf(0)
    private var telaPedida: Tela? = null

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

        lerPedido(intent)
        setContent {
            JustInTimeApp(
                tarefas, plataforma, atualizacao.intValue,
                telaPedida = telaPedida, pedido = pedido.intValue,
                avisoDaVersao = "Versão 0.5.0: Finalizar e Ajustar em cada tarefa, aviso antes do prazo com o tempo " +
                    "que você quiser (ex.: 30 min) e o aviso agora aparece flutuando na tela.",
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        lerPedido(intent)
    }

    private fun lerPedido(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(NOVA_TAREFA, false) == true) {
            telaPedida = Tela.Editor(null); pedido.intValue++
        }
    }

    override fun onResume() {
        super.onResume()
        // Confere se algum prazo passou com o app fechado, refaz os alarmes e relê permissões/erros
        Erros.proteger(this, "Reagendar ao abrir") { tarefas.reconciliar() }
        atualizacao.intValue++
    }
}
