package com.example.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.core.designsystem.theme.SkyplusIconSize

/**
 * Stage 1 text input. One component covers labels, errors, leading icons, keyboard types and the
 * accent focus colour, so no screen has to configure [OutlinedTextField] by hand.
 */
@Composable
fun SkyplusTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  placeholder: String? = null,
  supportingText: String? = null,
  isError: Boolean = false,
  enabled: Boolean = true,
  singleLine: Boolean = true,
  minLines: Int = 1,
  leadingIcon: ImageVector? = null,
  trailingContent: (@Composable () -> Unit)? = null,
  keyboardType: KeyboardType = KeyboardType.Text,
  imeAction: ImeAction = ImeAction.Next,
) {
  val colors = MaterialTheme.colorScheme
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.fillMaxWidth(),
    enabled = enabled,
    singleLine = singleLine,
    minLines = minLines,
    isError = isError,
    shape = MaterialTheme.shapes.small,
    label = label?.let { { Text(text = it, style = MaterialTheme.typography.bodySmall) } },
    placeholder =
      placeholder?.let {
        { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
      },
    leadingIcon =
      leadingIcon?.let {
        {
          Icon(
            imageVector = it,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(SkyplusIconSize.sm),
          )
        }
      },
    trailingIcon = trailingContent,
    supportingText =
      supportingText?.let {
        { Text(text = it, style = MaterialTheme.typography.labelSmall) }
      },
    textStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
    colors =
      OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.tertiary,
        unfocusedBorderColor = colors.outline,
        disabledBorderColor = colors.outline.copy(alpha = 0.38f),
        errorBorderColor = colors.error,
        focusedLabelColor = colors.tertiary,
        cursorColor = colors.tertiary,
      ),
  )
}

/** Search input with a leading search icon and a clear button that only appears when needed. */
@Composable
fun SkyplusSearchField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String = "Search",
  enabled: Boolean = true,
) {
  SkyplusTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    placeholder = placeholder,
    enabled = enabled,
    leadingIcon = Icons.Outlined.Search,
    imeAction = ImeAction.Search,
    trailingContent =
      if (value.isNotEmpty()) {
        {
          SkyplusIconButton(
            icon = Icons.Outlined.Close,
            contentDescription = "Clear search",
            onClick = { onValueChange("") },
          )
        }
      } else {
        null
      },
  )
}
