package com.example.feature.authentication

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.component.BadgeTone
import com.example.core.designsystem.component.SkyplusSectionCard
import com.example.core.designsystem.component.OceanStatusBadge
import com.example.core.designsystem.theme.OceanSpacing
import com.example.domain.model.AuthenticatedUser

@Composable
fun AuthFoundationScreen(
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
  authenticatedUser: AuthenticatedUser? = null,
  onSignOut: () -> Unit = {},
) {
  BackHandler(onBack = onNavigateBack)
  val phase1Milestones =
    listOf(
      "Google Sign-In (Credential Manager + Firebase Auth)" to
        "Implemented in Phase 1B using generated default_web_client_id.",
      "Facebook Login" to
        "Scheduled for a later authorized Phase 1 step. Never auto-links accounts solely by email.",
      "Mandatory Terms & Privacy Consent (Phase 1D)" to
        "Versioned consent capture linked to Firebase UID before full financial workspace access.",
    )

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(OceanSpacing.md)
        .widthIn(max = 760.dp)
        .testTag("auth_readiness_screen"),
    verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(
        onClick = onNavigateBack,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("auth_back_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
          contentDescription = stringResource(R.string.cd_navigate_back),
        )
      }
      Text(
        text = stringResource(R.string.auth_status_title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
      )
    }

    if (authenticatedUser != null) {
      SkyplusSectionCard {
        OceanStatusBadge(text = "Authenticated Session Active", tone = BadgeTone.POSITIVE)
        Spacer(modifier = Modifier.height(OceanSpacing.sm))
        Text(
          text =
            authenticatedUser.displayName?.takeIf { it.isNotBlank() }
              ?: authenticatedUser.email
              ?: "SKYPLUS User",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (!authenticatedUser.email.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(OceanSpacing.xxs))
          Text(
            text = authenticatedUser.email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.height(OceanSpacing.xs))
        Text(
          text = stringResource(R.string.auth_provider_google),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(modifier = Modifier.height(OceanSpacing.md))
        OutlinedButton(
          onClick = onSignOut,
          modifier =
            Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("auth_sign_out_button"),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Text(text = stringResource(R.string.auth_sign_out))
        }
      }
    } else {
      SkyplusSectionCard {
        OceanStatusBadge(text = "No Fake Login • Phase 1 Gate", tone = BadgeTone.WARNING)
        Spacer(modifier = Modifier.height(OceanSpacing.sm))
        Text(
          text = stringResource(R.string.auth_status_banner),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(OceanSpacing.xs))
        Text(
          text = stringResource(R.string.auth_status_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    SkyplusSectionCard {
      Text(
        text = "Phase 1 Authentication Milestones",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(OceanSpacing.sm))
      phase1Milestones.forEachIndexed { index, (item, detail) ->
        Text(
          text = "${index + 1}. $item",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.secondary,
        )
        Text(
          text = detail,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (index < phase1Milestones.lastIndex) {
          Spacer(modifier = Modifier.height(OceanSpacing.sm))
        }
      }
    }
  }
}
