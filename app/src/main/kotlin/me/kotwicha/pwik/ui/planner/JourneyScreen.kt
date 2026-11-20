package me.kotwicha.pwik.ui.planner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import me.kotwicha.pwik.core.geo.Bounds
import me.kotwicha.pwik.core.routing.Journey
import me.kotwicha.pwik.core.routing.Leg
import me.kotwicha.pwik.data.repo.MapBundle
import me.kotwicha.pwik.ui.components.BadgeSize
import me.kotwicha.pwik.ui.components.LineBadge
import me.kotwicha.pwik.ui.components.Pill
import me.kotwicha.pwik.ui.components.TimeText
import me.kotwicha.pwik.ui.countdown
import me.kotwicha.pwik.ui.dayLabel
import me.kotwicha.pwik.ui.distance
import me.kotwicha.pwik.ui.duration
import me.kotwicha.pwik.ui.hhmm
import me.kotwicha.pwik.ui.map.MapCameraState
import me.kotwicha.pwik.ui.map.MapView
import me.kotwicha.pwik.ui.map.OverlayPainter
import me.kotwicha.pwik.ui.map.UnitLine
import me.kotwicha.pwik.ui.map.UnitPoint
import me.kotwicha.pwik.ui.nav.MainViewModel
import me.kotwicha.pwik.ui.nav.rememberNow
import me.kotwicha.pwik.ui.stopsCount
import me.kotwicha.pwik.ui.theme.LineColors
import me.kotwicha.pwik.ui.theme.LocalExtraColors
import me.kotwicha.pwik.ui.transfers

private sealed interface Seg {
    data class Solid(val color: Color) : Seg
    data class Dotted(val color: Color) : Seg
}

private sealed interface Node {
    data class Ring(val color: Color) : Node
    data class Dot(val color: Color) : Node
    data class Pin(val color: Color) : Node
}

@Composable
fun JourneyScreen(
    vm: MainViewModel,
    bundle: MapBundle,
    journey: Journey,
    date: LocalDate,
) {
    val map = bundle.map
    val extra = LocalExtraColors.current
    val camera = remember { MapCameraState() }
    val painter = remember { OverlayPainter() }
    val density = LocalDensity.current.density
    val rides = remember(journey) { journey.rides.map { UnitLine.of(map, it.shape) to LineColors.of(it.line.name) } }
    val walks = remember(journey) { journey.legs.filterIsInstance<Leg.Walk>().filter { it.path.size >= 2 }.map { UnitLine.of(map, it.path) } }
    val visits = remember(journey) {
        journey.rides.flatMap { r -> r.visits.map { UnitPoint.of(map, it.point) to LineColors.of(r.line.name) } }
    }
    val allPoints = remember(journey) {
        journey.legs.flatMap { leg -> if (leg is Leg.Ride) leg.shape else (leg as Leg.Walk).path }
    }
    val start = remember(journey) { allPoints.firstOrNull()?.let { UnitPoint.of(map, it) } }
    val end = remember(journey) { allPoints.lastOrNull()?.let { UnitPoint.of(map, it) } }
    val ring = MaterialTheme.colorScheme.surfaceContainerLowest
    val statusBar = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()

    LaunchedEffect(camera.viewport) {
        if (camera.viewport != IntSize.Zero && !camera.initialized) {
            val hidden = camera.viewport.height * (MAP_HEIGHT + SHEET_HEIGHT - 1f) / MAP_HEIGHT
            Bounds.of(allPoints)?.let {
                camera.fit(map, it, 32f * density, topPx = statusBar + 30f * density, bottomPx = hidden + 8f * density)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        MapView(
            map = map,
            camera = camera,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(MAP_HEIGHT),
            overlay = { proj ->
                for (w in walks) painter.line(this, proj, w, extra.walk, 4.5f * density, dashed = true)
                for ((line, color) in rides) painter.line(this, proj, line, color, 6f * density, casing = ring)
                for ((p, color) in visits) painter.stop(this, Offset(proj.x(p.x), proj.y(p.y)), color, ring, 3.2f * density)
                start?.let { painter.origin(this, Offset(proj.x(it.x), proj.y(it.y)), extra.origin, ring, 7f * density) }
                end?.let { painter.pin(this, Offset(proj.x(it.x), proj.y(it.y)), extra.destination, ring, 30f * density) }
            },
        )
        FilledIconButton(
            onClick = { vm.pop() },
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(12.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Wróć") }

        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT).align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp,
        ) {
            LazyColumn(
                contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                item { Handle() }
                item { Summary(journey, date) }
                item { Timeline(journey) }
            }
        }
    }
}

private const val MAP_HEIGHT = 0.47f
private const val SHEET_HEIGHT = 0.56f

@Composable
private fun Handle() {
    Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.width(40.dp).height(4.dp), shape = RoundedCornerShape(2.dp), color = MaterialTheme.colorScheme.outlineVariant) {}
    }
}

@Composable
private fun Summary(journey: Journey, date: LocalDate) {
    val now = rememberNow()
    val extra = LocalExtraColors.current
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimeText("${hhmm(journey.departMinute)} – ${hhmm(journey.arriveMinute)}", MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            Pill(duration(journey.totalMinutes), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(4.dp))
        val parts = buildList {
            add(dayLabel(date))
            if (!journey.walkOnly) add(transfers(journey.transfers))
            add("${distance(journey.walkMeters)} pieszo")
        }
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (date == now.toLocalDate()) {
            val leave = journey.departMinute - (now.hour * 60 + now.minute)
            if (leave >= -1) {
                Spacer(Modifier.height(8.dp))
                Pill(
                    if (leave <= 0) "Wyjdź teraz" else "Wyjdź ${countdown(leave)}",
                    extra.live.copy(alpha = 0.14f),
                    extra.live,
                )
            }
        }
    }
}

