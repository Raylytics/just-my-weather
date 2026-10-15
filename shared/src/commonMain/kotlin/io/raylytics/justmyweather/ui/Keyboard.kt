package io.raylytics.justmyweather.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * A tap on empty space puts the keyboard away.
 *
 * Android's keyboard always has a key that closes it. iOS's number pad has
 * none: no return key, so a field's ImeAction.Done never arrives, and the pad
 * sat over the rest of the Alerts form until the screen was left
 * (just-my-weather-n52). Tapping outside the field is how iPhone users expect
 * to close it, and it costs nothing on Android.
 *
 * Put it on the screen's root. Taps that land on a control are consumed by
 * that control first, so this sees only taps on empty space and never steals
 * a button's or a chip's tap.
 */
fun Modifier.dismissKeyboardOnTap(): Modifier =
    composed {
        val focus = LocalFocusManager.current
        pointerInput(focus) { detectTapGestures(onTap = { focus.clearFocus() }) }
    }
