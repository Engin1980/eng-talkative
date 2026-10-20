package cz.talkative.app.data

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.domain.drawing.DrawingBoard
import cz.talkative.app.domain.drawing.DrawingColors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Měření výplně celého plátna na skutečném (emulovaném) zařízení; čas se zapíše do logcatu (štítek `DrawingPerf`). */
@RunWith(AndroidJUnit4::class)
class DrawingPerformanceTest {

    @Test
    fun fillingTheWholeCanvas_takesTensOfMilliseconds() = runBlocking {
        val board = DrawingBoard()
        board.fill(0, 0, DrawingColors.PALETTE[1]) // zahřátí JIT
        board.undo()

        val times = (1..5).map {
            val start = System.nanoTime()
            val filled = board.fill(0, 0, DrawingColors.PALETTE[it])
            val millis = (System.nanoTime() - start) / 1_000_000
            assertEquals(512 * 512, filled)
            board.undo()
            millis
        }

        Log.i("DrawingPerf", "výplň celého plátna 512x512 (ms): $times")
        assertTrue("výplň trvala $times ms", times.max() < 500)
    }
}
