package com.baca.desktop

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.baca.app.App
import com.baca.app.ui.AppThemeChoice
import com.baca.shared.db.Baca
import com.baca.shared.AppInfo
import java.awt.Desktop
import java.io.File

fun main(args: Array<String>) {
    // next to the books (~/.baca/books); an installed app starts in an unwritable folder
    val dataDir = File(System.getProperty("user.home"), ".baca").apply { mkdirs() }
    val db = createDesktopDb(File(dataDir, "baca.db"))
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
    // the book to open: named on the command line (--book=, or a bare path, as Windows and Linux pass
    // a double-clicked file), or handed over by macOS when a file is opened with Baca
    val initialBook = mutableStateOf(
        args.firstOrNull { it.startsWith("--book=") }?.removePrefix("--book=")
            ?: args.firstOrNull { !it.startsWith("-") && File(it).isFile },
    )
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE)) {
        Desktop.getDesktop().setOpenFileHandler { event -> event.files.firstOrNull()?.let { initialBook.value = it.path } }
    }
    application {
        Window(
            title = AppInfo.name,
            // window and dock icon while running unpackaged (gradle run)
            icon = remember { appIcon() },
            state = rememberWindowState(width = 900.dp, height = 640.dp),
            onCloseRequest = ::exitApplication,
        ) {
            App(db, initialBookPath = initialBook.value)
        }
    }
}

private fun appIcon(): BitmapPainter {
    val stream = checkNotNull(object {}.javaClass.getResourceAsStream("/baca.png")) { "baca.png missing from resources" }
    return BitmapPainter(stream.use { javax.imageio.ImageIO.read(it) }.toComposeImageBitmap())
}

private fun createDesktopDb(file: File): Baca {
    val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
    runCatching { Baca.Schema.create(driver) }
        .onFailure { if (!it.message.orEmpty().contains("already exists")) throw it }
    return Baca(driver)
}
