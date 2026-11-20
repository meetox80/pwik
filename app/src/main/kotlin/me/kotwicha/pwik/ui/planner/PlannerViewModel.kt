package me.kotwicha.pwik.ui.planner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kotwicha.pwik.PwikApp
import me.kotwicha.pwik.core.geo.LatLon
import me.kotwicha.pwik.core.geo.metersBetween
import me.kotwicha.pwik.core.routing.Endpoint
import me.kotwicha.pwik.core.routing.Journey
import me.kotwicha.pwik.core.routing.Preference
import me.kotwicha.pwik.core.routing.rankedBy
import me.kotwicha.pwik.core.timetable.Network
import me.kotwicha.pwik.data.repo.Place
import me.kotwicha.pwik.ui.distance

sealed interface When {
    data object Now : When
    data class At(val date: LocalDate, val time: LocalTime) : When
}

data class PlannerUi(
    val from: Place? = Place.MyLocation,
    val to: Place? = null,
    val departure: When = When.Now,
    val preference: Preference = Preference.FASTEST,
    val loading: Boolean = false,
    val results: List<Journey> = emptyList(),
    val resultDate: LocalDate = LocalDate.now(),
    val searched: Boolean = false,
    val message: String? = null,
    val offerTomorrow: Boolean = false,
    val needsLocationPermission: Boolean = false,
)

private class SearchOutcome(val journeys: List<Journey>, val message: String?, val offerTomorrow: Boolean)

class PlannerViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as PwikApp).container
    private val _ui = MutableStateFlow(PlannerUi())
    val ui: StateFlow<PlannerUi> = _ui.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val s = c.settings.settings.first()
            _ui.update { it.copy(preference = s.preference) }
        }
    }

    fun setFrom(p: Place?) {
        _ui.update { it.copy(from = p, results = emptyList(), searched = false, message = null) }
        autoSearch()
    }

    fun setTo(p: Place?) {
        _ui.update { it.copy(to = p, results = emptyList(), searched = false, message = null) }
        autoSearch()
    }

    fun swap() {
        _ui.update { it.copy(from = it.to, to = it.from, results = emptyList(), searched = false, message = null) }
        autoSearch()
    }

    fun setWhen(w: When) {
        _ui.update { it.copy(departure = w) }
        autoSearch()
    }

    fun setPreference(p: Preference) {
        _ui.update { s -> s.copy(preference = p, results = s.results.rankedBy(p)) }
        viewModelScope.launch { c.settings.update { it.copy(preference = p) } }
    }

    fun permissionHandled(granted: Boolean) {
        _ui.update { it.copy(needsLocationPermission = false) }
        if (granted) search() else _ui.update {
            it.copy(message = "Bez dostępu do lokalizacji wybierz przystanek lub ulicę startową.")
        }
    }

    private fun autoSearch() {
        val s = _ui.value
        if (s.from != null && s.to != null) search()
    }

    fun refreshIfStale(now: LocalDateTime) {
        val s = _ui.value
        if (s.departure != When.Now || s.loading || s.results.isEmpty()) return
        val minute = now.hour * 60 + now.minute
        if (s.resultDate != now.toLocalDate() || s.results.any { it.departMinute < minute }) search(silent = true)
    }

    fun search(silent: Boolean = false) {
        val s = _ui.value
        val from = s.from
        val to = s.to
        if (from == null || to == null) {
            _ui.update { it.copy(message = "Wybierz, skąd i dokąd jedziesz.") }
            return
        }
        if (from.key == to.key) {
            _ui.update { it.copy(message = "Start i cel są w tym samym miejscu.", results = emptyList(), searched = true) }
            return
        }
        if ((from == Place.MyLocation || to == Place.MyLocation) && !c.location.hasPermission()) {
            _ui.update { it.copy(needsLocationPermission = true) }
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            if (!silent) _ui.update { it.copy(loading = true, message = null, offerTomorrow = false) }
            val network = c.engine.network.value ?: c.engine.network.first { it != null }!!
            val fromPoint = resolve(from, network)
            val toPoint = resolve(to, network)
            if (fromPoint == null || toPoint == null) {
                _ui.update {
                    it.copy(loading = false, searched = true, results = emptyList(), message = "Nie udało się ustalić Twojej lokalizacji. Włącz GPS albo wybierz miejsce startu ręcznie.")
                }
                return@launch
            }
            val bundle = c.maps.get()
            if (!bundle.map.bounds.contains(fromPoint.lat, fromPoint.lon) || !bundle.map.bounds.contains(toPoint.lat, toPoint.lon)) {
                _ui.update {
                    it.copy(loading = false, searched = true, results = emptyList(), message = "To miejsce jest poza zasięgiem komunikacji miejskiej w Jarosławiu.")
                }
                return@launch
            }
            val (date, minute) = when (val w = s.departure) {
                When.Now -> LocalTime.now().let { LocalDate.now() to it.hour * 60 + it.minute }
                is When.At -> w.date to w.time.hour * 60 + w.time.minute
            }
            val router = c.engine.router(network)
            val outcome = withContext(Dispatchers.Default) {
                val day = c.engine.serviceDay(network, date)
                val start = endpoint(from, fromPoint, toPoint, network)
                val end = endpoint(to, toPoint, fromPoint, network)
                val found = router.plan(start, end, day, minute)
                var message: String? = null
                var tomorrow = false
                if (found.all { it.walkOnly }) {
                    val far = listOf(start, end).filterIsInstance<Endpoint.AtPoint>().firstNotNullOfOrNull { e ->
                        router.nearestStop(e.point)?.takeIf { it.second > c.engine.settings.value.maxWalkMeters }
                    }
                    when {
                        far != null -> message = "Najbliższy przystanek, ${far.first.name}, jest ${distance(far.second)} stąd. Zwiększ limit dojścia w ustawieniach, aby go uwzględnić."
                        found.isNotEmpty() -> message = "Autobusem nie dojedziesz tam szybciej. Najlepiej przejść pieszo."
                        else -> {
                            message = if (minute > 18 * 60) "Dziś nie ma już autobusów na tej trasie." else "W tym dniu nie ma autobusów na tej trasie."
                            tomorrow = true
                        }
                    }
                }
                SearchOutcome(found, message, tomorrow)
            }
            val found = outcome.journeys
            _ui.update {
                it.copy(
                    loading = false,
                    searched = true,
                    results = found.rankedBy(it.preference),
                    resultDate = date,
                    message = outcome.message,
                    offerTomorrow = outcome.offerTomorrow,
                )
            }
            if (found.isNotEmpty()) {
                if (from != Place.MyLocation) c.places.record(from)
                if (to != Place.MyLocation) c.places.record(to)
            }
        }
    }

    fun searchTomorrowMorning() {
        val next = when (val w = _ui.value.departure) {
            When.Now -> LocalDate.now().plusDays(1)
            is When.At -> w.date.plusDays(1)
        }
        setWhen(When.At(next, LocalTime.of(5, 0)))
    }

    private suspend fun resolve(p: Place, network: Network): LatLon? = when (p) {
        Place.MyLocation -> c.location.current()
        is Place.AtStop -> network.stop(p.stopKey)?.point
        is Place.AtStreet -> p.point
        is Place.AtPoint -> p.point
    }

    private suspend fun endpoint(p: Place, own: LatLon, other: LatLon, network: Network): Endpoint = when (p) {
        is Place.AtStop -> network.stop(p.stopKey)?.let { Endpoint.AtStop(it.index) } ?: Endpoint.AtPoint(own, p.name)
        is Place.AtStreet -> {
            val snapped = if (p.kind == "STREET") c.maps.get().streets.nearestPointOnStreet(p.name, other) else null
            Endpoint.AtPoint(snapped?.takeIf { metersBetween(it, own) < 5000 } ?: own, p.name)
        }
        is Place.AtPoint -> Endpoint.AtPoint(own, p.name)
        Place.MyLocation -> Endpoint.AtPoint(own, "Moja lokalizacja")
    }
}
