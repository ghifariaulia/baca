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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.baca.shared.parser.TextReplacement

/** Display settings body, shared by the reader's Display sheet and the Settings screen. */

/**
 * Display settings body (stepper + segmented rows + swatches) shared by the
 * phone sheet and the desktop popover.
 */
@Composable
internal fun DisplayContent(display: DisplaySettings, onChange: (DisplaySettings) -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SheetSection("FONT SIZE") {
            Stepper("${display.fontSizePercent}%") { onChange(display.stepFontSize(it)) }
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
                // in LibreraX's order
                listOf(
                    "white" to "White", "paper" to "Paper", "grey" to "Grey",
                    "dim" to "Dim", "dusk" to "Dusk", "black" to "Black",
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
            Stepper("${display.lineSpacingTenths / 10}.${display.lineSpacingTenths % 10}") { onChange(display.stepLineSpacing(it)) }
        }
        SheetSection("MARGINS") {
            Stepper("${display.paddingPercent}%") { onChange(display.stepPadding(it)) }
        }
        SheetSection("DIRECTION") {
            SegmentedRow(
                options = listOf("auto" to "Book's own", "ltr" to "Left to right", "rtl" to "Right to left"),
                selected = display.direction,
            ) { onChange(display.copy(direction = it)) }
        }
        SheetSection("COLUMNS") {
            SegmentedRow(
                options = listOf("1" to "One", "2" to "Two"),
                selected = display.columns.toString(),
            ) { onChange(display.copy(columns = it.toInt())) }
        }
        SheetSection("FLOW") {
            SegmentedRow(
                options = listOf("paged" to "Paged", "scrolled" to "Scrolled"),
                selected = display.flow,
            ) { onChange(display.copy(flow = it)) }
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
        Advanced(display, onChange)
        Text(
            "Display settings stay on this device and are not synced.",
            style = Type.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
/** Settings that change the book's text and styling, folded away so they are not touched by accident. */
@Composable
private fun Advanced(display: DisplaySettings, onChange: (DisplaySettings) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ADVANCED", style = Type.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (open) "Hide" else "Show", style = Type.caption, color = MaterialTheme.colorScheme.primary)
        }
        if (open) {
            Text(
                "These change how the book is read. Each change reads the book again.",
                style = Type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SheetSection("CUSTOM CSS") { CustomCss(display, onChange) }
            SheetSection("FIND AND REPLACE") { Replacements(display, onChange) }
        }
    }
}

/** The reader's own CSS, over the book's; applied when asked, as the book is read again for it. */
@Composable
private fun CustomCss(display: DisplaySettings, onChange: (DisplaySettings) -> Unit) {
    var css by remember(display.userCss) { mutableStateOf(display.userCss) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = css,
            onValueChange = { css = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("p { text-align: left }") },
            minLines = 3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Apply", css != display.userCss) { onChange(display.copy(userCss = css)) }
            if (display.userCss.isNotEmpty()) ChoiceChip("Clear", false) { onChange(display.copy(userCss = "")) }
        }
    }
}

/** Find-and-replace rules for the book's text: the rules in force, each removable, and fields for a new one. */
@Composable
private fun Replacements(display: DisplaySettings, onChange: (DisplaySettings) -> Unit) {
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        display.replacements.forEachIndexed { index, rule ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("“${rule.find}”  →  “${rule.replace}”", Modifier.weight(1f), style = Type.body)
                ChoiceChip("Remove", false) { onChange(display.copy(replacements = display.replacements.filterIndexed { i, _ -> i != index })) }
            }
        }
        OutlinedTextField(value = find, onValueChange = { find = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Find") }, singleLine = true)
        OutlinedTextField(value = replace, onValueChange = { replace = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Replace with") }, singleLine = true)
        ChoiceChip("Add", find.isNotEmpty()) {
            if (find.isNotEmpty()) {
                onChange(display.copy(replacements = display.replacements + TextReplacement(find, replace)))
                find = ""
                replace = ""
            }
        }
    }
}

/** − value +: [onStep] gets -1 or +1. */
@Composable
private fun Stepper(value: String, onStep: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChoiceChip("−", true) { onStep(-1) }
        Text(value, style = Type.cardTitle, fontSize = 18.sp)
        ChoiceChip("+", true) { onStep(1) }
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
