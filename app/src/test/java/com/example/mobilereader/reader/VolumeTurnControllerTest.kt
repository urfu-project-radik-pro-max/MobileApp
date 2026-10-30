package com.example.mobilereader.reader

import com.example.mobilereader.ui.reader.VolumeTurnController
import com.example.mobilereader.ui.reader.VolumeTurnEvent
import com.example.mobilereader.ui.reader.collectTurns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VolumeTurnControllerTest {
    @Test
    fun firstPressIsImmediateAndRepeatsAreRateLimited() = runTest {
        var nowMillis = 0L
        val controller = VolumeTurnController { nowMillis }
        val received = mutableListOf<VolumeTurnEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.events.collect { received.add(it) }
        }

        controller.press(forward = true)
        assertEquals(1, received.size)
        assertTrue(received[0].forward)
        assertFalse(received[0].repeat)

        nowMillis = 100L
        controller.repeat(forward = true)
        assertEquals(1, received.size)

        nowMillis = 220L
        controller.repeat(forward = true)
        assertEquals(2, received.size)
        assertTrue(received[1].repeat)

        nowMillis = 300L
        controller.repeat(forward = true)
        assertEquals(2, received.size)
    }

    @Test
    fun releaseInvalidatesPendingRepeatsButKeepsShortPress() = runTest {
        var nowMillis = 0L
        val controller = VolumeTurnController { nowMillis }
        val received = mutableListOf<VolumeTurnEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.events.collect { received.add(it) }
        }

        controller.press(forward = false)
        nowMillis = 220L
        controller.repeat(forward = false)
        controller.release(forward = false)

        assertEquals(2, received.size)
        assertTrue(controller.shouldTurn(received[0]))
        assertFalse(controller.shouldTurn(received[1]))

        controller.press(forward = false)
        assertTrue(controller.shouldTurn(received[2]))
        assertFalse(controller.shouldTurn(received[1]))
    }

    @Test
    fun directionsKeepIndependentPresses() = runTest {
        var nowMillis = 0L
        val controller = VolumeTurnController { nowMillis }
        val received = mutableListOf<VolumeTurnEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.events.collect { received.add(it) }
        }

        controller.press(forward = true)
        controller.press(forward = false)
        nowMillis = 220L
        controller.repeat(forward = true)
        assertTrue(controller.shouldTurn(received[2]))
        controller.repeat(forward = false)
        controller.release(forward = true)

        assertFalse(controller.shouldTurn(received[2]))
        assertTrue(controller.shouldTurn(received[3]))
    }

    @Test
    fun busyConsumerKeepsOnlyOnePendingRepeat() = runTest {
        var nowMillis = 0L
        val controller = VolumeTurnController { nowMillis }

        controller.press(forward = true)
        repeat(20) {
            nowMillis += 220L
            controller.repeat(forward = true)
        }

        val received = mutableListOf<VolumeTurnEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.events.collect { received.add(it) }
        }

        assertEquals(2, received.size)
        controller.release(forward = true)
        assertTrue(controller.shouldTurn(received[0]))
        assertFalse(controller.shouldTurn(received[1]))
    }

    @Test
    fun gestureCancellationDoesNotStopLaterKeyTurns() = runTest {
        val controller = VolumeTurnController { 0L }
        val directions = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.collectTurns { forward ->
                directions.add(forward)
                if (directions.size == 1) {
                    throw CancellationException("Gesture interrupted the animation")
                }
            }
        }

        controller.press(forward = true)
        controller.release(forward = true)
        controller.press(forward = false)

        assertEquals(listOf(true, false), directions)
    }
}
