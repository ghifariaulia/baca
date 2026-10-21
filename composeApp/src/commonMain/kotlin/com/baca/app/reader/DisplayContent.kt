package com.baca.app.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baca.app.ui.ReaderThemes
import com.baca.app.ui.Type
import com.baca.shared.domain.DisplaySettings

/** Display settings body, shared by the reader's Display sheet and the Settings screen. */

/**
 * Display settings body (stepper + segmented rows + swatches) shared by the
 * phone sheet and the desktop popover.
 */
@Composable
internal fun DisplayContent(display: DisplaySettings, onChange: (DisplaySettings) -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SheetSection("FONT SIZE") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChoiceChip("A−", true) { onChange(display.copy(fontSizeSp = (display.fontSizeSp - 2).coerceAtLeast(12))) }
                Text("${display.fontSizeSp}", style = Type.cardTitle, fontSize = 18.sp)
                ChoiceChip("A+", true) { onChange(display.copy(fontSizeSp = (display.fontSizeSp + 2).coerceAtMost(28))) }
            }
        }
        SheetSection("FONT") {
            // 2×2: four font names in one row wrap on phone widths
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    listOf("serif" to "Literata", "sans" to "DM Sans"),
                    listOf("libron" to "Libron", "mono" to "Monospace"),
                ).forEach { row ->
                    SegmentedRow(options = row, selected = display.font) { onChange(display.copy(font = it)) }
                }
            }
        }
        SheetSection("READING THEME") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(
                    "paper" to "Paper", "white" to "White",
                    "dim" to "Dim", "black" to "Black",
                ).forEach { (key, label) ->
                    val swatch = ReaderThemes.forTheme(key).background
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(onClick = { onChange(display.copy(theme = key)) }),
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape)
                                .background(swatch)
                                .then(
                                    if (key == display.theme) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(4.dp)
                                    } else {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    },
                                ),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(label, style = Type.caption, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        SheetSection("LINE SPACING") {
            SegmentedRow(
                options = listOf(
                    "compact" to "Compact", "normal" to "Normal", "relaxed" to "Relaxed",
                ),
                selected = display.lineSpacing,
            ) { onChange(display.copy(lineSpacing = it)) }
        }
        SheetSection("MARGINS") {
            SegmentedRow(
                options = listOf(
                    "narrow" to "Narrow", "medium" to "Medium", "wide" to "Wide",
                ),
                selected = display.margins,
            ) { onChange(display.copy(margins = it)) }
        }
        if (tapZonesForPageTurnEnabled) {
            SheetSection("PAGE TURN") {
                SegmentedRow(
                    options = listOf("tap" to "Tap & swipe", "swipe" to "Swipe only"),
                    selected = if (display.tapToTurn) "tap" else "swipe",
                ) { onChange(display.copy(tapToTurn = it == "tap")) }
            }
            SheetSection("FULL SCREEN") {
                SegmentedRow(
                    options = listOf("on" to "Hide status bar", "off" to "Keep status bar"),
                    selected = if (display.fullScreen) "on" else "off",
                ) { onChange(display.copy(fullScreen = it == "on")) }
            }
        }
        Text(
            "Display settings stay on this device and are not synced.",
            style = Type.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
@Composable
internal fun SheetSection(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = Type.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        content()
    }
}
/** Row of option chips, evenly spaced; selected chip uses accent fill (design file). */
@Composable
internal fun SegmentedRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (key, label) ->
            val isSelected = key == selected
            val chipColor =
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(chipColor)
                    .clickable { onSelect(key) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
@Composable
internal fun ChoiceChip(label: String, filled: Boolean, onClick: () -> Unit) {
    val chipColor =
        if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background
    Box(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(chipColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (filled) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}
