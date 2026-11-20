package me.kotwicha.pwik.ui.planner

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Signpost
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import me.kotwicha.pwik.ui.components.EmptyState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.LocalTime
import me.kotwicha.pwik.core.routing.Journey
import me.kotwicha.pwik.core.routing.Preference
import me.kotwicha.pwik.core.timetable.Network
import me.kotwicha.pwik.data.repo.Place
import me.kotwicha.pwik.ui.components.IconBubble
import me.kotwicha.pwik.ui.components.JourneyStrip
import me.kotwicha.pwik.ui.components.Pill
import me.kotwicha.pwik.ui.components.SectionTitle
import me.kotwicha.pwik.ui.components.TimeText
import me.kotwicha.pwik.ui.countdown
import me.kotwicha.pwik.ui.dayLabel
import me.kotwicha.pwik.ui.distance
import me.kotwicha.pwik.ui.duration
import me.kotwicha.pwik.ui.hhmm
import me.kotwicha.pwik.ui.nav.Field
import me.kotwicha.pwik.ui.nav.MainViewModel
import me.kotwicha.pwik.ui.nav.Route
import me.kotwicha.pwik.ui.nav.rememberNow
import me.kotwicha.pwik.ui.theme.LocalExtraColors
import me.kotwicha.pwik.ui.transfers

fun preferenceLabel(p: Preference) = when (p) {
    Preference.FASTEST -> "Najszybciej"
    Preference.LEAST_WALKING -> "Najmniej pieszo"
    Preference.FEWEST_TRANSFERS -> "Bez przesiadek"
}

