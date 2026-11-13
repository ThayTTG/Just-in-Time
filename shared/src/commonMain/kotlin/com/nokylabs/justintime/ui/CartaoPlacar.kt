package com.nokylabs.justintime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nokylabs.justintime.dominio.Periodo
import com.nokylabs.justintime.dominio.Placar
import com.nokylabs.ui.NokyBadge
import com.nokylabs.ui.NokyBadgeKind
import com.nokylabs.ui.NokyCard
import com.nokylabs.ui.NokyCountText
import com.nokylabs.ui.NokySegmented
import com.nokylabs.ui.NokyTheme

/**
 * O contador de entregas: entregues × não entregues no dia ou na semana, o que vem pela frente
 * e a sequência de dias. Uma recompensa em números pelo que foi entregue.
 */
@Composable
fun CartaoPlacar(placar: Placar, aoTrocarPeriodo: (Periodo) -> Unit, modifier: Modifier = Modifier) {
    val c = NokyTheme.colors
    val t = NokyTheme.type
    NokyCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ENTREGAS", style = t.caps, modifier = Modifier.weight(1f))
            NokySegmented(Periodo.entries.map { it.rotulo }, placar.periodo.ordinal, { aoTrocarPeriodo(Periodo.entries[it]) })
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Numero(placar.entregues, "entregue${if (placar.entregues == 1) "" else "s"}", c.success, Modifier.weight(1f))
            Numero(placar.naoEntregues, "não entregue${if (placar.naoEntregues == 1) "" else "s"}", c.danger, Modifier.weight(1f))
            Numero(placar.pelaFrente, "pela frente", c.textSecondary, Modifier.weight(1f))
        }
        if (placar.total > 0) {
            Spacer(Modifier.height(12.dp))
            BarraDoPlacar(placar)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(mensagem(placar), style = t.secondary, modifier = Modifier.weight(1f))
            if (placar.sequencia > 0)
                NokyBadge("🔥 ${placar.sequencia} dia${if (placar.sequencia > 1) "s" else ""} seguido${if (placar.sequencia > 1) "s" else ""}", NokyBadgeKind.Gold)
        }
    }
}

@Composable
private fun Numero(valor: Int, rotulo: String, cor: Color, modifier: Modifier) {
    val t = NokyTheme.type
    Column(modifier) {
        // Zero fica discreto: nenhum "não entregue" é motivo de comemorar, não de alerta
        NokyCountText(valor.toDouble(), style = t.h1.copy(color = if (valor == 0) NokyTheme.colors.textMuted else cor))
        Text(rotulo, style = t.muted)
    }
}

/** Barra dividida: verde (entregues), vermelho (não entregues), cinza (pela frente). */
@Composable
private fun BarraDoPlacar(p: Placar) {
    val c = NokyTheme.colors
    val forma = RoundedCornerShape(50)
    Row(Modifier.fillMaxWidth().height(8.dp).clip(forma).background(c.surfaceRaised)) {
        listOf(p.entregues to c.success, p.naoEntregues to c.danger, p.pelaFrente to c.surfaceRaised)
            .filter { it.first > 0 }
            .forEachIndexed { i, (n, cor) ->
                Box(Modifier.weight(n.toFloat()).fillMaxHeight().padding(start = if (i > 0) 2.dp else 0.dp).background(cor))
            }
    }
}

/** Uma frase de retorno, sem cobrança: comemora o que deu certo e mostra o que falta. */
fun mensagem(p: Placar): String {
    val quando = if (p.periodo == Periodo.DIA) "hoje" else "nesta semana"
    return when {
        p.total == 0 -> "Nada com prazo $quando."
        p.respondidas == 0 -> "${p.pelaFrente} pela frente $quando. Bora!"
        p.naoEntregues == 0 && p.pelaFrente == 0 -> "Tudo entregue $quando! 🎉"
        p.naoEntregues == 0 -> "Tudo em dia até agora. Faltam ${p.pelaFrente}."
        else -> "${((p.taxa ?: 0.0) * 100).toInt()}% entregue $quando."
    }
}
