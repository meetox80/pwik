package me.kotwicha.pwik.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt
import me.kotwicha.pwik.BuildConfig
import me.kotwicha.pwik.core.calendar.DateSpan
import me.kotwicha.pwik.data.repo.SyncOutcome
import me.kotwicha.pwik.data.repo.ThemeMode
import me.kotwicha.pwik.data.repo.TimetableState
import me.kotwicha.pwik.ui.components.IconBubble
import me.kotwicha.pwik.ui.distance
import me.kotwicha.pwik.ui.longDate
import me.kotwicha.pwik.ui.nav.MainViewModel
import me.kotwicha.pwik.ui.relativeDays
import me.kotwicha.pwik.ui.theme.LocalExtraColors

@Composable
fun SettingsScreen(vm: MainViewModel, padding: PaddingValues) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val timetable by vm.timetable.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    var pickWinter by remember { mutableStateOf(false) }
    val extra = LocalExtraColors.current

    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Ustawienia", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
        item {
            Group(Icons.Rounded.CloudDownload, "Rozkład jazdy") {
                val info = (timetable as? TimetableState.Ready)?.info
                if (info != null) {
                    Text(
                        info.validFrom?.let { "Obowiązuje od ${longDate(it)}" } ?: info.fileName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Pobrany ze strony pwik-jaroslaw.pl ${relativeDays(info.importedAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                val status = sync.status
                val checked = status.lastAttempt?.let { "Ostatnie sprawdzenie: ${relativeDays(it)}" } ?: "Strona PWiK nie była jeszcze sprawdzana"
                Text("$checked. Aplikacja sprawdza nowy rozkład automatycznie co miesiąc.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedVisibility(sync.busy) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (status.running) "Pobieram i czytam rozkład ze strony PWiK…" else "Czekam na połączenie z internetem…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (!sync.busy && status.lastOutcome != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val ok = status.lastOutcome != SyncOutcome.FAILED
                        Icon(
                            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                            null,
                            Modifier.size(18.dp),
                            tint = if (ok) extra.live else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (status.lastOutcome) {
                                SyncOutcome.UPDATED -> "Pobrano nowy rozkład."
                                SyncOutcome.UNCHANGED -> "Masz aktualny rozkład."
                                else -> status.message ?: "Nie udało się sprawdzić rozkładu."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = vm::syncNow, enabled = !sync.busy, shape = RoundedCornerShape(50)) { Text("Sprawdź teraz") }
            }
        }
        item {
            Group(Icons.AutoMirrored.Rounded.DirectionsWalk, "Wyszukiwanie połączeń") {
                SliderRow("Dojście do przystanku", settings.maxWalkMeters.toFloat(), 300f..2500f, 21, distance(settings.maxWalkMeters)) { v ->
                    vm.updateSettings { it.copy(maxWalkMeters = (v / 100).roundToInt() * 100) }
                }
                SliderRow("Przejście przy przesiadce", settings.maxTransferWalkMeters.toFloat(), 100f..1000f, 17, distance(settings.maxTransferWalkMeters)) { v ->
                    vm.updateSettings { it.copy(maxTransferWalkMeters = (v / 50).roundToInt() * 50) }
                }
                SliderRow("Tempo marszu", settings.walkSpeedKmh.toFloat(), 3f..6f, 14, "%.1f km/h".format(settings.walkSpeedKmh).replace('.', ',')) { v ->
                    vm.updateSettings { it.copy(walkSpeedKmh = (v * 5).roundToInt() / 5.0) }
                }
                SliderRow("Najwięcej przesiadek", settings.maxTransfers.toFloat(), 0f..3f, 2, settings.maxTransfers.toString()) { v ->
                    vm.updateSettings { it.copy(maxTransfers = v.roundToInt()) }
                }
            }
        }
        item {
            Group(Icons.Rounded.School, "Ferie zimowe") {
                val today = LocalDate.now()
                val upcoming = settings.winterBreaks().filter { !it.end.isBefore(today) }.minByOrNull { it.start }
                Text(
                    upcoming?.let { "${longDate(it.start)} – ${longDate(it.end)}" } ?: "Termin nieustalony",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (settings.winterBreakOverride != null) "Termin ustawiony ręcznie" else "Kursy szkolne wtedy nie jeżdżą",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickWinter = true }, shape = RoundedCornerShape(50)) { Text("Zmień termin") }
                    if (settings.winterBreakOverride != null) {
                        TextButton(onClick = { vm.setWinterBreak(null) }) { Text("Przywróć") }
                    }
                }
            }
        }
        item {
            Group(Icons.Rounded.Palette, "Wygląd") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val modes = listOf(ThemeMode.SYSTEM to "Systemowy", ThemeMode.LIGHT to "Jasny", ThemeMode.DARK to "Ciemny")
                    modes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { vm.updateSettings { it.copy(themeMode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        ) { Text(label) }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Kolory z tapety", style = MaterialTheme.typography.titleSmall)
                            Text("Material You zamiast barw aplikacji", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = settings.dynamicColor, onCheckedChange = { v -> vm.updateSettings { it.copy(dynamicColor = v) } })
                    }
                }
            }
        }
        item {
            Group(Icons.Rounded.History, "Historia") {
                Text(
                    "Ostatnio używane przystanki i miejsca podpowiadamy na górze wyszukiwarki.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = vm::clearHistory, contentPadding = PaddingValues(0.dp)) { Text("Wyczyść historię") }
            }
        }
        item {
            Group(Icons.Rounded.Info, "O aplikacji") {
                Text("Autor: kotwicha", style = MaterialTheme.typography.titleSmall)
                Text("Wersja ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Rozkład jazdy: PWiK Sp. z o.o. w Jarosławiu, pwik-jaroslaw.pl\n" +
                        "Mapa: © współtwórcy OpenStreetMap, licencja ODbL\n" +
                        "Czcionka: Plus Jakarta Sans, licencja SIL Open Font License",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (pickWinter) WinterBreakDialog(onDismiss = { pickWinter = false }) { span ->
        vm.setWinterBreak(span)
        pickWinter = false
    }
}

@Composable
private fun Group(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(icon, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    onDone: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local.coerceIn(range),
            onValueChange = { local = it },
            onValueChangeFinished = { onDone(local) },
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WinterBreakDialog(onDismiss: () -> Unit, onPick: (DateSpan) -> Unit) {
    val state = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                onClick = {
                    val s = Instant.ofEpochMilli(state.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                    val e = Instant.ofEpochMilli(state.selectedEndDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                    onPick(DateSpan(s, e))
                },
            ) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    ) {
        DateRangePicker(
            state = state,
            title = { Text("Termin ferii zimowych", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            showModeToggle = false,
            modifier = Modifier.height(480.dp),
        )
    }
}