@Composable
fun PlannerScreen(vm: MainViewModel, planner: PlannerViewModel, network: Network, padding: PaddingValues) {
    val ui by planner.ui.collectAsStateWithLifecycle()
    val recent by vm.recentPlaces.collectAsStateWithLifecycle()
    val extra = LocalExtraColors.current
    val now = rememberNow()
    var timeSheet by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        planner.permissionHandled(result.values.any { it })
    }
    LaunchedEffect(now) { planner.refreshIfStale(now) }
    LaunchedEffect(ui.needsLocationPermission) {
        if (ui.needsLocationPermission) {
            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp),
    ) {
        item(key = "hero") {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                        .background(Brush.linearGradient(listOf(extra.heroTop, extra.heroBottom), start = Offset.Zero, end = Offset(1200f, 900f)))
                ) {
                    HeroDecoration(Modifier.matchParentSize())
                    Column(
                        Modifier
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 22.dp + HERO_OVERLAP)
                    ) {
                        Text("Autobusy Jarosław", style = MaterialTheme.typography.labelLarge, color = extra.onHero.copy(alpha = 0.8f))
                        Spacer(Modifier.height(6.dp))
                        Text(greeting(now.toLocalTime()), style = MaterialTheme.typography.headlineLarge, color = extra.onHero)
                    }
                }
                RouteCard(
                    ui = ui,
                    onFrom = { vm.push(Route.Picker(Field.FROM)) },
                    onTo = { vm.push(Route.Picker(Field.TO)) },
                    onSwap = planner::swap,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .layout { measurable, constraints ->
                            val overlap = HERO_OVERLAP.roundToPx()
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height - overlap) { placeable.place(0, -overlap) }
                        },
                )
            }
        }
        item(key = "controls") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    AssistChip(
                        onClick = { timeSheet = true },
                        label = { Text(whenLabel(ui.departure)) },
                        leadingIcon = { Icon(Icons.Rounded.AccessTime, null, Modifier.size(18.dp)) },
                        trailingIcon = { Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(50),
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                }
                item { PreferenceChip(ui.preference, planner::setPreference) }
            }
        }
        if (ui.loading) {
            items(3, key = { "skeleton$it" }) { SkeletonCard() }
        }
        ui.message?.let { msg ->
            item(key = "message") {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            if (ui.offerTomorrow) {
                                TextButton(onClick = planner::searchTomorrowMorning, contentPadding = PaddingValues(0.dp)) {
                                    Text("Pokaż połączenia następnego dnia")
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!ui.loading && ui.results.isNotEmpty()) {
            item(key = "resultsTitle") {
                SectionTitle(
                    "Połączenia · ${dayLabel(ui.resultDate)}",
                    Modifier.padding(horizontal = 20.dp),
                )
            }
            itemsIndexed(ui.results, key = { _, j -> j.signature }) { _, j ->
                JourneyCard(
                    journey = j,
                    nowMinute = if (ui.resultDate == now.toLocalDate()) now.hour * 60 + now.minute else null,
                    onClick = { vm.push(Route.JourneyDetail(j, ui.resultDate)) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).animateItem(),
                )
            }
        }
        if (!ui.loading && !ui.searched && ui.to == null) {
            val targets = recent.filter { it.key != ui.from?.key }
            if (targets.isEmpty()) {
                item(key = "hint") {
                    EmptyState(
                        Icons.Rounded.Signpost,
                        "Tu pojawią się Twoje miejsca",
                        "Wybierz cel: przystanek, ulicę albo miejscowość. Miejsca, do których jeździsz, zobaczysz tutaj, ostatnio używane na górze.",
                        action = {
                            FilledTonalButton(onClick = { vm.push(Route.Picker(Field.TO)) }, shape = RoundedCornerShape(50)) {
                                Text("Wybierz cel")
                            }
                        },
                    )
                }
            } else {
                item(key = "recentTitle") { SectionTitle("Ostatnio wybierane", Modifier.padding(horizontal = 20.dp)) }
                items(targets, key = { "r" + it.key }) { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { planner.setTo(p) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBubble(
                            if (p is Place.AtStop) Icons.Rounded.Signpost else Icons.Rounded.History,
                            MaterialTheme.colorScheme.onSecondaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (p is Place.AtStop) "Przystanek" else "Miejsce",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (timeSheet) {
        TimeSheet(ui.departure, onDismiss = { timeSheet = false }) {
            planner.setWhen(it)
            timeSheet = false
        }
    }
}

private val HERO_OVERLAP = 64.dp

private fun greeting(t: LocalTime): String = when (t.hour) {
    in 4..10 -> "Dzień dobry.\nDokąd jedziesz?"
    in 11..17 -> "Dokąd dziś\njedziesz?"
    in 18..21 -> "Dobry wieczór.\nDokąd jedziesz?"
    else -> "Dokąd o tej\nporze?"
}

@Composable
private fun HeroDecoration(modifier: Modifier) {
    val color = LocalExtraColors.current.onHero
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))
        drawCircle(color.copy(alpha = 0.06f), w * 0.38f, Offset(w * 0.95f, h * 0.05f))
        drawCircle(color.copy(alpha = 0.05f), w * 0.22f, Offset(w * 0.78f, h * 0.95f))
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.52f, h * 1.05f)
            cubicTo(w * 0.62f, h * 0.6f, w * 0.78f, h * 0.7f, w * 0.86f, h * 0.38f)
            cubicTo(w * 0.92f, h * 0.15f, w * 1.0f, h * 0.18f, w * 1.08f, h * 0.05f)
        }
        drawPath(path, color.copy(alpha = 0.22f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round, pathEffect = dash))
        drawCircle(color.copy(alpha = 0.5f), 5.dp.toPx(), Offset(w * 0.86f, h * 0.38f))
        drawCircle(color.copy(alpha = 0.9f), 2.5.dp.toPx(), Offset(w * 0.86f, h * 0.38f))
    }
}

@Composable
private fun RouteCard(ui: PlannerUi, onFrom: () -> Unit, onTo: () -> Unit, onSwap: () -> Unit, modifier: Modifier) {
    val extra = LocalExtraColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 10.dp,
        tonalElevation = 0.dp,
    ) {
        Row(Modifier.padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.width(18.dp).height(96.dp)) {
                val cx = size.width / 2
                val top = size.height * 0.25f
                val bottom = size.height * 0.75f
                drawLine(
                    extra.walk.copy(alpha = 0.6f), Offset(cx, top + 9.dp.toPx()), Offset(cx, bottom - 9.dp.toPx()),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 5.dp.toPx())),
                )
                drawCircle(extra.origin, 7.dp.toPx(), Offset(cx, top))
                drawCircle(Color.White, 3.dp.toPx(), Offset(cx, top))
                drawCircle(extra.destination, 7.dp.toPx(), Offset(cx, bottom))
                drawCircle(Color.White, 3.dp.toPx(), Offset(cx, bottom))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                PlaceField("Skąd", ui.from, "Wybierz start", onFrom)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                PlaceField("Dokąd", ui.to, "Wybierz cel", onTo)
            }
            FilledTonalIconButton(
                onClick = onSwap,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Icon(Icons.Rounded.SwapVert, "Zamień")
            }
        }
    }
}

