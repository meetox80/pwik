package me.kotwicha.pwik.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.kotwicha.pwik.core.geo.LatLon
import me.kotwicha.pwik.data.db.PlaceUsageEntity
import me.kotwicha.pwik.data.db.PlacesDao

sealed interface Place {
    val key: String
    val label: String

    data object MyLocation : Place {
        override val key = "me"
        override val label = "Moja lokalizacja"
    }

    data class AtStop(val stopKey: String, val name: String) : Place {
        override val key get() = "stop:$stopKey"
        override val label get() = name
    }

    data class AtStreet(val name: String, val point: LatLon, val kind: String) : Place {
        override val key get() = "street:$name"
        override val label get() = name
    }

    data class AtPoint(val point: LatLon, val name: String) : Place {
        override val key get() = "point:%.5f,%.5f".format(java.util.Locale.ROOT, point.lat, point.lon)
        override val label get() = name
    }
}

class PlacesRepository(private val dao: PlacesDao) {

    fun recent(limit: Int = 12): Flow<List<Place>> = dao.recent(limit).map { list -> list.mapNotNull(::toPlace) }

    fun recentStops(limit: Int = 30): Flow<List<Place.AtStop>> =
        dao.recentStops(limit).map { list -> list.mapNotNull(::toPlace).filterIsInstance<Place.AtStop>() }

    suspend fun record(place: Place) {
        val now = System.currentTimeMillis()
        val e = when (place) {
            Place.MyLocation -> return
            is Place.AtStop -> PlaceUsageEntity(place.key, "stop", place.name, 0.0, 0.0, place.stopKey, now, 1)
            is Place.AtStreet -> PlaceUsageEntity(place.key, "street:${place.kind}", place.name, place.point.lat, place.point.lon, null, now, 1)
            is Place.AtPoint -> PlaceUsageEntity(place.key, "point", place.name, place.point.lat, place.point.lon, null, now, 1)
        }
        val prev = dao.get(e.key)
        dao.upsert(if (prev == null) e else e.copy(uses = prev.uses + 1))
    }

    suspend fun forget(place: Place) = dao.delete(place.key)

    suspend fun clear() = dao.clear()

    private fun toPlace(e: PlaceUsageEntity): Place? = when {
        e.kind == "stop" && e.stopKey != null -> Place.AtStop(e.stopKey, e.label)
        e.kind.startsWith("street") -> Place.AtStreet(e.label, LatLon(e.lat, e.lon), e.kind.substringAfter(':', "STREET"))
        e.kind == "point" -> Place.AtPoint(LatLon(e.lat, e.lon), e.label)
        else -> null
    }
}
