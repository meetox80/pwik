package me.kotwicha.pwik.ui.nav

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.kotwicha.pwik.PwikApp
import me.kotwicha.pwik.core.calendar.DateSpan
import me.kotwicha.pwik.core.routing.Journey
import me.kotwicha.pwik.data.Connectivity
import me.kotwicha.pwik.data.repo.MapBundle
import me.kotwicha.pwik.data.repo.Place
import me.kotwicha.pwik.data.repo.Settings
import me.kotwicha.pwik.data.repo.SyncStatus
import me.kotwicha.pwik.data.repo.TimetableState
import me.kotwicha.pwik.data.sync.SyncScheduler

enum class Tab { PLAN, MAP, STOPS, SETTINGS }

enum class Field { FROM, TO }

sealed interface Route {
    data object Home : Route
    data class Picker(val field: Field) : Route
    data class MapPicker(val field: Field) : Route
    data class JourneyDetail(val journey: Journey, val date: LocalDate) : Route
    data class StopBoard(val stopKey: String) : Route
}

data class SyncUi(val status: SyncStatus, val pending: Boolean) {
    val busy: Boolean get() = pending || status.running
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val container = (app as PwikApp).container

    val stack = mutableStateListOf<Route>(Route.Home)
    var tab by mutableStateOf(Tab.PLAN)

    val timetable: StateFlow<TimetableState> = container.timetable.state
    val settings: StateFlow<Settings> = container.engine.settings
    val recentPlaces = container.places.recent(10).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentStops = container.places.recentStops(30).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sync: StateFlow<SyncUi> = combine(container.syncStatus.status, SyncScheduler.pending(app)) { s, p -> SyncUi(s, p) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SyncUi(SyncStatus(), false))
    val online: StateFlow<Boolean> = Connectivity.online(app).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    var map by mutableStateOf<MapBundle?>(container.maps.loaded)
        private set

    init {
        viewModelScope.launch { map = container.maps.get() }
    }

    val current: Route get() = stack.last()

    fun push(route: Route) {
        stack.add(route)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun openStop(stopKey: String, name: String) {
        record(Place.AtStop(stopKey, name))
        push(Route.StopBoard(stopKey))
    }

    fun record(place: Place) {
        viewModelScope.launch { container.places.record(place) }
    }

    fun forget(place: Place) {
        viewModelScope.launch { container.places.forget(place) }
    }

    fun clearHistory() {
        viewModelScope.launch { container.places.clear() }
    }

    fun updateSettings(block: (Settings) -> Settings) {
        viewModelScope.launch { container.settings.update(block) }
    }

    fun setWinterBreak(span: DateSpan?) = updateSettings { it.copy(winterBreakOverride = span) }

    fun syncNow() = SyncScheduler.syncNow(getApplication())
}
