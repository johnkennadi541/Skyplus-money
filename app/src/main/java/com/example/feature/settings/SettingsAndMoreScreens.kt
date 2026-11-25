package com.example.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import com.example.core.designsystem.component.OceanSectionHeader
import com.example.core.designsystem.component.SkyplusSectionCard
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.ThemePreference

@Composable
fun MoreScreen(
  onOpenSettings: () -> Unit,
  onOpenAuthReadiness: () -> Unit,
  onOpenLegalPreview: () -> Unit,
  onOpenAccounts: () -> Unit = {},
  onOpenSmartDetection: () -> Unit = {},
  modifier: Modifier = Modifier,
  onSignOut: (() -> Unit)? = null,
) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(OceanSpacing.md)
        .widthIn(max = 760.dp)
        .testTag("more_screen"),
    verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
  ) {
    OceanSectionHeader(
      title = stringResource(R.string.more_title),
      subtitle = stringResource(R.string.more_subtitle),
    )

    OceanCard(onClick = onOpenSettings, modifier = Modifier.testTag("more_settings_item")) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Rounded.Settings,
          contentDescription = stringResource(R.string.action_open_settings),
          tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(modifier = Modifier.width(OceanSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.action_open_settings),
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = "Appearance and app preferences",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
          contentDescription = null,
        )
      }
    }

    OceanCard(
      onClick = onOpenAuthReadiness,
      modifier = Modifier.testTag("more_auth_readiness_item"),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Rounded.Security,
          contentDescription = stringResource(R.string.action_open_auth_status),
          tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(modifier = Modifier.width(OceanSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.action_open_auth_status),
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = "Google sign-in and session readiness",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
          contentDescription = null,
        )
      }
    }

    OceanCard(onClick = onOpenAccounts, modifier = Modifier.testTag("more_accounts_item")) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = androidx.compose.material.icons.Icons.Rounded.AccountBalance,
          contentDescription = "Accounts & Wallets",
          tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(modifier = Modifier.width(OceanSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Accounts & Wallets",
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = "Manage multi-currency banks, cash, wallets, and credit cards",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
          contentDescription = null,
        )
      }
    }

    OceanCard(onClick = onOpenSmartDetection, modifier = Modifier.testTag("more_smart_detection_item")) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = androidx.compose.material.icons.Icons.Rounded.NotificationsActive,
          contentDescription = "Smart Detection",
          tint = OceanTheme.colors.gold,
        )
        Spacer(modifier = Modifier.width(OceanSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Smart Transaction Detection",
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = "Review supported bank notifications before recording entries",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
          contentDescription = null,
        )
      }
    }

    OceanCard(onClick = onOpenLegalPreview, modifier = Modifier.testTag("more_legal_item")) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Rounded.Gavel,
          contentDescription = stringResource(R.string.action_open_legal_preview),
          tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(OceanSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.action_open_legal_preview),
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = "Terms, privacy information, and consent details",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
          contentDescription = null,
        )
      }
    }

    if (onSignOut != null) {
      OceanCard(onClick = onSignOut, modifier = Modifier.testTag("more_sign_out_button")) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.Logout,
            contentDescription = stringResource(R.string.auth_sign_out),
            tint = MaterialTheme.colorScheme.error,
          )
          Spacer(modifier = Modifier.width(OceanSpacing.sm))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.auth_sign_out),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.error,
            )
            Text(
              text = "Sign out of Firebase Auth and clear Google credential state",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }


  }
}

