package com.baca.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.baca.app.App
import com.baca.app.ui.AppThemeChoice
import com.baca.shared.db.Baca
import com.baca.shared.AppInfo
import java.io.File

fun main(args: Array<String>) {
    val db = createDesktopDb(File("baca.db"))
    // macOS draws the title bar natively and keeps it light for Java apps
    // unless told otherwise; it can only be set before AWT starts, so a
    // changed app theme reaches the title bar on the next launch
    val theme = AppThemeChoice.of(db.settings_localQueries.getLocal(AppThemeChoice.STORE_KEY).executeAsOneOrNull()?.value_)
    System.setProperty(
        "apple.awt.application.appearance",
        when (theme) {
            AppThemeChoice.SYSTEM -> "system"
            AppThemeChoice.LIGHT -> "NSAppearanceNameAqua"
            AppThemeChoice.DARK -> "NSAppearanceNameDarkAqua"
        },
    )
    val initialBook = args.firstOrNull { it.startsWith("--book=") }?.removePrefix("--book=")
    application {
        Window(
            title = AppInfo.name,
            state = rememberWindowState(width = 900.dp, height = 640.dp),
            onCloseRequest = ::exitApplication,
        ) {
            App(db, initialBookPath = initialBook)
        }
    }
}

private fun createDesktopDb(file: File): Baca {
    val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
    runCatching { Baca.Schema.create(driver) }
        .onFailure { if (!it.message.orEmpty().contains("already exists")) throw it }
    return Baca(driver)
}
