package cz.talkative.app.testing

import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import java.io.FileInputStream

/**
 * Po dobu testu vypne softwarovou klávesnici. Dialog s textovým polem ji jinak otevře sám a zmenší se nad ní,
 * takže by se výsledek klepání a kontrol lišil podle časování animace klávesnice. Po testu se klávesnice vrátí.
 * Funguje jen v instrumentovaných testech (potřebuje shell přes UiAutomation).
 */
class NoSoftKeyboardRule : ExternalResource() {
    private var disabled: List<String> = emptyList()

    override fun before() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val enabled = shell(automation, "ime list -s").lines().map { it.trim() }.filter { it.isNotEmpty() }
        enabled.forEach { shell(automation, "ime disable $it") }
        disabled = enabled
    }

    override fun after() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        disabled.forEach { shell(automation, "ime enable $it") }
    }

    private fun shell(automation: UiAutomation, command: String): String {
        val descriptor: ParcelFileDescriptor = automation.executeShellCommand(command)
        return FileInputStream(descriptor.fileDescriptor).use { it.readBytes().decodeToString() }
    }
}
