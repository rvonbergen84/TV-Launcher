package io.github.rvonbergen84.tvlauncher

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

/** The hold-OK menu must survive the release of the button that opened it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private var result: Boolean? = null

    private fun open() {
        compose.setContent {
            MaterialTheme {
                AppMenu(
                    AppEntry("sample.youtube", "YouTube", null, false),
                    favorites = listOf("sample.youtube", "sample.netflix"),
                ) { result = it }
            }
        }
        compose.onNodeWithText("Move right").assertExists()
    }

    /** Sends a raw key event to the menu's window, the way the TV delivers it. */
    private fun send(action: Int, repeat: Int) = compose.runOnIdle {
        val window = compose.onNodeWithText("Move right").fetchSemanticsNode().root!!
        window.sendKeyEvent(ComposeKeyEvent(KeyEvent(0, 0, action, KeyEvent.KEYCODE_DPAD_CENTER, repeat)))
    }

    @Test
    fun releaseOfHeldButtonDoesNothing() {
        open()
        send(KeyEvent.ACTION_DOWN, repeat = 3) // OK still held, repeating
        send(KeyEvent.ACTION_UP, repeat = 0) // ...then let go
        compose.waitForIdle()
        assertNull("menu should still be open", result)
        compose.onNodeWithText("Move right").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun freshPressPicksOption() {
        open()
        compose.onNodeWithText("Move right").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(true, result)
    }
}
