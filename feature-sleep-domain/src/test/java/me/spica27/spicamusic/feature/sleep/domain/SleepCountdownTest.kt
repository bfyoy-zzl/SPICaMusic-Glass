package me.spica27.spicamusic.feature.sleep.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepCountdownTest {
    @Test
    fun `渐弱窗口之前音量系数恒为 1`() {
        val countdown = SleepCountdown(totalMs = 600_000L, remainingMs = 45_000L, fadeOutMs = 30_000L)

        assertFalse(countdown.isFading)
        assertEquals(1f, countdown.fadeScale, 0.0001f)
    }

    @Test
    fun `窗口起点系数恰好为 1，曲线连续`() {
        val countdown = SleepCountdown(totalMs = 600_000L, remainingMs = 30_000L, fadeOutMs = 30_000L)

        assertTrue(countdown.isFading)
        // 边界必须正好是 1，否则进入渐弱的一瞬间会有可听的音量跳变。
        assertEquals(1f, countdown.fadeScale, 0.0001f)
    }

    @Test
    fun `窗口中点系数为 0 点 5`() {
        val countdown = SleepCountdown(totalMs = 600_000L, remainingMs = 15_000L, fadeOutMs = 30_000L)

        assertEquals(0.5f, countdown.fadeScale, 0.0001f)
    }

    @Test
    fun `到点后系数为 0`() {
        val countdown = SleepCountdown(totalMs = 600_000L, remainingMs = 0L, fadeOutMs = 30_000L)

        assertEquals(0f, countdown.fadeScale, 0.0001f)
    }

    @Test
    fun `没有渐弱窗口时不会误判为渐弱中`() {
        val countdown = SleepCountdown(totalMs = 1_000L, remainingMs = 0L, fadeOutMs = 0L)

        assertFalse(countdown.isFading)
        assertEquals(1f, countdown.fadeScale, 0.0001f)
    }

    @Test
    fun `未知音轨类别回退到 OTHER`() {
        assertEquals(SleepStemKind.RAIN, SleepStemKind.fromName("RAIN"))
        assertEquals(SleepStemKind.OTHER, SleepStemKind.fromName("SOMETHING_NEW"))
    }
}
