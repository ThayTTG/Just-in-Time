package com.nokylabs.justintime

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.nokylabs.justintime.dados.JitDatabase
import com.nokylabs.justintime.dominio.Config
import com.nokylabs.justintime.dominio.DadosTarefa
import com.nokylabs.justintime.dominio.Tarefas
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** Quem tem a 0.1.0 instalada (banco versão 1) atualiza sem perder as tarefas. */
class MigracaoTest {
    @Test fun versao1_para_2_mantem_as_tarefas() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        JitDatabase.Schema.create(driver)
        driver.execute(null, "DROP TABLE preferencia", 0) // agora o banco está como na 0.1.0
        val relogio = RelogioDeTeste(LocalDateTime.parse("2026-11-13T10:00"))
        Tarefas(JitDatabase(driver), AgendadorDeTeste(), relogio)
            .criar(DadosTarefa("Antiga", prazo = LocalDateTime.parse("2026-11-13T18:00")))

        JitDatabase.Schema.migrate(driver, 1, JitDatabase.Schema.version)

        val tarefas = Tarefas(JitDatabase(driver), AgendadorDeTeste(), relogio)
        assertEquals(listOf("Antiga"), tarefas.abertasAgora().map { it.tarefa.titulo })
        tarefas.salvarConfig(Config(silencioInicio = LocalTime(22, 0)))
        assertEquals(LocalTime(22, 0), tarefas.config().silencioInicio)
        assertEquals(2L, JitDatabase.Schema.version)
    }
}
