package io.rotaskat.app.ui.nav

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import io.rotaskat.app.R
import io.rotaskat.app.ui.theme.accentColors

private data class TopLevelDestination(
    val route: String,
    val label: String,
    @DrawableRes val icon: Int,
)

private val TopLevelDestinations = Routes.TOP_LEVEL.map { route ->
    when (route) {
        Routes.HOME -> TopLevelDestination(route, "Abende", R.drawable.ic_style)
        Routes.LEADERBOARD -> TopLevelDestination(route, "Rangliste", R.drawable.ic_emoji_events)
        Routes.STATS -> TopLevelDestination(route, "Statistik", R.drawable.ic_bar_chart)
        else -> error("Unbekanntes Tab-Ziel $route")
    }
}

/**
 * Die untere Leiste: Abende, Rangliste, Statistik.
 *
 * Unten statt als Textknoepfe in der Kopfzeile, weil sie dort mit dem Daumen
 * erreichbar ist und die Kopfzeile fuer den Titel frei bleibt.
 */
@Composable
fun RotaskatBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val muted = MaterialTheme.accentColors.labelMuted
    NavigationBar(containerColor = colors.surfaceContainerLow, modifier = modifier) {
        for (destination in TopLevelDestinations) {
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(destination.route) },
                icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                label = { Text(destination.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.primary,
                    selectedTextColor = colors.onSurface,
                    indicatorColor = colors.primaryContainer,
                    unselectedIconColor = muted,
                    unselectedTextColor = muted,
                ),
            )
        }
    }
}
