package me.kotwicha.pwik.core.importer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.kotwicha.pwik.core.calendar.Service
import me.kotwicha.pwik.core.geo.LatLon
import me.kotwicha.pwik.core.timetable.Line
import me.kotwicha.pwik.core.timetable.Network
import me.kotwicha.pwik.core.timetable.Pattern
import me.kotwicha.pwik.core.timetable.Platform
import me.kotwicha.pwik.core.timetable.Stop
import me.kotwicha.pwik.core.timetable.Trip

@Serializable
data class GazetteerPlatform(val code: String, val lat: Double, val lon: Double)

@Serializable
data class GazetteerStop(
    val key: String,
    val name: String,
    val names: List<String>,
    val osm: List<String>,
    val placed: Boolean,
    val platforms: List<GazetteerPlatform>,
)

@Serializable
data class GazetteerOsmGroup(val name: String, val platforms: List<GazetteerPlatform>)

@Serializable
data class Gazetteer(val version: Int, val stops: List<GazetteerStop>, val osm: List<GazetteerOsmGroup>) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun decode(text: String): Gazetteer = json.decodeFromString(serializer(), text)
    }
}

data class FeedStop(
    val key: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val approximate: Boolean,
    val platforms: List<Platform>,
    val aliases: List<String>,
)

data class FeedLine(val name: String, val title: String)

data class FeedPattern(
    val line: Int,
    val stops: IntArray,
    val platforms: IntArray,
    val shape: List<LatLon> = emptyList(),
    val shapeBreaks: IntArray = IntArray(0),
)

data class FeedTrip(
    val pattern: Int,
    val service: Int,
    val times: IntArray,
    val conditions: IntArray,
    val note: String?,
)

data class Feed(
    val stops: List<FeedStop>,
    val lines: List<FeedLine>,
    val patterns: List<FeedPattern>,
    val services: List<Service>,
    val trips: List<FeedTrip>,
    val warnings: List<String>,
) {
    fun toNetwork(): Network = Network(
        stops = stops.mapIndexed { i, s -> Stop(i, s.key, s.name, s.lat, s.lon, s.approximate, s.platforms, s.aliases) },
        lines = lines.mapIndexed { i, l -> Line(i, l.name, l.title) },
        patterns = patterns.mapIndexed { i, p -> Pattern(i, p.line, p.stops, p.platforms, p.shape, p.shapeBreaks) },
        services = services,
        trips = trips.mapIndexed { i, t -> Trip(i, t.pattern, t.service, t.times, t.conditions, t.note) },
    )
}
