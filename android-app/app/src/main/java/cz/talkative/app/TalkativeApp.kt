package cz.talkative.app

import android.app.Application
import cz.talkative.app.domain.usecase.CleanUpOrphanImagesUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class TalkativeApp : Application() {

    @Inject
    lateinit var cleanUpOrphanImages: CleanUpOrphanImagesUseCase

    override fun onCreate() {
        super.onCreate()
        // Úklid po přerušené práci: dočasné fotky z fotoaparátu a obrázky, které nepatří žádnému slovíčku.
        // Chyba úklidu nesmí shodit aplikaci.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { File(cacheDir, "camera").deleteRecursively() }
            runCatching { cleanUpOrphanImages() }
        }
    }
}