@Composable
private fun Timeline(journey: Journey) {
    val extra = LocalExtraColors.current
    Column(Modifier.padding(top = 8.dp, end = 20.dp)) {
        val legs = journey.legs
        val first = legs.first()
        val originLabel = when (first) {
            is Leg.Walk -> first.from
            is Leg.Ride -> first.from.stop.name
        }
        if (first is Leg.Walk) {
            Row0(hhmm(journey.departMinute), null, Seg.Dotted(extra.walk), Node.Ring(extra.origin)) {
                Text(originLabel, style = MaterialTheme.typography.titleMedium)
                Text("Start", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
            }
        }
        legs.forEachIndexed { i, leg ->
            when (leg) {
                is Leg.Walk -> {
                    val toLabel = if (i == legs.lastIndex) "do celu" else "do przystanku ${leg.to}"
                    Row0(null, Seg.Dotted(extra.walk), Seg.Dotted(extra.walk), null) {
                        Row(
                            Modifier.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, null, Modifier.size(20.dp), tint = extra.walk)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Idź pieszo $toLabel", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${distance(leg.meters)} · ok. ${leg.minutes} min",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (i == legs.lastIndex) {
                        Row0(hhmm(leg.endMinute), Seg.Dotted(extra.walk), null, Node.Pin(extra.destination)) {
                            Text(leg.to, style = MaterialTheme.typography.titleMedium)
                            Text("Cel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                is Leg.Ride -> RideRows(leg, i == 0, i == legs.lastIndex, legs.getOrNull(i - 1) is Leg.Walk)
            }
        }
    }
}

@Composable
private fun RideRows(ride: Leg.Ride, first: Boolean, last: Boolean, afterWalk: Boolean) {
    val extra = LocalExtraColors.current
    val color = LineColors.of(ride.line.name)
    var expanded by remember { mutableStateOf(false) }
    Row0(
        hhmm(ride.from.minute),
        when {
            first -> null
            afterWalk -> Seg.Dotted(extra.walk)
            else -> Seg.Solid(color)
        },
        Seg.Solid(color),
        Node.Ring(color),
        bold = true,
    ) {
        Text(ride.from.stop.name, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineBadge(ride.line.name, size = BadgeSize.Medium)
            Spacer(Modifier.width(10.dp))
            Text("→ ${ride.headsign.name}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        ride.note?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
    Row0(null, Seg.Solid(color), Seg.Solid(color), null) {
        Row(
            Modifier
                .padding(vertical = 6.dp)
                .clickable(enabled = ride.intermediate > 0) { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${duration(ride.to.minute - ride.from.minute)} jazdy" +
                    if (ride.intermediate > 0) " · ${stopsCount(ride.intermediate)} po drodze" else " · bez zatrzymań",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ride.intermediate > 0) {
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column {
            for (v in ride.visits.subList(1, ride.visits.size - 1)) {
                Row0(hhmm(v.minute), Seg.Solid(color), Seg.Solid(color), Node.Dot(color), small = true) {
                    Text(v.stop.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp))
                }
            }
        }
    }
    Row0(hhmm(ride.to.minute), Seg.Solid(color), if (last) null else Seg.Solid(color), if (last) Node.Pin(extra.destination) else Node.Ring(color), bold = true) {
        Text(ride.to.stop.name, style = MaterialTheme.typography.titleMedium)
        Text(if (last) "Cel" else "Wysiądź", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun Row0(
    time: String?,
    top: Seg?,
    bottom: Seg?,
    node: Node?,
    bold: Boolean = false,
    small: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(64.dp).padding(top = 2.dp), contentAlignment = Alignment.TopEnd) {
            if (time != null) {
                TimeText(
                    time,
                    if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleSmall.copy(fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold),
                    color = if (small) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Canvas(Modifier.width(36.dp).fillMaxHeight()) {
            val cx = size.width / 2
            val ny = 11.dp.toPx()
            val w = 5.dp.toPx()
            fun seg(s: Seg, y0: Float, y1: Float) = when (s) {
                is Seg.Solid -> drawLine(s.color, Offset(cx, y0), Offset(cx, y1), strokeWidth = w)
                is Seg.Dotted -> drawLine(
                    s.color, Offset(cx, y0 + 3.dp.toPx()), Offset(cx, y1), strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 7.dp.toPx())),
                )
            }
            top?.let { seg(it, 0f, if (node == null) size.height else ny) }
            bottom?.let { seg(it, if (node == null && top != null) size.height else ny, size.height) }
            when (node) {
                is Node.Ring -> {
                    drawCircle(node.color, 8.dp.toPx(), Offset(cx, ny))
                    drawCircle(surface, 4.dp.toPx(), Offset(cx, ny))
                }
                is Node.Dot -> {
                    drawCircle(surface, 3.5.dp.toPx(), Offset(cx, ny))
                    drawCircle(node.color.copy(alpha = 0.9f), 2.2.dp.toPx(), Offset(cx, ny))
                }
                is Node.Pin -> {
                    drawCircle(node.color, 9.dp.toPx(), Offset(cx, ny))
                    drawCircle(Color.White, 3.5.dp.toPx(), Offset(cx, ny))
                }
                null -> Unit
            }
        }
        Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.Top, content = content)
    }
}
