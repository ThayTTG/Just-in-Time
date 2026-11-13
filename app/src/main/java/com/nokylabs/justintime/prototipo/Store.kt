package com.nokylabs.justintime.prototipo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Estados de uma tarefa no protótipo (no app final eles ficam na ocorrência). */
object Status {
    const val ATIVA = "ATIVA"
    const val AGUARDANDO = "AGUARDANDO"
    const val FEITA = "FEITA"
    const val NAO_FEITA = "NAO_FEITA"

    fun rotulo(s: String) = when (s) {
        ATIVA -> "Ativa"
        AGUARDANDO -> "Aguardando resposta"
        FEITA -> "Feita"
        NAO_FEITA -> "Não feita"
        else -> s
    }
}

data class Tarefa(
    val id: Int,
    var titulo: String,
    var prazo: Long,            // System.currentTimeMillis() do prazo
    var status: String = Status.ATIVA,
    var disparouEm: Long = 0L,  // quando o alarme realmente tocou
    var adiamentos: Int = 0,
) {
    val aberta get() = status == Status.ATIVA || status == Status.AGUARDANDO

    fun toJson() = JSONObject()
        .put("id", id).put("titulo", titulo).put("prazo", prazo)
        .put("status", status).put("disparouEm", disparouEm).put("adiamentos", adiamentos)

    companion object {
        fun fromJson(o: JSONObject) = Tarefa(
            id = o.getInt("id"),
            titulo = o.getString("titulo"),
            prazo = o.getLong("prazo"),
            status = o.optString("status", Status.ATIVA),
            disparouEm = o.optLong("disparouEm", 0L),
            adiamentos = o.optInt("adiamentos", 0),
        )
    }
}

/**
 * Armazenamento simples em SharedPreferences (JSON).
 * No app final isso vira o banco SQLDelight; aqui o objetivo é só testar alarme e widget.
 */
object Store {
    private const val PREFS = "jit_prototipo"
    private const val MAX_LOG = 200

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun tarefas(c: Context): MutableList<Tarefa> {
        val arr = JSONArray(prefs(c).getString("tarefas", "[]"))
        return MutableList(arr.length()) { Tarefa.fromJson(arr.getJSONObject(it)) }
    }

    private fun salvarTodas(c: Context, lista: List<Tarefa>) {
        val arr = JSONArray()
        lista.forEach { arr.put(it.toJson()) }
        // commit() (síncrono) porque receivers podem ter o processo encerrado logo depois
        prefs(c).edit().putString("tarefas", arr.toString()).commit()
    }

    fun get(c: Context, id: Int) = tarefas(c).firstOrNull { it.id == id }

    fun salvar(c: Context, t: Tarefa) {
        val lista = tarefas(c)
        val i = lista.indexOfFirst { it.id == t.id }
        if (i >= 0) lista[i] = t else lista.add(t)
        salvarTodas(c, lista)
    }

    fun remover(c: Context, id: Int) = salvarTodas(c, tarefas(c).filterNot { it.id == id })

    fun novoId(c: Context): Int {
        val p = prefs(c)
        val n = p.getInt("seq", 1)
        p.edit().putInt("seq", n + 1).commit()
        return n
    }

    /** Abertas primeiro (aguardando resposta no topo), depois pelo prazo mais próximo. */
    fun abertasOrdenadas(c: Context) = tarefas(c)
        .filter { it.aberta }
        .sortedWith(compareBy<Tarefa>({ if (it.status == Status.AGUARDANDO) 0 else 1 }, { it.prazo }))

    // ---------- Log de diagnóstico ----------

    private val ptBR = Locale("pt", "BR")
    private val fmt = SimpleDateFormat("dd/MM HH:mm:ss", ptBR)
    private val fmtAmigavel = SimpleDateFormat("EEE, dd/MM 'às' HH:mm", ptBR)
    private val fmtData = SimpleDateFormat("EEE, dd/MM/yyyy", ptBR)
    private val fmtHora = SimpleDateFormat("HH:mm", ptBR)

    /** Com segundos, para o log de diagnóstico. */
    fun hora(ms: Long): String = fmt.format(Date(ms))
    /** Para pessoas: "sex., 13/11 às 18:00". */
    fun amigavel(ms: Long): String = fmtAmigavel.format(Date(ms))
    fun data(ms: Long): String = fmtData.format(Date(ms))
    fun soHora(ms: Long): String = fmtHora.format(Date(ms))

    // ---------- Preferências ----------

    fun resumoAtivo(c: Context) = prefs(c).getBoolean("resumo", true)
    fun definirResumo(c: Context, v: Boolean) = prefs(c).edit().putBoolean("resumo", v).commit()

    fun bolhaAtiva(c: Context) = prefs(c).getBoolean("bolha", true)
    fun definirBolha(c: Context, v: Boolean) = prefs(c).edit().putBoolean("bolha", v).commit()

    /** Id da tarefa cuja bolha a pessoa fechou (volta a aparecer quando a próxima tarefa mudar). */
    fun bolhaFechadaPara(c: Context) = prefs(c).getInt("bolhaFechada", -1)
    fun fecharBolhaPara(c: Context, id: Int) = prefs(c).edit().putInt("bolhaFechada", id).commit()

    fun posicaoBolha(c: Context) = Pair(prefs(c).getInt("bolhaX", 24), prefs(c).getInt("bolhaY", 240))
    fun salvarPosicaoBolha(c: Context, x: Int, y: Int) =
        prefs(c).edit().putInt("bolhaX", x).putInt("bolhaY", y).apply()

    // ---------- Relatório de erros ----------

    fun errosPendentes(c: Context): List<String> {
        val arr = JSONArray(prefs(c).getString("erros", "[]"))
        return List(arr.length()) { arr.getString(it) }
    }

    fun adicionarErro(c: Context, texto: String) {
        val arr = JSONArray(prefs(c).getString("erros", "[]"))
        if (arr.length() < 10) arr.put(texto) // guarda no máximo 10 até serem reportados
        prefs(c).edit().putString("erros", arr.toString()).commit()
    }

    fun limparErros(c: Context) = prefs(c).edit().putString("erros", "[]").commit()

    fun log(c: Context, msg: String) {
        val arr = JSONArray(prefs(c).getString("log", "[]"))
        val novo = JSONArray().put("${hora(System.currentTimeMillis())}  $msg")
        // a lista é guardada da mais recente para a mais antiga; descarta as mais antigas
        for (i in 0 until minOf(arr.length(), MAX_LOG - 1)) novo.put(arr.getString(i))
        prefs(c).edit().putString("log", novo.toString()).commit()
    }

    /** Mais recente primeiro. */
    fun logs(c: Context): List<String> {
        val arr = JSONArray(prefs(c).getString("log", "[]"))
        return List(arr.length()) { arr.getString(it) }
    }

    fun limparLog(c: Context) = prefs(c).edit().putString("log", "[]").commit()
}
