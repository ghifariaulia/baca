package com.baca.app.reader

/** Body of GitHub's "latest release" answer for Baca. Blocking: call it off the main thread. */
expect fun fetchLatestReleaseJson(): String
