package com.example.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * SKYPLUS MONEY — reusable text input components (Phase 1).
 *
 * Token-driven colours, a consistent 16 dp corner, error and supporting-text
 * slots, and a minimum 48 dp touch height, so every form in the app looks the
 * same without duplicating `OutlinedTextFieldDefaults` at each call site.
 */
@Composable
fun OceanTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  supportingText: String? = null,
  isError: Boolean = false,
  enabled: Boolean = true,
  singleLine: Boolean = true,
  leadingIcon: ImageVector? = null,
  trailingIcon: ImageVector? = null,
  trailingIconDescription: String? = null,
  onTrailingIconClick: (() -> Unit)? = null,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  shape: Shape? = null,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.fillMaxWidth(),
    enabled = enabled,
    singleLine = singleLine,
    isError = isError,
    label = { Text(text = label, style = MaterialTheme.typography.bodySmall) },
    placeholder =
      if (placeholder != null) {
        { Text(text = placeholder, style = MaterialTheme.typography.bodyMedium) }
      } else {
        null
      },
    supportingText =
      if (supportingText != null) {
        { Text(text = supportingText, style = MaterialTheme.typography.bodySmall) }
      } else {
        null
      },
    leadingIcon =
      if (leadingIcon != null) {
        {
          Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        null
      },
    trailingIcon =
      if (trailingIcon != null) {
        {
          if (onTrailingIconClick != null) {
            IconButton(onClick = onTrailingIconClick) {
              Icon(
                imageVector = trailingIcon,
                contentDescription = trailingIconDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            Icon(
              imageVector = trailingIcon,
              contentDescription = trailingIconDescription,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        null
      },
    keyboardOptions = keyboardOptions,
    visualTransformation = visualTransformation,
    shape = shape ?: RoundedCornerShape(16.dp),
    colors = oceanTextFieldColors(),
  )
}

/** Password input with a show/hide toggle. Never logs or stores the value. */
@Composable
fun OceanPasswordField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  supportingText: String? = null,
  isError: Boolean = false,
  enabled: Boolean = true,
  showPasswordLabel: String = "Show password",
  hidePasswordLabel: String = "Hide password",
) {
  var isVisible by remember { mutableStateOf(false) }

  OceanTextField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    modifier = modifier,
    placeholder = placeholder,
    supportingText = supportingText,
    isError = isError,
    enabled = enabled,
    singleLine = true,
    trailingIcon = if (isVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
    trailingIconDescription = if (isVisible) hidePasswordLabel else showPasswordLabel,
    onTrailingIconClick = { isVisible = !isVisible },
    visualTransformation =
      if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
  )
}

/** Shared token-based colour set for every Ocean text field. */
@Composable
private fun oceanTextFieldColors() =
  OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    errorBorderColor = MaterialTheme.colorScheme.error,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    errorLabelColor = MaterialTheme.colorScheme.error,
    cursorColor = MaterialTheme.colorScheme.primary,
    errorSupportingTextColor = MaterialTheme.colorScheme.error,
  )
