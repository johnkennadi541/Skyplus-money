package com.example.core.designsystem.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.example.core.designsystem.theme.SkyplusMotion

/**
 * Motion foundation for Stage 1.
 *
 * Three rules keep this app calm: nothing moves longer than 420ms, nothing loops except the
 * skeleton pulse, and every animation has a static fallback when the system disables animations.
 */

/**
 * Press feedback for a tappable card or tile: a 2% shrink, nothing more. Pass the same
 * [interactionSource] the `clickable` modifier uses, otherwise the pressed state never changes.
 */
fun Modifier.skyplusPressScale(
  interactionSource: MutableInteractionSource,
  pressedScale: Float = 0.98f,
): Modifier = composed {
  val pressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (pressed) pressedScale else 1f,
      animationSpec = SkyplusMotion.standardSpec(SkyplusMotion.durationShort),
      label = "pressScale",
    )
  this.graphicsLayer {
    scaleX = scale
    scaleY = scale
  }
}

/** Fade plus a short upward slide, used when a list or a card first appears. */
@Composable
fun SkyplusFadeSlideIn(
  visible: Boolean,
  modifier: Modifier = Modifier,
  delayMillis: Int = 0,
  content: @Composable () -> Unit,
) {
  val entranceSpec = tween<Float>(
    durationMillis = SkyplusMotion.durationMedium,
    delayMillis = delayMillis.coerceAtLeast(0),
    easing = SkyplusMotion.standard,
  )
  val slideSpec = tween<Int>(
    durationMillis = SkyplusMotion.durationMedium,
    delayMillis = delayMillis.coerceAtLeast(0),
    easing = SkyplusMotion.emphasized,
  )
  AnimatedVisibility(
    visible = visible,
    modifier = modifier,
    enter =
      fadeIn(animationSpec = entranceSpec) +
        slideInVertically(
          animationSpec = slideSpec,
          initialOffsetY = { fullHeight -> fullHeight / 12 },
        ),
    exit =
      fadeOut(animationSpec = SkyplusMotion.standardSpec(SkyplusMotion.durationShort)) +
        slideOutVertically(
          animationSpec = SkyplusMotion.standardSpec(SkyplusMotion.durationShort),
          targetOffsetY = { fullHeight -> -fullHeight / 12 },
        ),
  ) {
    content()
  }
}
