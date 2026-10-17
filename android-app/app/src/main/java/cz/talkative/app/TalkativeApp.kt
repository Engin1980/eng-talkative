package cz.talkative.app

import android.app.Application
import cz.talkative.app.domain.usecase.CleanUpOrphanImagesUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TalkativeApp : Application() {

    @Inject
    lateinit var cleanUpOrphanImages: CleanUpOrphanImagesUseCase

    override fun onCreate() {
        super.onCreate()
        // Úklid souborů po přerušeném mazání; chyba úklidu nesmí shodit aplikaci.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { cleanUpOrphanImages() }
        }
    }
}
