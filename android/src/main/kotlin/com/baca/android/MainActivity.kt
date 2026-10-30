package com.baca.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.baca.app.App
import com.baca.shared.db.Baca

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15+ forces this; opting in gives older versions the same layout
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        com.baca.app.reader.AndroidContexts.current = applicationContext
        val driver = AndroidSqliteDriver(Baca.Schema, applicationContext, "baca.db")
        // debug/automation entry: --es book <path> --ez contents true
        val showContents = intent.getBooleanExtra("contents", false)
        bookPath.value = intent.getStringExtra("book")
        // nothing is open yet: copies a closed app left in the cache are dead weight, swept before
        // the book another app handed us is copied in
        Thread {
            com.baca.app.reader.clearStagedBooks(applicationContext)
            openedBook(intent)
        }.start()
        setContent { App(Baca(driver), initialBookPath = bookPath.value, openContents = showContents) }
    }

    // "Open with Baca" while Baca is already running
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Thread { openedBook(intent) }.start()
    }

    /** The book to open, as a file in the cache; set from a background thread, read by the screen. */
    private val bookPath = mutableStateOf<String?>(null)

    private fun openedBook(intent: Intent) {
        com.baca.app.reader.copyOpenedBook(applicationContext, intent)?.let { bookPath.value = it }
    }
}
