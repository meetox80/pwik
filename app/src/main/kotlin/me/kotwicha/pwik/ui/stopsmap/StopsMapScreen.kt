package me.kotwicha.pwik.ui.stopsmap

import android.Manifest
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.log2
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.kotwicha.pwik.core.geo.Bounds
import me.kotwicha.pwik.core.geo.LatLon
import me.kotwicha.pwik.core.timetable.Network
import me.kotwicha.pwik.core.timetable.Stop
import me.kotwicha.pwik.data.repo.MapBundle
import me.kotwicha.pwik.data.repo.Place
import me.kotwicha.pwik.ui.components.BadgeSize
import me.kotwicha.pwik.ui.components.LineBadge
import me.kotwicha.pwik.ui.components.Pill
import me.kotwicha.pwik.ui.countdown
import me.kotwicha.pwik.ui.hhmm
import me.kotwicha.pwik.ui.map.LabelSprites
import me.kotwicha.pwik.ui.map.MapCameraState
import me.kotwicha.pwik.ui.map.MapView
import me.kotwicha.pwik.ui.map.OverlayPainter
import me.kotwicha.pwik.ui.nav.MainViewModel
import me.kotwicha.pwik.ui.nav.Tab
import me.kotwicha.pwik.ui.nav.rememberNow
import me.kotwicha.pwik.ui.planner.PlannerViewModel
import me.kotwicha.pwik.ui.theme.AppFont
import me.kotwicha.pwik.ui.theme.LineColors
import me.kotwicha.pwik.ui.theme.LocalExtraColors

