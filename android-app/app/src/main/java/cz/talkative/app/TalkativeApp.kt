package cz.talkative.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import cz.talkative.app.data.sync.SyncTrigger
import cz.talkative.app.domain.usecase.CleanUpOrphanFilesUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class TalkativeApp : Application(), Configuration.Provider {

    @Inject
    lateinit var cleanUpOrphanFiles: CleanUpOrphanFilesUseCase

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncTrigger: SyncTrigger

    // WorkManager se inicializuje ručně (výchozí inicializátor je v manifestu odebraný), aby uměl vytvářet úlohy přes Hilt.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Úklid po přerušené práci: dočasné fotky z fotoaparátu, stažené piktogramy, kresby a obrázky, které nepatří žádnému slovíčku.
        // Chyba úklidu nesmí shodit aplikaci.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            runCatching { File(cacheDir, "camera").deleteRecursively() }
            runCatching { File(cacheDir, "pictograms").deleteRecursively() }
            runCatching { File(cacheDir, "drawings").deleteRecursively() }
            runCatching { File(cacheDir, "recordings").deleteRecursively() }
            runCatching { File(cacheDir, "sync").deleteRecursively() }
            runCatching { cleanUpOrphanFiles() }
            // Synchronizace až po úklidu, aby úklid osiřelých souborů nesmazal právě stažená média.
            runCatching { syncTrigger.start(scope) }
        }
    }
}
