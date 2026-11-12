package me.spica27.spicamusic.player.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerStateTest {
    @Test
    fun updatedAtReportsElapsedTimeAndClampsAtZero() {
        val state =
            SleepTimerState(
                durationMs = 60_000L,
                remainingMs = 60_000L,
                deadlineElapsedRealtimeMs = 160_000L,
            )

        assertEquals(45_000L, state.updatedAt(115_000L).remainingMs)
        assertEquals(0L, state.updatedAt(160_001L).remainingMs)
    }

    @Test
    fun updatedAtDoesNotExceedInitialDurationWhenClockMovesBack() {
        val state =
            SleepTimerState(
                durationMs = 30_000L,
                remainingMs = 10_000L,
                deadlineElapsedRealtimeMs = 130_000L,
            )

        assertEquals(30_000L, state.updatedAt(90_000L).remainingMs)
    }

    @Test
    fun fadeLevelStaysFullUntilFadeWindow() {
        val state =
            SleepTimerState(
                durationMs = 60_000L,
                remainingMs = 60_000L,
                deadlineElapsedRealtimeMs = 160_000L,
                fadeOutMs = 30_000L,
            )

        val beforeFade = state.updatedAt(120_000L)
        assertFalse(beforeFade.isFading)
        assertEquals(1f, beforeFade.fadeLevel, 0f)
    }

    @Test
    fun fadeLevelRampsDownToSilenceAtDeadline() {
        val state =
            SleepTimerState(
                durationMs = 60_000L,
                remainingMs = 60_000L,
                deadlineElapsedRealtimeMs = 160_000L,
                fadeOutMs = 30_000L,
            )

        // 恰好进入渐弱窗口时音量仍是满值，音量曲线在这一点连续。
        val fadeStart = state.updatedAt(130_000L)
        assertTrue(fadeStart.isFading)
        assertEquals(1f, fadeStart.fadeLevel, 0f)

        // 剩一半渐弱时长时音量减半。
        assertEquals(0.5f, state.updatedAt(145_000L).fadeLevel, 0.0001f)

        // 到点时音量已归零，随后的暂停是无声的。
        assertEquals(0f, state.updatedAt(160_000L).fadeLevel, 0f)
    }

    @Test
    fun updatedAtKeepsFadeWindow() {
        val state =
            SleepTimerState(
                durationMs = 60_000L,
                remainingMs = 60_000L,
                deadlineElapsedRealtimeMs = 160_000L,
                fadeOutMs = 30_000L,
            )

        assertEquals(30_000L, state.updatedAt(115_000L).fadeOutMs)
    }

    @Test
    fun zeroFadeOutDisablesFading() {
        val state =
            SleepTimerState(
                durationMs = 60_000L,
                remainingMs = 1_000L,
                deadlineElapsedRealtimeMs = 160_000L,
            )

        assertFalse(state.isFading)
        assertEquals(1f, state.fadeLevel, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDurationIsRejected() {
        SleepTimerState(
            durationMs = 0L,
            remainingMs = 0L,
            deadlineElapsedRealtimeMs = 100L,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun fadeWindowLongerThanDurationIsRejected() {
        SleepTimerState(
            durationMs = 10_000L,
            remainingMs = 10_000L,
            deadlineElapsedRealtimeMs = 100_000L,
            fadeOutMs = 30_000L,
        )
    }
}
