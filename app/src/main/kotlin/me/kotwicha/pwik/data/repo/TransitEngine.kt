package me.kotwicha.pwik.data.repo

import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import me.kotwicha.pwik.core.calendar.DateSpan
import me.kotwicha.pwik.core.calendar.DayResolver
import me.kotwicha.pwik.core.calendar.SchoolCalendar
import me.kotwicha.pwik.core.routing.Router
import me.kotwicha.pwik.core.routing.RouterConfig
import me.kotwicha.pwik.core.text.fold
import me.kotwicha.pwik.core.text.searchRank
import me.kotwicha.pwik.core.timetable.Network
import me.kotwicha.pwik.core.timetable.ServiceDay
import me.kotwicha.pwik.core.timetable.Stop

class TransitEngine(
    timetable: TimetableRepository,
    settingsRepo: SettingsRepository,
    private val maps: MapRepository,
    scope: CoroutineScope,
) {
    val network: StateFlow<Network?> = timetable.state
        .map { (it as? TimetableState.Ready)?.network }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val settings: StateFlow<Settings> = settingsRepo.settings.stateIn(scope, SharingStarted.Eagerly, Settings())

    private var resolverKey: List<DateSpan>? = null
    private var resolver: DayResolver? = null
    private val dayCache = HashMap<Pair<Network, LocalDate>, ServiceDay>()
    private var dayCacheBreaks: List<DateSpan>? = null
    private var routerKey: Pair<Network, RouterConfig>? = null
    private var router: Router? = null

    @Synchronized
    fun days(): DayResolver {
        val breaks = settings.value.winterBreaks()
        if (resolverKey != breaks || resolver == null) {
            resolver = DayResolver(SchoolCalendar(breaks))
            resolverKey = breaks
        }
        return resolver!!
    }

    @Synchronized
    fun serviceDay(network: Network, date: LocalDate): ServiceDay {
        val breaks = settings.value.winterBreaks()
        if (dayCacheBreaks != breaks) {
            dayCache.clear()
            dayCacheBreaks = breaks
        }
        if (dayCache.keys.any { it.first !== network }) dayCache.clear()
        if (dayCache.size > 6) dayCache.clear()
        return dayCache.getOrPut(network to date) { ServiceDay(network, days().info(date)) }
    }

    suspend fun router(network: Network): Router {
        val cfg = settings.value.routerConfig()
        synchronized(this) {
            val cached = router
            if (cached != null && routerKey?.first === network && routerKey?.second == cfg) return cached
        }
        val walk = maps.get().walk
        val built = withContext(Dispatchers.Default) { Router(network, cfg, walk) }
        synchronized(this) {
            router = built
            routerKey = network to cfg
        }
        return built
    }

    fun searchStops(network: Network, query: String, limit: Int = 20): List<Stop> {
        val q = fold(query)
        if (q.isEmpty()) return emptyList()
        return network.stops.mapNotNull { s ->
            s.searchKeys.mapNotNull { searchRank(q, it) }.minOrNull()?.let { s to it }
        }.sortedWith(compareBy({ it.second }, { it.first.name.length }, { it.first.name })).take(limit).map { it.first }
    }
}
