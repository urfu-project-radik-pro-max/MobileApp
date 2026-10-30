package com.example.mobilereader.ui.reader

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow

internal data class VolumeTurnEvent(
    val forward: Boolean,
    val pressId: Long,
    val repeat: Boolean,
)

internal class VolumeTurnController(
    private val nowMillis: () -> Long,
) {
    private data class ActivePress(
        val id: Long,
        var lastTurnMillis: Long,
    )

    private val eventChannel = Channel<VolumeTurnEvent>(Channel.UNLIMITED)
    val events: Flow<VolumeTurnEvent> = eventChannel.receiveAsFlow()

    private var nextPressId = 0L
    private var volumeDownPress: ActivePress? = null
    private var volumeUpPress: ActivePress? = null
    private var pendingRepeat: VolumeTurnEvent? = null

    fun press(forward: Boolean) {
        nextPressId += 1
        val press = ActivePress(nextPressId, nowMillis())
        if (forward) {
            volumeDownPress = press
        } else {
            volumeUpPress = press
        }
        eventChannel.trySend(VolumeTurnEvent(forward, press.id, repeat = false))
    }

    fun repeat(forward: Boolean) {
        val press = if (forward) volumeDownPress else volumeUpPress
        if (press == null) {
            return
        }
        if (pendingRepeat != null) {
            return
        }

        val now = nowMillis()
        if (now - press.lastTurnMillis < REPEAT_INTERVAL_MILLIS) {
            return
        }

        press.lastTurnMillis = now
        val event = VolumeTurnEvent(forward, press.id, repeat = true)
        pendingRepeat = event
        eventChannel.trySend(event)
    }

    fun release(forward: Boolean) {
        if (forward) {
            volumeDownPress = null
        } else {
            volumeUpPress = null
        }
    }

    fun releaseAll() {
        volumeDownPress = null
        volumeUpPress = null
    }

    fun shouldTurn(event: VolumeTurnEvent): Boolean {
        if (!event.repeat) {
            return true
        }

        if (pendingRepeat == event) {
            pendingRepeat = null
        }
        val press = if (event.forward) volumeDownPress else volumeUpPress
        return press?.id == event.pressId
    }

    private companion object {
        const val REPEAT_INTERVAL_MILLIS = 220L
    }
}

internal suspend fun VolumeTurnController.collectTurns(turn: suspend (forward: Boolean) -> Unit) {
    events.collect { event ->
        if (!shouldTurn(event)) {
            return@collect
        }

        try {
            turn(event.forward)
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
        }
    }
}
