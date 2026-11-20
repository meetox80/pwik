package me.kotwicha.pwik.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.kotwicha.pwik.PwikApp
import me.kotwicha.pwik.core.importer.SourceInfo
import me.kotwicha.pwik.data.repo.FeedSource
import me.kotwicha.pwik.data.repo.ImportException
import me.kotwicha.pwik.data.repo.SyncOutcome
import me.kotwicha.pwik.data.repo.SyncStatusStore
import me.kotwicha.pwik.data.repo.TimetableRepository

class SyncResult(val outcome: SyncOutcome, val message: String?, val retryable: Boolean = false)

class TimetableSync(
    private val http: Http,
    private val timetable: TimetableRepository,
    private val status: SyncStatusStore,
) {
    suspend fun run(force: Boolean): SyncResult {
        status.started()
        val result = try {
            attempt(force)
        } catch (e: ImportException) {
            SyncResult(SyncOutcome.FAILED, "Plik z rozkładem ma nieznany układ: ${e.message}")
        } catch (e: HttpException) {
            SyncResult(SyncOutcome.FAILED, "Strona PWiK odpowiedziała błędem ${e.code}", retryable = e.code >= 500)
        } catch (e: IOException) {
            SyncResult(SyncOutcome.FAILED, "Brak połączenia ze stroną PWiK", retryable = true)
        } catch (e: Exception) {
            SyncResult(SyncOutcome.FAILED, e.message ?: e.javaClass.simpleName)
        }
        status.finished(result.outcome, result.message)
        return result
    }

    private suspend fun attempt(force: Boolean): SyncResult {
        val page = http.get(TimetableLinks.PAGE_URL)
        val html = String(page.body, Charsets.UTF_8)
        var links = TimetableLinks.find(html, page.finalUrl)
        if (links.isEmpty()) {
            for (sub in TimetableLinks.subpages(html, page.finalUrl)) {
                val subPage = http.get(sub)
                links = TimetableLinks.find(String(subPage.body, Charsets.UTF_8), subPage.finalUrl)
                if (links.isNotEmpty()) break
            }
        }
        val link = links.firstOrNull()
            ?: return SyncResult(SyncOutcome.FAILED, "Na stronie PWiK nie ma pliku z rozkładem")
        val current = timetable.currentInfo()
        val sameFile = current?.sourceUrl == link.url
        val pdf = http.get(
            link.url,
            etag = current?.etag?.takeIf { sameFile && !force },
            lastModified = current?.lastModified?.takeIf { sameFile && !force },
        )
        if (pdf.notModified) return SyncResult(SyncOutcome.UNCHANGED, null)
        if (!pdf.body.copyOfRange(0, minOf(5, pdf.body.size)).contentEquals("%PDF-".toByteArray())) {
            return SyncResult(SyncOutcome.FAILED, "Pobrany plik nie jest dokumentem PDF")
        }
        val sha = TimetableRepository.sha256(pdf.body)
        if (current != null && current.sha256 == sha) {
            timetable.updateSource(link.url, pdf.etag, pdf.lastModified)
            return SyncResult(SyncOutcome.UNCHANGED, null)
        }
        timetable.import(
            pdf.body,
            FeedSource(
                url = link.url,
                fileName = SourceInfo.fileName(pdf.finalUrl).ifBlank { SourceInfo.fileName(link.url) },
                origin = TimetableRepository.ORIGIN_WEB,
                etag = pdf.etag,
                lastModified = pdf.lastModified,
            )
        )
        return SyncResult(SyncOutcome.UPDATED, null)
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PwikApp
        val result = app.container.sync.run(force = inputData.getBoolean(KEY_FORCE, false))
        return when {
            result.outcome != SyncOutcome.FAILED -> Result.success()
            result.retryable && runAttemptCount < 4 -> Result.retry()
            else -> Result.failure(workDataOf(KEY_MESSAGE to result.message))
        }
    }

    companion object {
        const val KEY_FORCE = "force"
        const val KEY_MESSAGE = "message"
    }
}

object SyncScheduler {
    private const val MONTHLY = "timetable-monthly"
    private const val NOW = "timetable-now"
    private const val OVERDUE_MS = 31L * 24 * 60 * 60 * 1000

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context, lastSuccess: Long?) {
        val wm = WorkManager.getInstance(context)
        val monthly = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.DAYS)
            .setInitialDelay(30, TimeUnit.DAYS)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(MONTHLY, ExistingPeriodicWorkPolicy.KEEP, monthly)
        if (lastSuccess == null || System.currentTimeMillis() - lastSuccess > OVERDUE_MS) {
            enqueue(context, force = false, policy = ExistingWorkPolicy.KEEP)
        }
    }

    fun syncNow(context: Context) = enqueue(context, force = false, policy = ExistingWorkPolicy.REPLACE)

    private fun enqueue(context: Context, force: Boolean, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_FORCE to force))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, policy, request)
    }

    fun pending(context: Context): Flow<Boolean> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(NOW).map { infos ->
            infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        }
}