@Composable
private fun PlaceField(label: String, place: Place?, hint: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            place?.label ?: hint,
            style = MaterialTheme.typography.titleMedium,
            color = if (place == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun whenLabel(w: When): String = when (w) {
    When.Now -> "Odjazd teraz"
    is When.At -> "${dayLabel(w.date)}, ${"%02d:%02d".format(w.time.hour, w.time.minute)}"
}

@Composable
private fun PreferenceChip(current: Preference, onChange: (Preference) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(preferenceLabel(current)) },
            leadingIcon = { Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp)) },
            trailingIcon = { Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp)) },
            shape = RoundedCornerShape(50),
            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(18.dp)) {
            Preference.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(preferenceLabel(p), style = MaterialTheme.typography.bodyLarge) },
                    onClick = { onChange(p); open = false },
                    leadingIcon = {
                        Icon(
                            when (p) {
                                Preference.FASTEST -> Icons.Rounded.AccessTime
                                Preference.LEAST_WALKING -> Icons.AutoMirrored.Rounded.DirectionsWalk
                                Preference.FEWEST_TRANSFERS -> Icons.Rounded.SyncAlt
                            },
                            null,
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSheet(current: When, onDismiss: () -> Unit, onPick: (When) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = LocalDate.now()
    val initial = (current as? When.At)
    var now by remember { mutableStateOf(current == When.Now) }
    var date by remember { mutableStateOf(initial?.date ?: today) }
    val time = rememberTimePickerState(
        initial?.time?.hour ?: LocalTime.now().hour,
        initial?.time?.minute ?: LocalTime.now().minute,
        is24Hour = true,
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Kiedy jedziesz?", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = now, onClick = { now = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Teraz") }
                SegmentedButton(selected = !now, onClick = { now = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Inny termin") }
            }
            AnimatedVisibility(!now, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    Spacer(Modifier.height(18.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(7) { i ->
                            val d = today.plusDays(i.toLong())
                            val selected = d == date
                            Surface(
                                onClick = { date = d },
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            ) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(16.dp))
                                    Text(dayLabel(d, today), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimeInput(state = time) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onPick(if (now) When.Now else When.At(date, LocalTime.of(time.hour, time.minute))) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(27.dp),
            ) { Text("Pokaż połączenia", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun SkeletonCard() {
    val t = rememberInfiniteTransition(label = "skeleton")
    val alpha by t.animateFloat(0.35f, 0.75f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha")
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.padding(20.dp)) {
            Box(Modifier.width(150.dp).height(24.dp).clip(RoundedCornerShape(8.dp)).background(base.copy(alpha = alpha)))
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(11.dp)).background(base.copy(alpha = alpha)))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.width(200.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(base.copy(alpha = alpha)))
        }
    }
}

@Composable
fun JourneyCard(journey: Journey, nowMinute: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val extra = LocalExtraColors.current
    val firstRide = journey.rides.firstOrNull()
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeText(hhmm(journey.departMinute), MaterialTheme.typography.headlineSmall)
                Text("  →  ", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TimeText(hhmm(journey.arriveMinute), MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.weight(1f))
                Pill(duration(journey.totalMinutes), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (nowMinute != null && firstRide != null) {
                val leave = journey.departMinute - nowMinute
                Spacer(Modifier.height(4.dp))
                Text(
                    if (leave <= 0) "Wyjdź teraz · autobus ${countdown(firstRide.from.minute - nowMinute)}" else "Wyjdź ${countdown(leave)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (leave <= 10) extra.live else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            JourneyStrip(journey)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (firstRide != null) {
                    Text(
                        "z ${firstRide.from.stop.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text("Cała trasa pieszo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
                Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, null, Modifier.size(16.dp), tint = extra.walk)
                Text(distance(journey.walkMeters), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!journey.walkOnly) {
                    Box(Modifier.padding(horizontal = 8.dp).size(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
                    Text(transfers(journey.transfers), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val note = journey.rides.firstNotNullOfOrNull { it.note }
            if (note != null) {
                Spacer(Modifier.height(8.dp))
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}
