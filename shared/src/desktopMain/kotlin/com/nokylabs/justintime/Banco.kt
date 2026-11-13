package com.nokylabs.justintime

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.nokylabs.justintime.dados.JitDatabase

/** Banco no PC: um arquivo (ou na memória, para testes). */
fun bancoNoPc(arquivo: String? = null): JitDatabase {
    val driver = JdbcSqliteDriver(if (arquivo == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:$arquivo")
    JitDatabase.Schema.create(driver)
    return JitDatabase(driver)
}
