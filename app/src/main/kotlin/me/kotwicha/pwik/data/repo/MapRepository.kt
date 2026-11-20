package me.kotwicha.pwik.data.repo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.kotwicha.pwik.core.map.MapData
import me.kotwicha.pwik.core.map.StreetGraph
import me.kotwicha.pwik.core.map.Streets

class MapBundle(val map: MapData) {
    val streets: Streets by lazy { Streets(map) }
    val walk: StreetGraph by lazy { StreetGraph.build(map, walking = true) }
    val drive: StreetGraph by lazy { StreetGraph.build(map, walking = false) }
}

class MapRepository(private val context: Context) {
    private val mutex = Mutex()

    @Volatile
    private var bundle: MapBundle? = null

    val loaded: MapBundle? get() = bundle

    suspend fun get(): MapBundle = bundle ?: mutex.withLock {
        bundle ?: withContext(Dispatchers.Default) {
            val bytes = context.assets.open("seed/map.bin").use { it.readBytes() }
            MapBundle(MapData.decode(bytes))
        }.also { bundle = it }
    }
}
