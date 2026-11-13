package com.nokylabs.justintime.dominio

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.nokylabs.justintime.dados.JitDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid
import com.nokylabs.justintime.dados.Ocorrencia as LinhaOcorrencia
import com.nokylabs.justintime.dados.Tarefa as LinhaTarefa

/**
 * O que cada plataforma faz com os prazos: no Android, AlarmManager + notificações + widget;
 * no PC (fase 3), um temporizador do próprio app. As regras não conhecem nenhum dos dois.
 */
interface Agendador {
    /** Agenda o alarme do prazo de [item] para [quando]. Agendar de novo a mesma ocorrência substitui. */
    fun agendar(item: Item, quando: Instant)
    fun cancelar(ocorrenciaId: String)
    /** Algo mudou: atualizar widget, notificação-resumo e bolha. */
    fun vitrineMudou() {}
}

/** Relógio e fuso (nos testes, um relógio parado). */
interface Relogio {
    fun agora(): Instant
    val fuso: TimeZone

    object Sistema : Relogio {
        override fun agora() = Clock.System.now()
        override val fuso: TimeZone get() = TimeZone.currentSystemDefault()
    }
}

/** Erro que a tela mostra para a pessoa, com o texto pronto. */
class RegraViolada(mensagem: String) : IllegalArgumentException(mensagem)

/**
 * Todas as regras do Just in Time num lugar só: criar, editar, responder, adiar,
 * o que acontece quando o prazo chega e a repetição.
 */
