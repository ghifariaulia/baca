package com.baca.shared.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

fun inMemoryDb(): Pair<Baca, SqlDriver> {
    val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:")
    Baca.Schema.create(driver)
    return Pair(Baca(driver), driver)
}
