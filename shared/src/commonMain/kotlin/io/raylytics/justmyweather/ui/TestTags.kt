package io.raylytics.justmyweather.ui

import androidx.compose.ui.Modifier

/**
 * Makes the testTags under this node visible to UI tests. Needed again inside
 * a Dialog, which renders in its own window outside the root that set it.
 * Android: exposes them as resource-ids (what Maestro and UiAutomator read).
 * iOS: nothing to do — Compose already publishes a testTag as the view's
 * accessibilityIdentifier, which is what XCUITest and Maestro read there.
 */
expect fun Modifier.exposeTestTags(): Modifier