@Composable
fun StopsMapScreen(
    vm: MainViewModel,
    planner: PlannerViewModel,
    network: Network,
    bundle: MapBundle,
    camera: MapCameraState,
    padding: PaddingValues,
) {
    val map = bundle.map
    val density = LocalDensity.current.density
    val painter = remember { OverlayPainter() }
    val extra = LocalExtraColors.current
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var line by rememberSaveable { mutableStateOf<String?>(null) }
    var me by remember { mutableStateOf<LatLon?>(null) }
    var follow by remember { mutableStateOf(false) }

    val geometry = remember(map, network) { NetworkGeometry.of(map, network) }
    val stopPoints = geometry.stops
    val servedBy = geometry.servedBy
    val primary = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.surfaceContainerLowest
    val halo = MaterialTheme.colorScheme.surface
    val labelColor = MaterialTheme.colorScheme.onSurface
    val labelSize = with(LocalDensity.current) { MaterialTheme.typography.labelMedium.fontSize.toPx() }
    val resolver = LocalFontFamilyResolver.current
    val labelFont = remember(resolver) { resolver.resolve(AppFont, FontWeight.SemiBold).value as? Typeface }
    val sprites = remember(labelFont, labelSize, labelColor, halo) {
        LabelSprites.obtain(labelFont, labelSize, labelColor.toArgb(), halo.toArgb())
    }
    val routeOverlay = rememberRouteOverlay(geometry)
    val routeColors = remember(geometry, ring) { RouteColors(geometry, ring) }
    val routePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }

    val chromeTop = WindowInsets.statusBars.getTop(LocalDensity.current) + 64f * density
    val chromeBottom = with(LocalDensity.current) { padding.calculateBottomPadding().toPx() }
    LaunchedEffect(camera.viewport) {
        if (camera.viewport != IntSize.Zero && !camera.initialized) {
            geometry.bounds?.let { camera.fit(map, it, 24f * density, chromeTop, chromeBottom) }
        }
    }
    LaunchedEffect(follow) {
        if (!follow) return@LaunchedEffect
        vm.container.location.updates().collectLatest { p ->
            val first = me == null
            me = p
            if (first && map.bounds.contains(p.lat, p.lon)) {
                camera.animateToPoint(map, p, camera.zoom.coerceAtLeast(1.2f + log2(density)))
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) follow = true
    }

    fun focusLine(name: String?) {
        line = name
        val bounds = if (name == null) geometry.bounds
        else Bounds.of(network.patterns.filter { network.lines[it.line].name == name }.flatMap { network.fullShape(it.index) })
        bounds?.let { b -> scope.launch { camera.animateToBounds(map, b, 32f * density, chromeTop, chromeBottom) } }
    }

    Box(Modifier.fillMaxSize()) {
        MapView(
            map = map,
            camera = camera,
            modifier = Modifier.fillMaxSize(),
            tileOverlay = routeOverlay,
            onTap = { _, pos, proj ->
                var best: Int? = null
                var bestD = 26f * density
                stopPoints.forEachIndexed { i, p ->
                    if (line != null && line !in servedBy[i]) return@forEachIndexed
                    val d = (Offset(proj.x(p.x), proj.y(p.y)) - pos).getDistance()
                    if (d < bestD) { bestD = d; best = i }
                }
                selected = best?.let { network.stops[it].key }
                best?.let { i ->
                    val point = stopPoints[i]
                    val visibleBottom = camera.viewport.height - chromeBottom - 380f * density
                    if (proj.y(point.y) > visibleBottom) {
                        val target = (chromeTop + visibleBottom) / 2
                        val dy = (proj.y(point.y) - target) / proj.scale
                        scope.launch { camera.animateTo(camera.cx, camera.cy + dy, camera.zoom, 360) }
                    }
                }
            },
            overlay = { proj ->
                val zoom = camera.zoom
                val zoomDp = zoom - log2(density) + 16f
                val focused = line
                if (focused != null) {
                    val chunks = geometry.routeChunks(floor(zoom).toInt().coerceIn(-6, 4))
                    val scale = proj.scale
                    val originX = proj.x(0f)
                    val originY = proj.y(0f)
                    val pad = 12f * density
                    val viewMinX = (-pad - originX) / scale
                    val viewMinY = (-pad - originY) / scale
                    val viewMaxX = (size.width + pad - originX) / scale
                    val viewMaxY = (size.height + pad - originY) / scale
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        native.save()
                        native.translate(originX, originY)
                        native.scale(scale, scale)
                        for ((width, colors) in listOf(9.4f to routeColors.casing, 5.5f to routeColors.solid)) {
                            routePaint.strokeWidth = width * density / scale
                            for (chunk in chunks) {
                                if (chunk.maxX < viewMinX || chunk.minX > viewMaxX || chunk.maxY < viewMinY || chunk.minY > viewMaxY) continue
                                if (geometry.routes[chunk.route].line != focused) continue
                                routePaint.color = colors[chunk.route]
                                native.drawPath(chunk.path, routePaint)
                            }
                        }
                        native.restore()
                    }
                }
                val r = when {
                    zoomDp < 12.5f -> 2.6f
                    zoomDp < 14.5f -> 3.6f
                    else -> 4.8f
                } * density
                val lineColor = line?.let { LineColors.of(it) }
                stopPoints.forEachIndexed { i, p ->
                    val active = line == null || line in servedBy[i]
                    if (!active) return@forEachIndexed
                    val at = Offset(proj.x(p.x), proj.y(p.y))
                    if (at.x < -40 || at.y < -40 || at.x > size.width + 40 || at.y > size.height + 40) return@forEachIndexed
                    if (network.stops[i].key != selected) painter.stop(this, at, lineColor ?: primary, ring, r)
                }
                selected?.let { key -> network.stop(key) }?.let { s ->
                    val p = stopPoints[s.index]
                    painter.stop(this, Offset(proj.x(p.x), proj.y(p.y)), lineColor ?: primary, ring, r, selected = true)
                }
                if (zoomDp >= 15f) {
                    val taken = ArrayList<Rect>()
                    drawIntoCanvas { canvas ->
                        stopPoints.forEachIndexed { i, p ->
                            if (line != null && line !in servedBy[i]) return@forEachIndexed
                            val sprite = sprites.get(network.stops[i].name)
                            val left = proj.x(p.x) - sprite.width / 2f
                            val top = proj.y(p.y) + r * 1.5f
                            val box = Rect(left, top, left + sprite.width, top + sprite.height)
                            if (box.right < 0 || box.left > size.width || box.bottom < 0 || box.top > size.height) return@forEachIndexed
                            if (taken.any { it.overlaps(box) }) return@forEachIndexed
                            taken.add(box)
                            canvas.nativeCanvas.drawBitmap(sprite, left, top, null)
                        }
                    }
                }
                me?.let { painter.me(this, proj.toScreen(it), extra.origin, 6f * density) }
            },
        )

        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Brush.verticalGradient(listOf(halo.copy(alpha = 0.96f), halo.copy(alpha = 0.82f))))
        )
        LazyRow(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { LineChip("Wszystkie linie", null, line == null) { focusLine(null) } }
            items(network.lines) { l -> LineChip("Linia ${l.name}", l.name, line == l.name) { focusLine(if (line == l.name) null else l.name) } }
        }

        val stop = selected?.let { network.stop(it) }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(bottom = padding.calculateBottomPadding()).fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            Column(Modifier.padding(end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallFloatingActionButton(
                    onClick = { focusLine(line) },
                    containerColor = ring,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) { Icon(Icons.Rounded.ZoomOutMap, "Pokaż całość") }
                SmallFloatingActionButton(
                    onClick = {
                        if (vm.container.location.hasPermission()) {
                            if (follow && me != null) scope.launch { camera.animateToPoint(map, me!!, camera.zoom.coerceAtLeast(1.2f + log2(density))) }
                            follow = true
                        } else {
                            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    },
                    containerColor = ring,
                    contentColor = if (follow) extra.origin else MaterialTheme.colorScheme.onSurface,
                ) { Icon(Icons.Rounded.MyLocation, "Moja lokalizacja") }
            }
            AnimatedVisibility(
                visible = stop != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                if (stop != null) {
                    StopCard(
                        vm = vm,
                        network = network,
                        stop = stop,
                        onClose = { selected = null },
                        onDepartures = { vm.openStop(stop.key, stop.name) },
                        onFrom = {
                            val place = Place.AtStop(stop.key, stop.name)
                            planner.setFrom(place)
                            vm.record(place)
                            vm.tab = Tab.PLAN
                        },
                        onTo = {
                            val place = Place.AtStop(stop.key, stop.name)
                            planner.setTo(place)
                            vm.record(place)
                            vm.tab = Tab.PLAN
                        },
                    )
                }
            }
        }
    }
}

