package com.example.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * SKYPLUS MONEY — Stage 1 design tokens.
 *
 * Every screen and component must read its sizes, radii, elevation, icon sizes and motion from
 * these objects instead of hardcoding `16.dp` or `300` inline. Spacing lives in [OceanSpacing]
 * (kept where it always was so existing screens keep compiling).
 */
object SkyplusRadius {
  val xs: Dp = 8.dp
  val sm: Dp = 12.dp
  val md: Dp = 16.dp
  val lg: Dp = 20.dp
  val xl: Dp = 28.dp
  val pill: Dp = 100.dp
}

object SkyplusElevation {
  val none: Dp = 0.dp
  val low: Dp = 1.dp
  val medium: Dp = 2.dp
  val high: Dp = 6.dp
  val overlay: Dp = 12.dp
}

object SkyplusIconSize {
  val xs: Dp = 16.dp
  val sm: Dp = 20.dp
  val md: Dp = 24.dp
  val lg: Dp = 32.dp
  val xl: Dp = 48.dp

  /** Material accessibility minimum. Every tappable icon must reach this size. */
  val minTouchTarget: Dp = 48.dp
}

object SkyplusOpacity {
  const val disabled: Float = 0.38f
  const val muted: Float = 0.62f
  const val border: Float = 0.75f
  const val subtleFill: Float = 0.12f
  const val pressedOverlay: Float = 0.08f
}

object SkyplusSizes {
  val buttonHeight: Dp = 52.dp
  val fieldMinHeight: Dp = 56.dp
  val cardMinHeight: Dp = 88.dp
  val screenMaxContentWidth: Dp = 720.dp
  val listItemMinHeight: Dp = 56.dp
}

/**
 * Motion standard. Keep animations short and purposeful: this is a finance app, not a showreel.
 * Durations are milliseconds; easings follow the Material 3 curve family without copying it.
 */
object SkyplusMotion {
  const val durationInstant: Int = 80
  const val durationShort: Int = 160
  const val durationMedium: Int = 260
  const val durationLong: Int = 420
  const val skeletonPulse: Int = 1200

  val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
  val emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
  val decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
  val accelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

  fun <T> standardSpec(durationMillis: Int = durationMedium): FiniteAnimationSpec<T> =
    tween(durationMillis = durationMillis, easing = standard)

  fun <T> emphasizedSpec(durationMillis: Int = durationLong): FiniteAnimationSpec<T> =
    tween(durationMillis = durationMillis, easing = emphasized)
}
