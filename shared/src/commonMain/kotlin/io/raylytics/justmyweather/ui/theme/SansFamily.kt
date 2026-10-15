package io.raylytics.justmyweather.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/**
 * The app's sans-serif — Roboto on both platforms. Android's system sans IS
 * Roboto. iOS's system sans is SF Pro, which sets wider: the glance's tiles
 * and the action row are sized against Roboto's metrics (and measured by the
 * instrumented tests), and in SF they truncate and wrap. So iOS bundles
 * Roboto (SIL OFL, shared/src/iosMain/ROBOTO-OFL.txt) — a deliberate
 * divergence in docs/PARITY.md, until the layouts tolerate any face.
 */
@Composable
expect fun sansFamily(): FontFamily
