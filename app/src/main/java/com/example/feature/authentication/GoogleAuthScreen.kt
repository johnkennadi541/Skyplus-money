package com.example.feature.authentication

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.core.designsystem.component.BadgeTone
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.component.OceanStatusBadge
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.data.remote.AuthSignInDiagnostics

@Composable
fun AuthLoadingScreen(modifier: Modifier = Modifier) {
  val extended = OceanTheme.colors
  Scaffold(
    modifier = modifier.fillMaxSize().testTag("auth_loading_screen"),
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
      ) {
        Image(
          painter = painterResource(id = R.drawable.skyplus_money_icon_1795025038411),
          contentDescription = stringResource(R.string.cd_brand_emblem),
          contentScale = ContentScale.Crop,
          modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)),
        )
        CircularProgressIndicator(
          color = extended.gold,
          modifier = Modifier.size(32.dp),
          strokeWidth = 3.dp,
        )
        Text(
          text = stringResource(R.string.auth_loading_session),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
fun GoogleAuthScreen(
  uiState: AuthUiState,
  onContinueWithGoogle: (android.content.Context) -> Unit,
  onDismissFeedback: () -> Unit,
  onToggleTheme: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val extended = OceanTheme.colors

  Scaffold(
    modifier = modifier.fillMaxSize().testTag("google_auth_screen"),
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = OceanSpacing.md, vertical = OceanSpacing.md),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OceanStatusBadge(
            text = stringResource(R.string.auth_screen_badge),
            tone = BadgeTone.GOLD,
          )
          IconButton(
            onClick = onToggleTheme,
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("auth_theme_toggle_button"),
          ) {
            Icon(
              imageVector =
                if (extended.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
              contentDescription = stringResource(R.string.cd_toggle_theme),
              tint = extended.gold,
            )
          }
        }

        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.large,
          border = BorderStroke(1.dp, extended.cardBorder),
          color = Color.Transparent,
        ) {
          Box(
            modifier =
              Modifier.background(
                  Brush.linearGradient(
                    listOf(extended.heroGradientStart, extended.heroGradientEnd)
                  )
                )
                .padding(OceanSpacing.lg)
          ) {
            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
            ) {
              Image(
                painter = painterResource(id = R.drawable.skyplus_money_icon_1795025038411),
                contentDescription = stringResource(R.string.cd_brand_emblem),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)),
              )
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
              )
              Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.titleSmall,
                color = extended.gold,
                textAlign = TextAlign.Center,
              )
              Spacer(modifier = Modifier.height(OceanSpacing.xxs))
              Text(
                text = stringResource(R.string.auth_screen_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
              )
              Text(
                text = stringResource(R.string.auth_screen_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD3E4F4),
                textAlign = TextAlign.Center,
              )
            }
          }
        }

        AnimatedVisibility(visible = uiState.feedbackMessageRes != null) {
          val messageRes = uiState.feedbackMessageRes
          if (messageRes != null) {
            val isCancellation = uiState.isCancellationNotice
            val bg = if (isCancellation) extended.warningContainer else extended.negativeContainer
            val fg = if (isCancellation) extended.warning else extended.negative
            Surface(
              modifier = Modifier.fillMaxWidth().testTag("auth_error_banner"),
              shape = MaterialTheme.shapes.medium,
              color = bg,
              border = BorderStroke(1.dp, fg.copy(alpha = 0.5f)),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(OceanSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector =
                    if (isCancellation) Icons.Rounded.Info else Icons.Rounded.ErrorOutline,
                  contentDescription = null,
                  tint = fg,
                )
                Spacer(modifier = Modifier.width(OceanSpacing.xs))
                Text(
                  text = stringResource(messageRes),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.weight(1f),
                )
                IconButton(
                  onClick = onDismissFeedback,
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.auth_dismiss_message),
                    tint = fg,
                  )
                }
              }
            }
          }
        }

        if (BuildConfig.DEBUG) {
          val diagnostics = uiState.diagnostics
          if (diagnostics != null) {
            AuthSignInDiagnosticsCard(
              diagnostics = diagnostics,
              modifier = Modifier.fillMaxWidth().testTag("auth_debug_diagnostics"),
            )
          }
        }

        OceanCard {
          Button(
            onClick = { onContinueWithGoogle(context) },
            enabled = !uiState.isSigningIn,
            modifier =
              Modifier.fillMaxWidth()
                .height(52.dp)
                .minimumInteractiveComponentSize()
                .testTag("continue_with_google_button"),
            shape = MaterialTheme.shapes.medium,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
              ),
          ) {
            if (uiState.isSigningIn) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp).testTag("auth_loading_indicator"),
                color = MaterialTheme.colorScheme.onSecondary,
                strokeWidth = 2.dp,
              )
              Spacer(modifier = Modifier.width(OceanSpacing.sm))
              Text(
                text = stringResource(R.string.auth_signing_in),
                style = MaterialTheme.typography.labelLarge,
              )
            } else {
              Icon(
                imageVector = Icons.Rounded.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(OceanSpacing.xs))
              Text(
                text = stringResource(R.string.auth_continue_with_google),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }

          Spacer(modifier = Modifier.height(OceanSpacing.md))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.Lock,
              contentDescription = null,
              tint = extended.gold,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(OceanSpacing.xs))
            Text(
              text = stringResource(R.string.auth_facebook_upcoming),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(modifier = Modifier.height(OceanSpacing.xs))
          Text(
            text = stringResource(R.string.auth_consent_upcoming),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

/**
 * DEBUG-ONLY Google Sign-In diagnostics card.
 * Rendered only when BuildConfig.DEBUG is true, so release builds never show it.
 * It shows no tokens, no account identity and no personal data.
 */
@Composable
private fun AuthSignInDiagnosticsCard(
  diagnostics: AuthSignInDiagnostics,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors

  val rows =
    buildList {
      add("Stage" to diagnostics.stage.name)
      add("Outcome" to diagnostics.outcome.name)
      diagnostics.statusCode?.let { add("ApiException statusCode" to it.toString()) }
      diagnostics.exceptionClassName?.let { add("Exception class" to it) }
      diagnostics.credentialExceptionType?.let { add("GetCredentialException type" to it) }
      diagnostics.message?.let { add("Message" to it) }
      if (diagnostics.causeChain.isNotEmpty()) {
        add("Cause chain" to diagnostics.causeChain.joinToString(" -> "))
      }
      diagnostics.firebaseErrorCode?.let { add("Firebase error code" to it) }
      diagnostics.packageName?.let { add("Package" to it) }
      add("default_web_client_id present" to diagnostics.webClientIdConfigured.toString())
      diagnostics.signingSha1?.let { add("APK signing SHA-1" to it) }
      diagnostics.signingSha256?.let { add("APK signing SHA-256" to it) }
    }

  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.medium,
    color = extended.warningContainer,
    border = BorderStroke(1.dp, extended.warning.copy(alpha = 0.5f)),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(OceanSpacing.sm),
      verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
    ) {
      Text(
        text = "DEBUG ONLY - Google Sign-In diagnostics",
        style = MaterialTheme.typography.labelLarge,
        color = extended.warning,
      )
      Text(
        text = diagnostics.hint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      rows.forEach { (label, value) ->
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
          Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = extended.warning,
          )
          Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
      Text(
        text = "This panel exists only in debug builds and is never shown in release.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
