package com.nokylabs.justintime.ui

/** Permissões que o app pede (cada uma com o porquê, em palavras simples). */
enum class TipoPermissao(val nome: String, val explicacao: String) {
    NOTIFICACOES("Notificações", "Sem isso, o alarme não aparece nem toca."),
    ALARMES_EXATOS("Alarmes exatos", "Para tocar no segundo certo, mesmo com o celular em repouso."),
    TELA_CHEIA("Tela de alarme no bloqueio", "Para a tela de alarme acender o celular bloqueado."),
    SOBRE_OUTROS_APPS("Aparecer sobre outros apps", "Para a tela de alarme abrir com o celular em uso e para a bolha."),
    BATERIA("Bateria sem restrição", "Evita que o sistema encerre o app em segundo plano."),
}

data class Permissao(val tipo: TipoPermissao, val ok: Boolean)

/**
 * O que cada plataforma oferece às telas: permissões, relatório de bugs, versão.
 * No Android é a PlataformaAndroid; nas capturas e no PC, [Nenhuma].
 */
interface Plataforma {
    val versao: String
    val aparelho: String

    fun permissoes(): List<Permissao>
    fun liberar(tipo: TipoPermissao)

    /** Erros registrados desde o último relatório. */
    fun errosPendentes(): List<String>
    fun limparErros()
    /** Abre o "compartilhar" do sistema com o relatório pronto. */
    fun reportar(texto: String)
    fun copiar(texto: String)
    /** Para testar o relatório (Configurações → Diagnóstico). */
    fun simularErro()
    /** A bolha foi fechada no ✕: volta a aparecer. */
    fun mostrarBolhaDeNovo() {}

    object Nenhuma : Plataforma {
        override val versao = "prévia"
        override val aparelho = "PC"
        override fun permissoes() = TipoPermissao.entries.map { Permissao(it, ok = it != TipoPermissao.BATERIA) }
        override fun liberar(tipo: TipoPermissao) {}
        override fun errosPendentes() = emptyList<String>()
        override fun limparErros() {}
        override fun reportar(texto: String) {}
        override fun copiar(texto: String) {}
        override fun simularErro() {}
    }
}
