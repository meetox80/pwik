package me.kotwicha.pwik

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.kotwicha.pwik.data.LocationSource
import me.kotwicha.pwik.data.db.PwikDatabase
import me.kotwicha.pwik.data.repo.MapRepository
import me.kotwicha.pwik.data.repo.PlacesRepository
import me.kotwicha.pwik.data.repo.SettingsRepository
import me.kotwicha.pwik.data.repo.SyncStatusStore
import me.kotwicha.pwik.data.repo.TimetableRepository
import me.kotwicha.pwik.data.repo.TimetableState
import me.kotwicha.pwik.data.repo.TransitEngine
import me.kotwicha.pwik.data.sync.Http
import me.kotwicha.pwik.data.sync.SyncScheduler
import me.kotwicha.pwik.data.sync.TimetableSync

class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = PwikDatabase.create(context)
    val settings = SettingsRepository(context)
    val places = PlacesRepository(database.places())
    val maps = MapRepository(context)
    val timetable = TimetableRepository(context, database, maps, scope)
    val syncStatus = SyncStatusStore(context)
    val location = LocationSource(context)
    val sync = TimetableSync(Http("PWIK-Jaroslaw-Android/${BuildConfig.VERSION_NAME}"), timetable, syncStatus)
    val engine = TransitEngine(timetable, settings, maps, scope)
}

class PwikApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scope.launch {
            container.syncStatus.clearRunning()
            val state = container.timetable.state.first { it is TimetableState.Ready || it is TimetableState.Missing }
            val lastSuccess = if (state is TimetableState.Missing) null else container.syncStatus.status.first().lastSuccess
            SyncScheduler.schedule(this@PwikApp, lastSuccess)
        }
        container.scope.launch { container.maps.get() }
    }
}
