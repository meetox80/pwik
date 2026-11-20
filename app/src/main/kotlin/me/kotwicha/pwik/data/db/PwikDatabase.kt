package me.kotwicha.pwik.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FeedInfoEntity::class,
        StopEntity::class,
        PlatformEntity::class,
        LineEntity::class,
        PatternEntity::class,
        PatternStopEntity::class,
        ServiceEntity::class,
        TripEntity::class,
        StopTimeEntity::class,
        PlaceUsageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PwikDatabase : RoomDatabase() {
    abstract fun timetable(): TimetableDao
    abstract fun places(): PlacesDao

    companion object {
        fun create(context: Context): PwikDatabase =
            Room.databaseBuilder(context, PwikDatabase::class.java, "pwik.db").build()
    }
}
