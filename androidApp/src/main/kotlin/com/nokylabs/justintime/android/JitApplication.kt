package com.nokylabs.justintime.android

import android.app.Application
import android.content.Context
import com.nokylabs.justintime.bancoNoAndroid
import com.nokylabs.justintime.dominio.Tarefas

/** Monta as peças do app uma vez só: banco de dados + agendador do Android + regras. */
class JitApplication : Application() {
    lateinit var tarefas: Tarefas
        private set

    override fun onCreate() {
        super.onCreate()
        Notificacoes.criarCanais(this)
        tarefas = Tarefas(bancoNoAndroid(this), AgendadorAndroid(this))
    }
}

/** Atalho: `context.tarefas` em qualquer lugar do app. */
val Context.tarefas: Tarefas get() = (applicationContext as JitApplication).tarefas
