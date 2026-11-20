package com.example.feature.consent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.component.OceanStatusBadge
import com.example.core.designsystem.theme.OceanSpacing

@Composable
fun LegalConsentPreviewScreen(
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onNavigateBack)

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(OceanSpacing.md)
        .widthIn(max = 760.dp)
        .testTag("legal_preview_screen"),
    verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(
        onClick = onNavigateBack,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("legal_back_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
          contentDescription = stringResource(R.string.cd_navigate_back),
        )
      }
      Text(
        text = stringResource(R.string.legal_preview_title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
      )
    }

    OceanCard {
      OceanStatusBadge(text = "Draft Placeholder — Legal Review Required", tone = BadgeTone.WARNING)
      Spacer(modifier = Modifier.height(OceanSpacing.sm))
      Text(
        text = stringResource(R.string.legal_preview_disclaimer),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    OceanCard {
      Text(
        text = "Consent Audit Model (Scheduled in Phase 1)",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      Text(
        text =
          "• Firebase UID\n• Terms Version & Privacy Policy Version\n• Acceptance Timestamp (UTC)\n• Explicit Acceptance Status\n• Automatic re-consent gate when mandatory versions increase\n• Explicit in-app account deletion instructions",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
