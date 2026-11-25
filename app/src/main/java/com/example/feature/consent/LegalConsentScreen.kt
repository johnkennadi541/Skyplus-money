package com.example.feature.consent

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.annotation.StringRes
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.component.BadgeTone
import com.example.core.designsystem.component.SkyplusSectionCard
import com.example.core.designsystem.component.OceanStatusBadge
import com.example.core.designsystem.theme.OceanSpacing

@Composable
fun LegalConsentScreen(
  onAccept: () -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
  @StringRes consentErrorMessageRes: Int? = null,
  isSavingConsent: Boolean = false,
  onRetryConsentCheck: (() -> Unit)? = null,
) {
  var termsAccepted by rememberSaveable { mutableStateOf(false) }
  var privacyAccepted by rememberSaveable { mutableStateOf(false) }
  var showFullTerms by rememberSaveable { mutableStateOf(false) }
  var showFullPrivacy by rememberSaveable { mutableStateOf(false) }

  val canAccept = termsAccepted && privacyAccepted

  Scaffold(
    modifier = modifier.fillMaxSize().testTag("legal_consent_screen"),
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(OceanSpacing.md),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OceanStatusBadge(
            text = stringResource(R.string.legal_consent_badge),
            tone = BadgeTone.GOLD,
          )
          OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("legal_sign_out_button"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.Logout,
              contentDescription = stringResource(R.string.auth_sign_out),
            )
            Spacer(modifier = Modifier.width(OceanSpacing.xs))
            Text(text = stringResource(R.string.auth_sign_out))
          }
        }

        SkyplusSectionCard {
          Text(
            text = stringResource(R.string.legal_consent_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(OceanSpacing.xxs))
          Text(
            text = stringResource(R.string.legal_consent_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        SkyplusSectionCard(modifier = Modifier.testTag("legal_review_notice_card")) {
          OceanStatusBadge(text = "Legal Review Required", tone = BadgeTone.WARNING)
          Spacer(modifier = Modifier.height(OceanSpacing.xs))
          Text(
            text = stringResource(R.string.legal_review_required_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        if (consentErrorMessageRes != null) {
          SkyplusSectionCard(modifier = Modifier.testTag("consent_error_banner")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
              )
              Spacer(modifier = Modifier.width(OceanSpacing.xs))
              Text(
                text = stringResource(consentErrorMessageRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
              )
            }
            if (onRetryConsentCheck != null) {
              Spacer(modifier = Modifier.height(OceanSpacing.xs))
              TextButton(
                onClick = onRetryConsentCheck,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("retry_consent_button"),
              ) {
                Text(text = stringResource(R.string.legal_consent_retry_button))
              }
            }
          }
        }

        // Terms & Conditions Card
        SkyplusSectionCard {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.Gavel,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
            )
            Spacer(modifier = Modifier.width(OceanSpacing.sm))
            Text(
              text = stringResource(R.string.legal_terms_title),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          Spacer(modifier = Modifier.height(OceanSpacing.xs))
          Text(
            text = stringResource(R.string.legal_terms_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (showFullTerms) {
            Spacer(modifier = Modifier.height(OceanSpacing.xs))
            Text(
              text =
                "1. Acceptance of Terms: By accessing SKYPLUS MONEY, you agree to comply with all financial security and accuracy standards.\n" +
                  "2. Financial Data Integrity: All records use strict BigDecimal arithmetic. You are responsible for the accuracy of your entered financial entries.\n" +
                  "3. Account Security: You maintain confidentiality of your Google authentication credentials and session state.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          Spacer(modifier = Modifier.height(OceanSpacing.xs))
          Text(
            text = if (showFullTerms) "Hide Full Terms" else "Read Full Terms",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clickable { showFullTerms = !showFullTerms }
                .padding(vertical = 4.dp)
                .testTag("toggle_terms_button"),
          )
          Spacer(modifier = Modifier.height(OceanSpacing.xs))
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .minimumInteractiveComponentSize()
                .toggleable(
                  value = termsAccepted,
                  role = Role.Checkbox,
                  onValueChange = { termsAccepted = it }
                )
                .testTag("terms_checkbox_row"),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = termsAccepted,
              onCheckedChange = null,
              modifier = Modifier.testTag("terms_checkbox"),
              colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
            )
            Spacer(modifier = Modifier.width(OceanSpacing.xs))
            Text(
              text = stringResource(R.string.legal_accept_terms_checkbox),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        // Privacy Policy Card
        SkyplusSectionCard {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.Security,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
            )
            Spacer(modifier = Modifier.width(OceanSpacing.sm))
            Text(
              text = stringResource(R.string.legal_privacy_title),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          Spacer(modifier = Modifier.height(OceanSpacing.xs))
          Text(
            text = stringResource(R.string.legal_privacy_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (showFullPrivacy) {
            Spacer(modifier = Modifier.height(OceanSpacing.xs))
            Text(
              text =
                "1. Data Collection: We collect only your Firebase Auth identity (UID, display name, email) for session security.\n" +
                  "2. Local Storage: Financial records are stored securely on your device with encryption support.\n" +
                  "3. No Third-Party Sharing: Your financial and personal data is never sold or shared with advertisers.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          Spacer(modifier = Modifier.height(OceanSpacing.xs))
          Text(
            text = if (showFullPrivacy) "Hide Full Privacy Policy" else "Read Full Privacy Policy",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clickable { showFullPrivacy = !showFullPrivacy }
                .padding(vertical = 4.dp)
                .testTag("toggle_privacy_button"),
          )
          Spacer(modifier = Modifier.height(OceanSpacing.xs))
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .minimumInteractiveComponentSize()
                .toggleable(
                  value = privacyAccepted,
                  role = Role.Checkbox,
                  onValueChange = { privacyAccepted = it }
                )
                .testTag("privacy_checkbox_row"),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = privacyAccepted,
              onCheckedChange = null,
              modifier = Modifier.testTag("privacy_checkbox"),
              colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
            )
            Spacer(modifier = Modifier.width(OceanSpacing.xs))
            Text(
              text = stringResource(R.string.legal_accept_privacy_checkbox),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        Button(
          onClick = onAccept,
          enabled = canAccept && !isSavingConsent,
          modifier =
            Modifier.fillMaxWidth()
              .height(52.dp)
              .minimumInteractiveComponentSize()
              .testTag("accept_consent_button"),
          shape = MaterialTheme.shapes.medium,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
              disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            ),
        ) {
          Text(
            text = stringResource(R.string.legal_accept_button),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}