class Tarefas(
    private val db: JitDatabase,
    private val agendador: Agendador,
    private val relogio: Relogio = Relogio.Sistema,
) {
    private val qt get() = db.tarefaQueries
    private val qo get() = db.ocorrenciaQueries
    private val qp get() = db.preferenciaQueries

    // ---------------- Preferências ----------------

    fun config(): Config {
        fun ler(chave: String) = qp.ler(chave).executeAsOneOrNull()
        fun hora(chave: String, padrao: LocalTime) = ler(chave)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: padrao
        val p = Config()
        return Config(
            silencioAtivo = ler("silencio.ativo")?.toBoolean() ?: p.silencioAtivo,
            silencioInicio = hora("silencio.inicio", p.silencioInicio),
            silencioFim = hora("silencio.fim", p.silencioFim),
            resumoDoDiaAtivo = ler("resumo.ativo")?.toBoolean() ?: p.resumoDoDiaAtivo,
            resumoDoDia = hora("resumo.hora", p.resumoDoDia),
            resumoNaNotificacao = ler("vitrine.resumo")?.toBoolean() ?: p.resumoNaNotificacao,
            bolhaAtiva = ler("vitrine.bolha")?.toBoolean() ?: p.bolhaAtiva,
            bolhaTamanho = ler("vitrine.bolha.tamanho")?.toFloatOrNull()?.coerceIn(0.7f, 1.8f) ?: p.bolhaTamanho,
        )
    }

    fun salvarConfig(c: Config) {
        db.transaction {
            qp.gravar("silencio.ativo", c.silencioAtivo.toString())
            qp.gravar("silencio.inicio", c.silencioInicio.toString())
            qp.gravar("silencio.fim", c.silencioFim.toString())
            qp.gravar("resumo.ativo", c.resumoDoDiaAtivo.toString())
            qp.gravar("resumo.hora", c.resumoDoDia.toString())
            qp.gravar("vitrine.resumo", c.resumoNaNotificacao.toString())
            qp.gravar("vitrine.bolha", c.bolhaAtiva.toString())
            qp.gravar("vitrine.bolha.tamanho", c.bolhaTamanho.coerceIn(0.7f, 1.8f).toString())
        }
        agendador.vitrineMudou() // os lembretes mudam de horário
    }

    /** Preferência livre de cada plataforma (ex.: tamanho da bolha). */
    fun preferencia(chave: String): String? = qp.ler(chave).executeAsOneOrNull()
    fun gravarPreferencia(chave: String, valor: String) = qp.gravar(chave, valor)

    fun agoraLocal(): LocalDateTime = relogio.agora().toLocalDateTime(relogio.fuso)
    fun instante(l: LocalDateTime): Instant = l.toInstant(relogio.fuso)

    // ---------------- Leitura ----------------

    /** Tarefas abertas, "aguardando resposta" primeiro e depois pelo prazo. Atualiza sozinha. */
    fun abertas(): Flow<List<Item>> = combine(
        qo.abertas().asFlow().mapToList(Dispatchers.Default),
        qt.todas().asFlow().mapToList(Dispatchers.Default),
    ) { ocorrencias, tarefas -> juntar(ocorrencias, tarefas) }

    fun historico(limite: Long = 100): Flow<List<Item>> = combine(
        qo.historico(limite).asFlow().mapToList(Dispatchers.Default),
        qt.todas().asFlow().mapToList(Dispatchers.Default),
    ) { ocorrencias, tarefas -> juntar(ocorrencias, tarefas) }

    fun abertasAgora(): List<Item> = juntar(qo.abertas().executeAsList(), qt.todas().executeAsList())

    fun item(ocorrenciaId: String): Item? {
        val o = qo.porId(ocorrenciaId).executeAsOneOrNull() ?: return null
        val t = qt.porId(o.tarefa_id).executeAsOneOrNull() ?: return null
        return Item(t.paraDominio(), o.paraDominio())
    }

    /** A ocorrência "da vez" de uma tarefa: a ativa, ou a última aguardando resposta. */
    fun ocorrenciaAtual(tarefaId: String): Ocorrencia? {
        val abertas = qo.daTarefa(tarefaId).executeAsList().map { it.paraDominio() }.filter { it.status.aberta }
        return abertas.firstOrNull { it.status == Status.ATIVA } ?: abertas.lastOrNull()
    }

    private fun juntar(ocorrencias: List<LinhaOcorrencia>, tarefas: List<LinhaTarefa>): List<Item> {
        val porId = tarefas.associateBy { it.id }
        return ocorrencias.mapNotNull { o -> porId[o.tarefa_id]?.let { Item(it.paraDominio(), o.paraDominio()) } }
    }

    // ---------------- Conflitos ----------------

    /** Tarefas abertas com o mesmo nome (sem diferenciar maiúsculas/espaços) ou no mesmo minuto. */
    fun conflitos(titulo: String, prazo: LocalDateTime, ignorarTarefaId: String? = null): List<Conflito> {
        val alvo = normalizar(titulo)
        return abertasAgora().filter { it.tarefa.id != ignorarTarefaId }.mapNotNull { item ->
            val mesmoNome = normalizar(item.tarefa.titulo) == alvo
            val mesmoHorario = item.prazo.mesmoMinuto(prazo)
            if (mesmoNome || mesmoHorario) Conflito(item, mesmoNome, mesmoHorario) else null
        }
    }

    // ---------------- Escrita ----------------

    fun criar(d: DadosTarefa): Item {
        validar(d, prazoPrecisaSerFuturo = true)
        val agora = relogio.agora().toEpochMilliseconds()
        val tarefaId = Uuid.random().toString()
        val prazo = d.prazoEfetivo
        val item = db.transactionWithResult {
            qt.inserir(
                tarefaId, d.titulo.trim(), d.emoji, d.cor.toLong(), d.tipoPrazo.name, prazo.toString(),
                d.repeticao.codificar(), d.avisos.joinToString(","), d.tocarNoSilencio.toLong(), 0L, agora, agora,
            )
            val ocorrenciaId = Uuid.random().toString()
            qo.inserir(ocorrenciaId, tarefaId, prazo.toString(), prazo.toString(), Status.ATIVA.name, 0, null, null, null)
            item(ocorrenciaId)!!
        }
        agendador.agendar(item, instante(item.prazo))
        agendador.vitrineMudou()
        return item
    }

    /**
     * Edita a tarefa e a sua ocorrência da vez. Mudar o prazo reabre a ocorrência (mesmo se
     * estava aguardando resposta); mudar só nome/cor/emoji mantém tudo como está.
     */
    fun editar(tarefaId: String, d: DadosTarefa): Item {
        val tarefa = qt.porId(tarefaId).executeAsOneOrNull()?.paraDominio() ?: throw RegraViolada("Essa tarefa não existe mais")
        val atual = ocorrenciaAtual(tarefaId)
        val novoPrazo = d.prazoEfetivo
        val prazoMudou = atual == null || novoPrazo != atual.prazo
        validar(d, prazoPrecisaSerFuturo = prazoMudou)
        val agora = relogio.agora().toEpochMilliseconds()

        val ocorrenciaId = db.transactionWithResult {
            val ancora = if (prazoMudou) novoPrazo else atual!!.prazoOriginal
            qt.atualizar(
                d.titulo.trim(), d.emoji, d.cor.toLong(), d.tipoPrazo.name, ancora.toString(), d.repeticao.codificar(),
                d.avisos.joinToString(","), d.tocarNoSilencio.toLong(), agora, tarefaId,
            )
            when {
                atual == null -> Uuid.random().toString().also {
                    qo.inserir(it, tarefaId, novoPrazo.toString(), novoPrazo.toString(), Status.ATIVA.name, 0, null, null, null)
                }
                prazoMudou -> {
                    qo.reprogramar(novoPrazo.toString(), novoPrazo.toString(), atual.id)
                    // Outras ocorrências abertas da mesma série deixam de valer (a regra mudou)
                    qo.apagarAbertasDaTarefa(tarefaId, atual.id)
                    atual.id
                }
                else -> atual.id
            }
        }
        if (tarefa.arquivada) qt.arquivar(agora, tarefaId)
        val item = item(ocorrenciaId)!!
        agendador.cancelar(ocorrenciaId)
        if (item.status == Status.ATIVA) agendador.agendar(item, instante(item.prazo))
        agendador.vitrineMudou()
        return item
    }

    fun responder(ocorrenciaId: String, feita: Boolean, motivo: Motivo? = null) {
        val o = qo.porId(ocorrenciaId).executeAsOneOrNull() ?: return
        val status = if (feita) Status.FEITA else Status.NAO_FEITA
        qo.responder(status.name, if (feita) null else motivo?.name, relogio.agora().toEpochMilliseconds(), o.id)
        agendador.cancelar(o.id)
        agendador.vitrineMudou()
    }

    fun adiar(ocorrenciaId: String, novoPrazo: LocalDateTime) {
        if (novoPrazo <= agoraLocal()) throw RegraViolada("Esse horário já passou. Escolha outro.")
        qo.adiar(novoPrazo.toString(), ocorrenciaId)
        val item = item(ocorrenciaId) ?: return
        agendador.agendar(item, instante(novoPrazo))
        agendador.vitrineMudou()
    }

    /** Hora local daqui a [segundos]. */
    fun daquiA(segundos: Long): LocalDateTime =
        Instant.fromEpochMilliseconds(relogio.agora().toEpochMilliseconds() + segundos * 1000).toLocalDateTime(relogio.fuso)

    /** Adia para daqui a [segundos] (botões "+15 min", "+1 h"). */
    fun adiarEm(ocorrenciaId: String, segundos: Long) = adiar(ocorrenciaId, daquiA(segundos))

    /**
     * O alarme tocou. A ocorrência passa a aguardar resposta e, se a tarefa repete, a próxima
     * já é criada e agendada (sem esperar a resposta).
     * @return o item, para a notificação; null se não havia nada a fazer (ex.: já respondida).
     */
    fun prazoChegou(ocorrenciaId: String): Item? {
        val item = item(ocorrenciaId) ?: return null
        if (item.status != Status.ATIVA) return null
        qo.mudarStatus(Status.AGUARDANDO.name, relogio.agora().toEpochMilliseconds(), ocorrenciaId)
        gerarProxima(item)
        agendador.vitrineMudou()
        return item(ocorrenciaId)
    }

    /**
     * Refaz todos os alarmes (ao abrir o app, ligar o celular, mudar a hora ou atualizar o app).
     * Prazos que passaram sem o alarme poder tocar viram "aguardando resposta".
     * @return quantos alarmes foram agendados.
     */
    fun reconciliar(): Int {
        val agora = agoraLocal()
        var agendados = 0
        for (item in abertasAgora()) {
            if (item.status != Status.ATIVA) continue
            if (item.prazo > agora) { agendador.agendar(item, instante(item.prazo)); agendados++ }
            else {
                qo.mudarStatus(Status.AGUARDANDO.name, null, item.ocorrencia.id)
                gerarProxima(item)
            }
        }
        // As próximas criadas acima também precisam de alarme
        abertasAgora().filter { it.status == Status.ATIVA && it.prazo > agora }.forEach { agendador.agendar(it, instante(it.prazo)) }
        agendador.vitrineMudou()
        return agendados
    }

    /** Apaga a tarefa e todo o seu histórico. */
    fun excluir(tarefaId: String) {
        val ocorrencias = qo.daTarefa(tarefaId).executeAsList()
        db.transaction {
            ocorrencias.forEach { qo.apagar(it.id) }
            qt.apagar(tarefaId)
        }
        ocorrencias.forEach { agendador.cancelar(it.id) }
        agendador.vitrineMudou()
    }

    // ---------------- Internos ----------------

    private fun gerarProxima(item: Item) {
        val regra = item.tarefa.repeticao
        if (!regra.repete) return
        val depoisDe = maxOf(agoraLocal(), item.ocorrencia.prazoOriginal)
        val proxima = regra.proximaApos(item.tarefa.ancora, depoisDe) ?: return
        if (qo.existe(item.tarefa.id, proxima.toString()).executeAsOne() > 0) return
        val id = Uuid.random().toString()
        qo.inserir(id, item.tarefa.id, proxima.toString(), proxima.toString(), Status.ATIVA.name, 0, null, null, null)
        item(id)?.let { agendador.agendar(it, instante(proxima)) }
    }

    private fun validar(d: DadosTarefa, prazoPrecisaSerFuturo: Boolean) {
        if (d.titulo.isBlank()) throw RegraViolada("Dê um nome para a tarefa")
        if (d.repeticao is Repeticao.Semanal && d.repeticao.horarios.isEmpty()) throw RegraViolada("Adicione pelo menos um horário")
        if (prazoPrecisaSerFuturo && d.prazoEfetivo <= agoraLocal()) throw RegraViolada("Esse horário já passou. Escolha outro.")
    }

    private fun normalizar(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")
}

private fun LocalDateTime.mesmoMinuto(o: LocalDateTime) = date == o.date && hour == o.hour && minute == o.minute
private fun Boolean.toLong() = if (this) 1L else 0L

internal fun LinhaTarefa.paraDominio() = Tarefa(
    id = id, titulo = titulo, emoji = emoji, cor = cor.toInt(), tipoPrazo = TipoPrazo.valueOf(tipo_prazo),
    ancora = LocalDateTime.parse(ancora), repeticao = Repeticao.decodificar(repeticao),
    avisos = avisos.split(",").mapNotNull { it.trim().toIntOrNull() },
    tocarNoSilencio = tocar_no_silencio != 0L, arquivada = arquivada != 0L,
)

internal fun LinhaOcorrencia.paraDominio() = Ocorrencia(
    id = id, tarefaId = tarefa_id, prazoOriginal = LocalDateTime.parse(prazo_original), prazo = LocalDateTime.parse(prazo),
    status = Status.valueOf(status), adiamentos = adiamentos.toInt(),
    motivo = motivo?.let { runCatching { Motivo.valueOf(it) }.getOrNull() },
    disparouEm = disparou_em, respondidaEm = respondida_em,
)
