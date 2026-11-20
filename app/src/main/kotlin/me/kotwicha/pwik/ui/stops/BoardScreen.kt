package me.kotwicha.pwik.ui.stops

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import me.kotwicha.pwik.core.timetable.Departure
import me.kotwicha.pwik.core.timetable.Network
import me.kotwicha.pwik.core.timetable.Stop
import me.kotwicha.pwik.data.repo.MapBundle
import me.kotwicha.pwik.data.repo.Place
import me.kotwicha.pwik.ui.components.BadgeSize
import me.kotwicha.pwik.ui.components.EmptyState
import me.kotwicha.pwik.ui.components.LineBadge
import me.kotwicha.pwik.ui.components.Pill
import me.kotwicha.pwik.ui.components.TimeText
import me.kotwicha.pwik.ui.countdown
import me.kotwicha.pwik.ui.dayLabel
import me.kotwicha.pwik.ui.hhmm
import me.kotwicha.pwik.ui.map.MapCameraState
import me.kotwicha.pwik.ui.map.MapView
import me.kotwicha.pwik.ui.map.OverlayPainter
import me.kotwicha.pwik.ui.map.UnitPoint
import me.kotwicha.pwik.ui.nav.MainViewModel
import me.kotwicha.pwik.ui.nav.Route
import me.kotwicha.pwik.ui.nav.Tab
import me.kotwicha.pwik.ui.nav.rememberNow
import me.kotwicha.pwik.ui.planner.PlannerViewModel
import me.kotwicha.pwik.ui.theme.LineColors
import me.kotwicha.pwik.ui.theme.LocalExtraColors

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    vm: MainViewModel,
    planner: PlannerViewModel,
    network: Network,
    bundle: MapBundle,
    stopKey: String,
    stopsCamera: MapCameraState,
) {
    val stop = network.stop(stopKey)
    if (stop == null) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            BackButton { vm.pop() }
            EmptyState(Icons.Rounded.Info, "Przystanku nie ma w nowym rozkładzie", "Rozkład jazdy został zaktualizowany i ten przystanek już w nim nie występuje.")
        }
        return
    }
    val now = rememberNow()
    val today = now.toLocalDate()
    var date by remember { mutableStateOf(today) }
    var showEarlier by remember { mutableStateOf(false) }
    var lineFilter by remember { mutableStateOf<String?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    val lines = network.linesAtStop[stop.index]
    val nowMinute = now.hour * 60 + now.minute
    val departures = remember(stop, date, showEarlier, network, if (date == today) nowMinute else 0) {
        val day = vm.container.engine.serviceDay(network, date)
        val from = if (date == today && !showEarlier) nowMinute else 0
        day.departures(stop.index, from, 400)
    }
    val shown = departures.filter { lineFilter == null || it.line.name == lineFilter }
    val scope = rememberCoroutineScope()
    val extra = LocalExtraColors.current

    fun plan(asFrom: Boolean) {
        val place = Place.AtStop(stop.key, stop.name)
        if (asFrom) planner.setFrom(place) else planner.setTo(place)
        vm.tab = Tab.PLAN
        while (vm.stack.size > 1) vm.pop()
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(250.dp)) {
            MiniMap(bundle, stop) {
                vm.tab = Tab.MAP
                while (vm.current !is Route.Home) vm.pop()
                scope.launch {
                    stopsCamera.animateToPoint(bundle.map, stop.point, stopsCamera.zoom.coerceAtLeast(2f))
                }
            }
            Box(Modifier.windowInsetsPadding(WindowInsets.statusBars)) { BackButton { vm.pop() } }
        }
        Surface(
            Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                item(key = "head") {
                    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp)) {
                        Text(stop.name, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            lines.forEach { LineBadge(it.name, size = BadgeSize.Medium) }
                        }
                        if (stop.approximate) {
                            Spacer(Modifier.height(10.dp))
                            Pill("Położenie na mapie przybliżone", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, icon = Icons.Rounded.Info)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { plan(true) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(50)) {
                                Icon(Icons.Rounded.NearMe, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Jedź stąd")
                            }
                            OutlinedButton(onClick = { plan(false) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(50)) {
                                Icon(Icons.Rounded.Place, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Jedź tutaj")
                            }
                        }
                    }
                }
                item(key = "days") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(listOf(today, today.plusDays(1))) { d ->
                            FilterChip(
                                selected = date == d,
                                onClick = { date = d; showEarlier = false },
                                label = { Text(dayLabel(d, today)) },
                                shape = RoundedCornerShape(50),
                            )
                        }
                        item {
                            val custom = date != today && date != today.plusDays(1)
                            FilterChip(
                                selected = custom,
                                onClick = { pickDate = true },
                                label = { Text(if (custom) dayLabel(date, today) else "Inny dzień") },
                                leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp)) },
                                shape = RoundedCornerShape(50),
                            )
                        }
                    }
                }
                if (lines.size > 1) {
                    item(key = "lines") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                FilterChip(selected = lineFilter == null, onClick = { lineFilter = null }, label = { Text("Wszystkie") }, shape = RoundedCornerShape(50))
                            }
                            items(lines) { l ->
                                val color = LineColors.of(l.name)
                                FilterChip(
                                    selected = lineFilter == l.name,
                                    onClick = { lineFilter = if (lineFilter == l.name) null else l.name },
                                    label = { Text("Linia ${l.name}") },
                                    leadingIcon = { Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color)) },
                                    shape = RoundedCornerShape(50),
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.18f)),
                                )
                            }
                        }
                    }
                }
                if (date == today && !showEarlier) {
                    item(key = "earlier") {
                        TextButton(onClick = { showEarlier = true }, modifier = Modifier.padding(start = 12.dp, top = 4.dp)) {
                            Text("Pokaż wcześniejsze odjazdy")
                        }
                    }
                }
                if (shown.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            Icons.Rounded.EventBusy,
                            if (date == today && !showEarlier) "Dziś już nic stąd nie odjeżdża" else "Brak odjazdów w tym dniu",
                            "Sprawdź inny dzień — rozkład różni się w dni robocze, soboty i święta.",
                        )
                    }
                }
                val grouped = shown.groupBy { (it.minute / 60) % 24 }
                for ((hour, deps) in grouped) {
                    stickyHeader(key = "h$hour") {
                        Text(
                            "%02d:00".format(hour),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                    items(deps, key = { "d${it.trip.trip.index}-${it.position}" }) { d ->
                        DepartureRow(d, if (date == today) nowMinute else null, extra.live)
                    }
                }
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        showEarlier = false
                    }
                    pickDate = false
                }) { Text("Wybierz") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Anuluj") } },
        ) { DatePicker(state = state, showModeToggle = false) }
    }
}