@Composable
fun SettingsScreen(
  selectedTheme: ThemePreference,
  onSelectTheme: (ThemePreference) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onNavigateBack)

  val options =
    listOf(
      ThemePreference.DARK to stringResource(R.string.settings_theme_dark),
      ThemePreference.LIGHT to stringResource(R.string.settings_theme_light),
      ThemePreference.SYSTEM to stringResource(R.string.settings_theme_system),
    )

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(OceanSpacing.md)
        .widthIn(max = 760.dp)
        .testTag("settings_screen"),
    verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(
        onClick = onNavigateBack,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_back_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
          contentDescription = stringResource(R.string.cd_navigate_back),
        )
      }
      Text(
        text = stringResource(R.string.settings_title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
      )
    }

    SkyplusSectionCard {
      Text(
        text = stringResource(R.string.settings_theme_section),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      options.forEach { (preference, label) ->
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .minimumInteractiveComponentSize()
              .clickable { onSelectTheme(preference) }
              .testTag("theme_option_${preference.name.lowercase()}"),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(
            selected = selectedTheme == preference,
            onClick = { onSelectTheme(preference) },
          )
          Spacer(modifier = Modifier.width(OceanSpacing.xs))
          Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
      }
    }

    SkyplusSectionCard {
      Text(
        text = stringResource(R.string.settings_sync_section),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(OceanSpacing.xxs))
      Text(
        text = stringResource(R.string.settings_sync_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun ProfileScreen(
  authenticatedUser: com.example.domain.model.AuthenticatedUser?,
  onOpenSettings: () -> Unit,
  onOpenAuthReadiness: () -> Unit,
  onOpenSmartDetection: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
  onOpenInvestments: () -> Unit = {},
  onSignOut: (() -> Unit)? = null,
) {
  BackHandler(onBack = onNavigateBack)
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(OceanSpacing.md)
        .widthIn(max = 760.dp)
        .testTag("profile_screen"),
    verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(
        onClick = onNavigateBack,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("profile_back_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
          contentDescription = stringResource(R.string.cd_navigate_back),
        )
      }
      Text(
        text = stringResource(R.string.profile_title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
      )
    }

    SkyplusSectionCard(modifier = Modifier.testTag("profile_account_details_card")) {
      Text(
        text = stringResource(R.string.profile_account_details),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      Text(
        text = authenticatedUser?.displayName?.takeIf { it.isNotBlank() } ?: "Authenticated User",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = authenticatedUser?.email?.ifBlank { "Email unavailable" } ?: "Email unavailable",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = "UID: ${authenticatedUser?.uid ?: "Secure Local UID"}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    OceanCard(onClick = onOpenSettings, modifier = Modifier.testTag("profile_settings_item")) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.width(OceanSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
          Text(text = stringResource(R.string.profile_settings), style = MaterialTheme.typography.titleMedium)
          Text(text = "Appearance and app preferences", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
      }
    }

    OceanCard(onClick = onOpenAuthReadiness, modifier = Modifier.testTag("profile_security_item")) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        Spacer(modifier = Modifier.width(OceanSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
          Text(text = stringResource(R.string.profile_security_settings), style = MaterialTheme.typography.titleMedium)
          Text(text = "Google sign-in and session status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
      }
    }

    OceanCard(onClick = onOpenSmartDetection, modifier = Modifier.testTag("profile_notification_item")) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Rounded.NotificationsActive, contentDescription = null, tint = OceanTheme.colors.gold)
        Spacer(modifier = Modifier.width(OceanSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
          Text(text = stringResource(R.string.profile_notification_settings), style = MaterialTheme.typography.titleMedium)
          Text(text = "Review supported bank notifications before recording entries", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
      }
    }

    OceanCard(onClick = onOpenInvestments, modifier = Modifier.testTag("profile_investments_item")) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.AutoMirrored.Rounded.TrendingUp, contentDescription = null, tint = OceanTheme.colors.positive)
        Spacer(modifier = Modifier.width(OceanSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
          Text(text = stringResource(R.string.investments_title), style = MaterialTheme.typography.titleMedium)
          Text(text = stringResource(R.string.investments_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
      }
    }

    SkyplusSectionCard(modifier = Modifier.testTag("profile_sync_item")) {
      Text(text = stringResource(R.string.profile_cloud_sync), style = MaterialTheme.typography.titleMedium)
      Spacer(modifier = Modifier.height(OceanSpacing.xxs))
      Text(text = "Cloud sync is not enabled. No financial records are uploaded from this screen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    SkyplusSectionCard(modifier = Modifier.testTag("profile_backup_item")) {
      Text(text = stringResource(R.string.profile_backup), style = MaterialTheme.typography.titleMedium)
      Spacer(modifier = Modifier.height(OceanSpacing.xxs))
      Text(text = "Encrypted backup is not available yet. Backup support requires a separate security design and explicit approval.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (onSignOut != null) {
      OceanCard(onClick = onSignOut, modifier = Modifier.testTag("profile_sign_out_button")) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Icon(imageVector = Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
          Spacer(modifier = Modifier.width(OceanSpacing.sm))
          Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.auth_sign_out), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Text(text = "Sign out of Firebase Auth", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

