package com.example.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.SkyplusIconSize

/**
 * One entry in the bottom bar. [testTag] is carried so existing UI tests keep finding the tabs.
 */
data class SkyplusNavItem(
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

/**
 * Navigation foundation for Stage 1 and later.
 *
 * The bar itself is generic: it renders whatever items it is given, so the six planned sections
 * (Home, Transactions, Portfolio, Tools, Reports, Settings) can be adopted one at a time without
 * touching this component again. The active item is tinted with the yellow accent container so the
 * selected state uses the primary blue indicator and a filled icon, so it is not colour-only.
 */
@Composable
fun SkyplusNavigationBar(
  items: List<SkyplusNavItem>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  NavigationBar(
    modifier = modifier.height(68.dp),
    containerColor = colors.surface,
    contentColor = colors.onSurfaceVariant,
    tonalElevation = 0.dp,
  ) {
    items.forEachIndexed { index, item ->
      val selected = index == selectedIndex
      NavigationBarItem(
        selected = selected,
        onClick = { onSelect(index) },
        modifier = Modifier.testTag(item.testTag),
        icon = {
          Icon(
            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
            contentDescription = item.label,
            modifier = Modifier.size(SkyplusIconSize.md),
          )
        },
        label = {
          Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        colors =
          NavigationBarItemDefaults.colors(
            selectedIconColor = colors.primary,
            selectedTextColor = colors.primary,
            indicatorColor = colors.primaryContainer,
            unselectedIconColor = colors.onSurfaceVariant,
            unselectedTextColor = colors.onSurfaceVariant,
          ),
      )
    }
  }
}

/** Small header used above navigation lists and drawers. */
@Composable
fun SkyplusNavigationHeader(
  title: String,
  subtitle: String? = null,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth().padding(OceanSpacing.md)) {
    Text(text = title, style = MaterialTheme.typography.titleLarge)
    if (subtitle != null) {
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