@Composable
private fun DepartureRow(d: Departure, nowMinute: Int?, live: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeText(hhmm(d.minute), MaterialTheme.typography.titleLarge, modifier = Modifier.width(72.dp))
        LineBadge(d.line.name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("→ ${d.headsign.name}", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            d.trip.trip.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 2)
            }
        }
        if (nowMinute != null) {
            val left = d.minute - nowMinute
            if (left in 0..90) {
                Text(countdown(left), style = MaterialTheme.typography.labelLarge, color = live)
            }
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.padding(12.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Wróć") }
}

@Composable
private fun MiniMap(bundle: MapBundle, stop: Stop, onOpen: () -> Unit) {
    val map = bundle.map
    val camera = remember(stop) { MapCameraState() }
    val painter = remember { OverlayPainter() }
    val density = LocalDensity.current.density
    val platforms = remember(stop) { stop.platforms.map { UnitPoint.of(map, it.point) } }
    val primary = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.surfaceContainerLowest
    LaunchedEffect(camera.viewport) {
        if (camera.viewport != IntSize.Zero && !camera.initialized) {
            camera.set(map.toUnitsX(stop.lon), map.toUnitsY(stop.lat), 0.3f + kotlin.math.log2(density))
        }
    }
    MapView(
        map = map,
        camera = camera,
        modifier = Modifier.fillMaxSize(),
        onTap = { _, _, _ -> onOpen() },
        overlay = { proj ->
            for (p in platforms) painter.stop(this, Offset(proj.x(p.x), proj.y(p.y)), primary, ring, 7f * density)
        },
    )
}
