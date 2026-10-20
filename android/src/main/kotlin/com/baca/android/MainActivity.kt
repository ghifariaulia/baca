package com.baca.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.baca.app.App
import com.baca.shared.db.Baca

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.baca.app.reader.AndroidContexts.current = applicationContext
        val driver = AndroidSqliteDriver(Baca.Schema, applicationContext, "baca.db")
        // debug/automation entry: --es book <path> --ez contents true
        val bookPath = intent.getStringExtra("book")
        val showContents = intent.getBooleanExtra("contents", false)
        setContent { App(Baca(driver), initialBookPath = bookPath, openContents = showContents) }
    }
}
