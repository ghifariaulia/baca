package com.baca.shared.db

import com.baca.shared.domain.stats.StatsStore

/** Stats in `settings_local` (TECH 10.9). */
class DbStatsStore(private val db: Baca) : StatsStore {
    override fun get(key: String): String? = db.settings_localQueries.getLocal(key).executeAsOneOrNull()?.value_

    override fun put(key: String, value: String) {
        db.settings_localQueries.setLocal(key, value)
    }

    override fun withPrefix(prefix: String): Map<String, String> =
        db.settings_localQueries.selectLocalByPrefix(prefix).executeAsList().associate { it.key to it.value_ }
}
