package cz.talkative.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cz.talkative.app.domain.sync.RunSyncUseCase
import cz.talkative.app.domain.sync.SyncRunResult
import cz.talkative.app.domain.sync.SyncScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Úloha na pozadí: jedna synchronizace. Chyby sítě a serveru se opakují s exponenciálním zpožděním, nejvýš [MAX_ATTEMPTS]krát. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runSync: RunSyncUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (runSync()) {
        is SyncRunResult.Retry -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        else -> Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "sync"
        const val MAX_ATTEMPTS = 5
    }
}

/**
 * Plánovač nad WorkManagerem: jedna unikátní úloha s omezením na připojení k síti. Nová žádost nahradí předchozí:
 * čekající (série úprav v administraci se sloučí do jedné synchronizace), ale i právě běžící, která se zruší a začne znovu.
 * Zrušení uprostřed je bezpečné, protože místní data se mění až v posledním kroku a slučování je idempotentní.
 * Exponenciální backoff při selhání.
 */
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncScheduler {

    override fun requestSync(delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SyncWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val BACKOFF_SECONDS = 30L
    }
}
