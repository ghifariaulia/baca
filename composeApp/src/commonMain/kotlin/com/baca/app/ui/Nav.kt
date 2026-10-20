package com.baca.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Screen frame with the app nav: 240dp sidebar at desktop width (≥ 900),
 * bottom nav below that (TECH 10.3/10.5).
 */
@Composable
fun NavShell(active: NavItem, onNav: (NavItem) -> Unit, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 900.dp) {
            Row(Modifier.fillMaxSize()) {
                Sidebar(active, onNav)
                Box(Modifier.weight(1f)) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) { content() }
                BottomNav(active, onNav)
            }
        }
    }
}

/** Bottom nav (phone, TECH 10.3): one item per [NavItem]; active item accent + bold label. */
@Composable
fun BottomNav(current: NavItem, onNav: (NavItem) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavItem.entries.forEach { item ->
            val active = item == current
            val color = if (active) accent else muted
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                // equal shares: five items no longer fit with fixed side padding
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNav(item) }
                    .padding(vertical = 4.dp),
            ) {
                NavIcon(item, color, 22.dp)
                Text(
                    item.label,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = color,
                )
            }
        }
    }
}

enum class NavItem(val label: String) {
    LIBRARY("Library"),
    NOTES("Notes"),
    BOOKMARKS("Bookmarks"),
    SYNC("Sync"),
    SETTINGS("Settings"),
}

/** Material icons tinted per state (TECH 10.3 2px-stroke look = outlined set). */
@Composable
fun NavIcon(item: NavItem, tint: Color, iconSize: Dp = 22.dp) {
    val painter = when (item) {
        NavItem.LIBRARY -> Icons.Outlined.AutoStories
        NavItem.NOTES -> Icons.Outlined.EditNote
        NavItem.BOOKMARKS -> Icons.Outlined.BookmarkBorder
        NavItem.SYNC -> Icons.Outlined.Sync
        NavItem.SETTINGS -> Icons.Outlined.Settings
    }
    androidx.compose.material3.Icon(
        imageVector = painter,
        contentDescription = item.label,
        tint = tint,
        modifier = Modifier.size(iconSize),
    )
}
