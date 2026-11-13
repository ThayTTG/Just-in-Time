package com.nokylabs.justintime

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.nokylabs.justintime.dados.JitDatabase

/** Banco no Android: arquivo "justintime.db" na pasta privada do app. */
fun bancoNoAndroid(context: Context): JitDatabase =
    JitDatabase(AndroidSqliteDriver(JitDatabase.Schema, context, "justintime.db"))
