package me.kotwicha.pwik.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

class FeedRows(
    val info: FeedInfoEntity,
    val stops: List<StopEntity>,
    val platforms: List<PlatformEntity>,
    val lines: List<LineEntity>,
    val patterns: List<PatternEntity>,
    val patternStops: List<PatternStopEntity>,
    val services: List<ServiceEntity>,
    val trips: List<TripEntity>,
    val stopTimes: List<StopTimeEntity>,
)

@Dao
abstract class TimetableDao {
    @Query("SELECT * FROM feed_info WHERE id = 0")
    abstract suspend fun info(): FeedInfoEntity?

    @Query("SELECT * FROM feed_info WHERE id = 0")
    abstract fun infoFlow(): Flow<FeedInfoEntity?>

    @Query("SELECT * FROM stop ORDER BY id")
    abstract suspend fun stops(): List<StopEntity>

    @Query("SELECT * FROM platform ORDER BY stopId, idx")
    abstract suspend fun platforms(): List<PlatformEntity>

    @Query("SELECT * FROM line ORDER BY id")
    abstract suspend fun lines(): List<LineEntity>

    @Query("SELECT * FROM pattern ORDER BY id")
    abstract suspend fun patterns(): List<PatternEntity>

    @Query("SELECT * FROM pattern_stop ORDER BY patternId, seq")
    abstract suspend fun patternStops(): List<PatternStopEntity>

    @Query("SELECT * FROM service ORDER BY id")
    abstract suspend fun services(): List<ServiceEntity>

    @Query("SELECT * FROM trip ORDER BY id")
    abstract suspend fun trips(): List<TripEntity>

    @Query("SELECT * FROM stop_time ORDER BY tripId, seq")
    abstract suspend fun stopTimes(): List<StopTimeEntity>

    @Insert protected abstract suspend fun insertInfo(e: FeedInfoEntity)
    @Insert protected abstract suspend fun insertStops(e: List<StopEntity>)
    @Insert protected abstract suspend fun insertPlatforms(e: List<PlatformEntity>)
    @Insert protected abstract suspend fun insertLines(e: List<LineEntity>)
    @Insert protected abstract suspend fun insertPatterns(e: List<PatternEntity>)
    @Insert protected abstract suspend fun insertPatternStops(e: List<PatternStopEntity>)
    @Insert protected abstract suspend fun insertServices(e: List<ServiceEntity>)
    @Insert protected abstract suspend fun insertTrips(e: List<TripEntity>)
    @Insert protected abstract suspend fun insertStopTimes(e: List<StopTimeEntity>)

    @Query("DELETE FROM feed_info") protected abstract suspend fun clearInfo()
    @Query("DELETE FROM stop") protected abstract suspend fun clearStops()
    @Query("DELETE FROM platform") protected abstract suspend fun clearPlatforms()
    @Query("DELETE FROM line") protected abstract suspend fun clearLines()
    @Query("DELETE FROM pattern") protected abstract suspend fun clearPatterns()
    @Query("DELETE FROM pattern_stop") protected abstract suspend fun clearPatternStops()
    @Query("DELETE FROM service") protected abstract suspend fun clearServices()
    @Query("DELETE FROM trip") protected abstract suspend fun clearTrips()
    @Query("DELETE FROM stop_time") protected abstract suspend fun clearStopTimes()

    @Query("UPDATE feed_info SET sourceUrl = :url, etag = :etag, lastModified = :lastModified WHERE id = 0")
    abstract suspend fun updateSource(url: String, etag: String?, lastModified: String?)

    @Transaction
    open suspend fun replace(rows: FeedRows) {
        clearStopTimes()
        clearTrips()
        clearServices()
        clearPatternStops()
        clearPatterns()
        clearLines()
        clearPlatforms()
        clearStops()
        clearInfo()
        insertStops(rows.stops)
        insertPlatforms(rows.platforms)
        insertLines(rows.lines)
        insertPatterns(rows.patterns)
        insertPatternStops(rows.patternStops)
        insertServices(rows.services)
        insertTrips(rows.trips)
        insertStopTimes(rows.stopTimes)
        insertInfo(rows.info)
    }
}

@Dao
interface PlacesDao {
    @Query("SELECT * FROM place_usage ORDER BY lastUsed DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<PlaceUsageEntity>>

    @Query("SELECT * FROM place_usage WHERE kind = 'stop' ORDER BY lastUsed DESC LIMIT :limit")
    fun recentStops(limit: Int): Flow<List<PlaceUsageEntity>>

    @Query("SELECT * FROM place_usage WHERE `key` = :key")
    suspend fun get(key: String): PlaceUsageEntity?

    @Upsert
    suspend fun upsert(e: PlaceUsageEntity)

    @Query("DELETE FROM place_usage WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM place_usage")
    suspend fun clear()
}