private class RouteColors(geometry: NetworkGeometry, ring: Color) {
    val solid = IntArray(geometry.routes.size) { geometry.routes[it].color.toArgb() }
    val casing = IntArray(geometry.routes.size) { ring.toArgb() }
}

@Composable
private fun LineChip(label: String, line: String?, selected: Boolean, onClick: () -> Unit) {
    val color = line?.let { LineColors.of(it) } ?: MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) color else MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = when {
            !selected -> MaterialTheme.colorScheme.onSurface
            line == null -> MaterialTheme.colorScheme.onPrimary
            else -> Color.White
        },
        shadowElevation = 3.dp,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (line != null && !selected) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StopCard(
    vm: MainViewModel,
    network: Network,
    stop: Stop,
    onClose: () -> Unit,
    onDepartures: () -> Unit,
    onFrom: () -> Unit,
    onTo: () -> Unit,
) {
    val now = rememberNow()
    val extra = LocalExtraColors.current
    val nowMinute = now.hour * 60 + now.minute
    val next = remember(stop, network, nowMinute) {
        vm.container.engine.serviceDay(network, now.toLocalDate()).departures(stop.index, nowMinute, 4)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = 6.dp)) {
                    Text(stop.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        network.linesAtStop[stop.index].forEach { LineBadge(it.name, size = BadgeSize.Small) }
                    }
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Zamknij") }
            }
            if (stop.approximate) {
                Spacer(Modifier.height(8.dp))
                Pill("Położenie przybliżone", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Spacer(Modifier.height(12.dp))
            if (next.isEmpty()) {
                Text("Dziś już nic stąd nie odjeżdża.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for (d in next) {
                Row(Modifier.padding(end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    LineBadge(d.line.name, size = BadgeSize.Small)
                    Spacer(Modifier.width(10.dp))
                    Text("→ ${d.headsign.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(hhmm(d.minute), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(countdown(d.minute - nowMinute), style = MaterialTheme.typography.labelLarge, color = extra.live)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDepartures, modifier = Modifier.weight(1f), shape = RoundedCornerShape(50)) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Wszystkie odjazdy")
                }
                FilledTonalIconButton(onClick = onFrom) { Icon(Icons.Rounded.NearMe, "Jedź stąd") }
                FilledTonalIconButton(onClick = onTo) { Icon(Icons.Rounded.Place, "Jedź tutaj") }
            }
        }
    }
}
