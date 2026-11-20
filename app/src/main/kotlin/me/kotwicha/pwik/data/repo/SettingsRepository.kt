package me.kotwicha.pwik.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.kotwicha.pwik.core.calendar.DateSpan
import me.kotwicha.pwik.core.calendar.SchoolCalendar
import me.kotwicha.pwik.core.routing.Preference
import me.kotwicha.pwik.core.routing.RouterConfig

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")
private val Context.syncStore: DataStore<Preferences> by preferencesDataStore("sync")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val maxWalkMeters: Int = 1000,
    val maxTransferWalkMeters: Int = 400,
    val walkSpeedKmh: Double = 4.6,
    val maxTransfers: Int = 2,
    val preference: Preference = Preference.FASTEST,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val winterBreakOverride: DateSpan? = null,
) {
    fun routerConfig() = RouterConfig(
        maxAccessWalkMeters = maxWalkMeters.toDouble(),
        maxTransferWalkMeters = maxTransferWalkMeters.toDouble(),
        walkSpeedKmh = walkSpeedKmh,
        maxTransfers = maxTransfers,
    )

    fun winterBreaks(): List<DateSpan> {
        val override = winterBreakOverride ?: return SchoolCalendar.PODKARPACKIE_WINTER_BREAKS
        return SchoolCalendar.PODKARPACKIE_WINTER_BREAKS.filter { it.start.year != override.start.year } + override
    }
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val maxWalk = intPreferencesKey("max_walk")
        val maxTransferWalk = intPreferencesKey("max_transfer_walk")
        val speed = doublePreferencesKey("walk_speed")
        val transfers = intPreferencesKey("max_transfers")
        val preference = stringPreferencesKey("preference")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val winterStart = longPreferencesKey("winter_start")
        val winterEnd = longPreferencesKey("winter_end")
    }

    val settings: Flow<Settings> = context.settingsStore.data.map(::read)

    private fun read(p: Preferences): Settings {
        val d = Settings()
        val ws = p[Keys.winterStart]
        val we = p[Keys.winterEnd]
        return Settings(
            maxWalkMeters = p[Keys.maxWalk] ?: d.maxWalkMeters,
            maxTransferWalkMeters = p[Keys.maxTransferWalk] ?: d.maxTransferWalkMeters,
            walkSpeedKmh = p[Keys.speed] ?: d.walkSpeedKmh,
            maxTransfers = p[Keys.transfers] ?: d.maxTransfers,
            preference = p[Keys.preference]?.let { runCatching { Preference.valueOf(it) }.getOrNull() } ?: d.preference,
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            dynamicColor = p[Keys.dynamic] ?: d.dynamicColor,
            winterBreakOverride = if (ws != null && we != null) DateSpan(LocalDate.ofEpochDay(ws), LocalDate.ofEpochDay(we)) else null,
        )
    }

    suspend fun update(block: (Settings) -> Settings) {
        context.settingsStore.edit { p ->
            val next = block(read(p))
            p[Keys.maxWalk] = next.maxWalkMeters
            p[Keys.maxTransferWalk] = next.maxTransferWalkMeters
            p[Keys.speed] = next.walkSpeedKmh
            p[Keys.transfers] = next.maxTransfers
            p[Keys.preference] = next.preference.name
            p[Keys.theme] = next.themeMode.name
            p[Keys.dynamic] = next.dynamicColor
            val w = next.winterBreakOverride
            if (w == null) {
                p.remove(Keys.winterStart); p.remove(Keys.winterEnd)
            } else {
                p[Keys.winterStart] = w.start.toEpochDay(); p[Keys.winterEnd] = w.end.toEpochDay()
            }
        }
    }
}

enum class SyncOutcome { UPDATED, UNCHANGED, FAILED }

data class SyncStatus(
    val running: Boolean = false,
    val lastAttempt: Long? = null,
    val lastSuccess: Long? = null,
    val lastOutcome: SyncOutcome? = null,
    val message: String? = null,
)

class SyncStatusStore(private val context: Context) {
    private object Keys {
        val running = booleanPreferencesKey("running")
        val attempt = longPreferencesKey("attempt")
        val success = longPreferencesKey("success")
        val outcome = stringPreferencesKey("outcome")
        val message = stringPreferencesKey("message")
    }

    val status: Flow<SyncStatus> = context.syncStore.data.map { p ->
        SyncStatus(
            running = p[Keys.running] ?: false,
            lastAttempt = p[Keys.attempt],
            lastSuccess = p[Keys.success],
            lastOutcome = p[Keys.outcome]?.let { runCatching { SyncOutcome.valueOf(it) }.getOrNull() },
            message = p[Keys.message],
        )
    }

    suspend fun started() = context.syncStore.edit {
        it[Keys.running] = true
        it[Keys.attempt] = System.currentTimeMillis()
    }

    suspend fun finished(outcome: SyncOutcome, message: String?) = context.syncStore.edit {
        it[Keys.running] = false
        it[Keys.outcome] = outcome.name
        if (message == null) it.remove(Keys.message) else it[Keys.message] = message
        if (outcome != SyncOutcome.FAILED) it[Keys.success] = System.currentTimeMillis()
    }

    suspend fun clearRunning() = context.syncStore.edit { it[Keys.running] = false }
}
