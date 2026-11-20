package me.kotwicha.pwik.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "feed_info")
data class FeedInfoEntity(
    @PrimaryKey val id: Int = 0,
    val sourceUrl: String,
    val fileName: String,
    val sha256: String,
    val validFrom: String?,
    val importedAt: Long,
    val origin: String,
    val etag: String?,
    val lastModified: String?,
    val warnings: String,
)

@Entity(tableName = "stop", indices = [Index(value = ["key"], unique = true)])
data class StopEntity(
    @PrimaryKey val id: Int,
    val key: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val approximate: Boolean,
    val aliases: String,
)

@Entity(tableName = "platform", primaryKeys = ["stopId", "idx"])
data class PlatformEntity(
    val stopId: Int,
    val idx: Int,
    val code: String,
    val lat: Double,
    val lon: Double,
)

@Entity(tableName = "line")
data class LineEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val title: String,
)

@Entity(tableName = "pattern")
data class PatternEntity(
    @PrimaryKey val id: Int,
    val lineId: Int,
    val shape: ByteArray,
)

@Entity(tableName = "pattern_stop", primaryKeys = ["patternId", "seq"])
data class PatternStopEntity(
    val patternId: Int,
    val seq: Int,
    val stopId: Int,
    val platform: Int,
    val shapeBreak: Int,
)

@Entity(tableName = "service")
data class ServiceEntity(
    @PrimaryKey val id: Int,
    val days: Int,
    val schoolDaysOnly: Boolean,
    val notInSummerHolidays: Boolean,
    val onlyInSummerHolidays: Boolean,
    val seasonStart: Int,
    val seasonEnd: Int,
    val closedOn: Int,
    val cutoffOn: Int,
    val cutoffMinute: Int,
)

@Entity(tableName = "trip", indices = [Index("patternId")])
data class TripEntity(
    @PrimaryKey val id: Int,
    val patternId: Int,
    val serviceId: Int,
    val note: String?,
)

@Entity(tableName = "stop_time", primaryKeys = ["tripId", "seq"])
data class StopTimeEntity(
    val tripId: Int,
    val seq: Int,
    val minute: Int,
    val conditionId: Int,
)

@Entity(tableName = "place_usage")
data class PlaceUsageEntity(
    @PrimaryKey val key: String,
    val kind: String,
    val label: String,
    val lat: Double,
    val lon: Double,
    val stopKey: String?,
    val lastUsed: Long,
    val uses: Int,
)
