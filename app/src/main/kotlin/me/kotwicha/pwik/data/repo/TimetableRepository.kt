package me.kotwicha.pwik.data.repo

import android.content.Context
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.kotwicha.pwik.core.importer.Feed
import me.kotwicha.pwik.core.importer.FeedBuilder
import me.kotwicha.pwik.core.importer.Gazetteer
import me.kotwicha.pwik.core.importer.ShapeBuilder
import me.kotwicha.pwik.core.importer.SourceInfo
import me.kotwicha.pwik.core.pdf.TimetablePdfParser
import me.kotwicha.pwik.core.timetable.Network
import me.kotwicha.pwik.data.db.FeedInfoEntity
import me.kotwicha.pwik.data.db.PwikDatabase
import me.kotwicha.pwik.data.sync.PdfText

data class FeedSource(
    val url: String,
    val fileName: String,
    val origin: String,
    val etag: String? = null,
    val lastModified: String? = null,
)

data class FeedInfo(
    val sourceUrl: String,
    val fileName: String,
    val validFrom: LocalDate?,
    val importedAt: Long,
    val sha256: String,
)

sealed interface TimetableState {
    data object Loading : TimetableState
    data object Missing : TimetableState
    data class Ready(val network: Network, val info: FeedInfo) : TimetableState
    data class Failed(val message: String) : TimetableState
}

class ImportException(message: String) : Exception(message)

class TimetableRepository(
    private val context: Context,
    private val db: PwikDatabase,
    private val maps: MapRepository,
    scope: CoroutineScope,
) {
    private val dao = db.timetable()
    private val importLock = Mutex()
    private val _state = MutableStateFlow<TimetableState>(TimetableState.Loading)
    val state: StateFlow<TimetableState> = _state.asStateFlow()

    private val gazetteer: Gazetteer by lazy {
        Gazetteer.decode(context.assets.open("seed/gazetteer.json").bufferedReader().use { it.readText() })
    }

    init {
        scope.launch(Dispatchers.IO) {
            var loadedStamp: Long? = null
            dao.infoFlow().distinctUntilChanged().collect { info ->
                runCatching {
                    val ready = _state.value as? TimetableState.Ready
                    when {
                        info == null -> _state.value = TimetableState.Missing
                        info.importedAt != loadedStamp || ready == null -> {
                            load()
                            loadedStamp = info.importedAt
                        }
                        else -> _state.value = ready.copy(info = info.toFeedInfo())
                    }
                }.onFailure { e ->
                    _state.value = TimetableState.Failed(e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    suspend fun currentInfo(): FeedInfoEntity? = dao.info()

    suspend fun updateSource(url: String, etag: String?, lastModified: String?) = dao.updateSource(url, etag, lastModified)

    private suspend fun load() {
        val info = dao.info() ?: return
        val network = withContext(Dispatchers.Default) { FeedStore.read(dao).toNetwork() }
        _state.value = TimetableState.Ready(network, info.toFeedInfo())
    }

    suspend fun import(pdf: ByteArray, source: FeedSource): Feed = importLock.withLock {
        withContext(Dispatchers.Default) {
            val pages = PdfText.pages(context, pdf)
            val doc = TimetablePdfParser().parse(pages)
            val bundle = maps.get()
            val raw = FeedBuilder(gazetteer, bundle.streets).build(doc)
            validate(raw)
            val network = raw.toNetwork()
            val shapes = ShapeBuilder(bundle.drive)
            val feed = raw.copy(patterns = raw.patterns.mapIndexed { i, p ->
                val (shape, breaks) = shapes.build(p.stops.indices.map { network.platformPoint(i, it) })
                p.copy(shape = shape, shapeBreaks = breaks)
            })
            val info = FeedInfoEntity(
                sourceUrl = source.url,
                fileName = source.fileName,
                sha256 = sha256(pdf),
                validFrom = SourceInfo.dateIn(source.fileName)?.toString(),
                importedAt = System.currentTimeMillis(),
                origin = source.origin,
                etag = source.etag,
                lastModified = source.lastModified,
                warnings = feed.warnings.joinToString("\n"),
            )
            dao.replace(FeedStore.toRows(feed, info))
            feed
        }
    }

    private fun validate(feed: Feed) {
        if (feed.lines.isEmpty()) throw ImportException("w pliku nie znaleziono żadnej linii")
        if (feed.trips.size < 20) throw ImportException("w pliku jest za mało kursów (${feed.trips.size})")
        val missing = feed.warnings.count { it.contains("nie znaleziono") }
        if (missing > feed.stops.size / 4) throw ImportException("nie udało się dopasować $missing przystanków")
    }

    companion object {
        const val ORIGIN_WEB = "web"

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        private fun FeedInfoEntity.toFeedInfo() = FeedInfo(
            sourceUrl = sourceUrl,
            fileName = fileName,
            validFrom = validFrom?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            importedAt = importedAt,
            sha256 = sha256,
        )
    }
}
