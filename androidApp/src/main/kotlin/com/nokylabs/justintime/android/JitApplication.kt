package com.nokylabs.justintime.android

import android.app.Application
import android.content.Context
import com.nokylabs.justintime.bancoNoAndroid
import com.nokylabs.justintime.dominio.Lembretes
import com.nokylabs.justintime.dominio.Tarefas

/** Monta as peças do app uma vez só: banco de dados + agendador do Android + regras. */
class JitApplication : Application() {
    lateinit var tarefas: Tarefas
        private set
    lateinit var lembretes: Lembretes
        private set

    override fun onCreate() {
        super.onCreate()
        Erros.instalar(this) // primeiro de tudo: qualquer falha daqui pra frente vira relatório
        Notificacoes.criarCanais(this)
        Vitrine.criarCanal(this)
        tarefas = Tarefas(bancoNoAndroid(this), AgendadorAndroid(this))
        lembretes = Lembretes(tarefas)
    }
}

/** Atalhos: `context.tarefas` e `context.lembretes` em qualquer lugar do app. */
val Context.tarefas: Tarefas get() = (applicationContext as JitApplication).tarefas
val Context.lembretes: Lembretes get() = (applicationContext as JitApplication).lembretes
