package com.baca.app

actual fun defaultNow(): () -> Long = System::currentTimeMillis
