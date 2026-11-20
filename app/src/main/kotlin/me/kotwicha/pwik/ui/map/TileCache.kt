package me.kotwicha.pwik.ui.map

import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.ceil
import me.kotwicha.pwik.core.map.MapData

fun tileKey(level: Int, tx: Int, ty: Int): Long =
    ((level + 64).toLong() shl 48) or ((tx.toLong() and 0xFFFFFF) shl 24) or (ty.toLong() and 0xFFFFFF)

private fun keyLevel(k: Long): Int = (k ushr 48).toInt() - 64
private fun keyX(k: Long): Int = ((k ushr 24) and 0xFFFFFF).toInt()
private fun keyY(k: Long): Int = (k and 0xFFFFFF).toInt()

class TileCache private constructor(
    private val map: MapData,
    private val theme: MapTheme,
    private val overlay: TileOverlay?,
) {
    private val lock = Any()
    private val cache = object : LruCache<Long, Bitmap>(budgetBytes()) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
    }
    private val pending = LinkedHashSet<Long>()
    private val inFlight = HashSet<Long>()
    private val labels = ConcurrentHashMap<Int, LabelLayout>()
    private val workers = 2
    private val executor: ExecutorService = Executors.newFixedThreadPool(workers) { r ->
        Thread(r, "map-tiles").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }
    private val renderers = ThreadLocal.withInitial { TileRenderer(map, theme) }
    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<() -> Unit>()

    @Volatile
    private var closed = false

    fun get(level: Int, tx: Int, ty: Int): Bitmap? = cache.get(tileKey(level, tx, ty))

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun request(keys: List<Long>) {
        synchronized(lock) {
            pending.clear()
            for (k in keys) if (cache.get(k) == null && k !in inFlight) pending.add(k)
        }
        pump()
    }

    fun prefetch(keys: List<Long>) {
        synchronized(lock) {
            for (k in keys) if (cache.get(k) == null && k !in inFlight) pending.add(k)
        }
        pump()
    }

    private fun pump() {
        while (true) {
            val key = synchronized(lock) {
                if (closed || inFlight.size >= workers || pending.isEmpty()) return
                val k = pending.first()
                pending.remove(k)
                inFlight.add(k)
                k
            }
            executor.execute { render(key) }
        }
    }

    private fun render(key: Long) {
        if (closed) return
        val level = keyLevel(key)
        val result = runCatching {
            val layout = labels.getOrPut(level) { LabelLayout.build(map, theme, level) }
            val drawn = renderers.get()!!.render(level, keyX(key), keyY(key), layout, overlay)
            val gpu = drawn.copy(Bitmap.Config.HARDWARE, false)
            if (gpu != null) {
                drawn.recycle()
                gpu
            } else drawn
        }.getOrNull()
        synchronized(lock) {
            inFlight.remove(key)
            if (result != null && !closed) cache.put(key, result)
        }
        if (result != null && !closed) main.post { if (!closed) listeners.toList().forEach { it() } }
        pump()
    }

    private fun close() {
        closed = true
        executor.shutdownNow()
        synchronized(lock) {
            pending.clear()
            cache.evictAll()
        }
    }

    companion object {
        private val slots = HashMap<String, TileCache>()

        @Synchronized
        fun obtain(map: MapData, theme: MapTheme, overlay: TileOverlay? = null): TileCache {
            val key = overlay?.key.orEmpty()
            val current = slots[key]
            if (current != null && current.map === map && current.theme.id == theme.id &&
                current.theme.typeface == theme.typeface && current.theme.boldTypeface == theme.boldTypeface
            ) return current
            val stale = slots.filter { (k, c) -> k == key || c.map !== map || c.theme.id != theme.id || (overlay != null && k.isNotEmpty()) }
            stale.forEach { (k, c) ->
                c.close()
                slots.remove(k)
            }
            return TileCache(map, theme, overlay).also { slots[key] = it }
        }

        private fun budgetBytes(): Int {
            val metrics = Resources.getSystem().displayMetrics
            val smallestTile = TILE_PX * MIN_TILE_SCALE
            val perScreen = (ceil(metrics.widthPixels / smallestTile) + 1) * (ceil(metrics.heightPixels / smallestTile) + 1)
            val tileBytes = TILE_PX.toLong() * TILE_PX * 4
            return (perScreen * 2 * tileBytes).toLong().coerceIn(48L shl 20, 192L shl 20).toInt()
        }
    }
}
