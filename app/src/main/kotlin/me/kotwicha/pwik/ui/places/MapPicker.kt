package me.kotwicha.pwik.ui.places

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import me.kotwicha.pwik.core.map.PlaceKind
import me.kotwicha.pwik.data.repo.MapBundle
import me.kotwicha.pwik.data.repo.Place
import me.kotwicha.pwik.ui.map.MapCameraState
import me.kotwicha.pwik.ui.map.MapView
import me.kotwicha.pwik.ui.map.OverlayPainter
import me.kotwicha.pwik.ui.nav.Field
import me.kotwicha.pwik.ui.nav.MainViewModel
import me.kotwicha.pwik.ui.planner.PlannerViewModel
import me.kotwicha.pwik.ui.theme.LocalExtraColors

@Composable
fun MapPickerScreen(vm: MainViewModel, planner: PlannerViewModel, bundle: MapBundle, field: Field) {
    val map = bundle.map
    val camera = remember { MapCameraState() }
    val painter = remember { OverlayPainter() }
    val extra = LocalExtraColors.current
    val density = LocalDensity.current.density
    val ui by planner.ui.collectAsStateWithLifecycle()
    val ring = MaterialTheme.colorScheme.surfaceContainerLowest

    LaunchedEffect(camera.viewport) {
        if (camera.viewport == IntSize.Zero || camera.initialized) return@LaunchedEffect
        val existing = (if (field == Field.FROM) ui.from else ui.to)
        val start = when (existing) {
            is Place.AtStreet -> existing.point
            is Place.AtPoint -> existing.point
            else -> bundle.streets.search("Jarosław", 5).firstOrNull { it.kind == PlaceKind.TOWN }?.point ?: map.bounds.center
        }
        camera.set(map.toUnitsX(start.lon), map.toUnitsY(start.lat), 1.4f + kotlin.math.log2(density))
    }

    val center by remember { derivedStateOf { if (camera.initialized) map.toLatLon(camera.cx, camera.cy) else null } }
    var label by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bundle) {
        snapshotFlow { center }.collectLatest { c ->
            if (c == null) return@collectLatest
            delay(250)
            label = withContext(Dispatchers.Default) { bundle.streets.streetNameNear(c, 120.0) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        MapView(map = map, camera = camera, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.align(Alignment.Center).size(48.dp, 48.dp).offset(y = (-22).dp)) {
            painter.pin(this, Offset(size.width / 2, size.height), if (field == Field.FROM) extra.origin else extra.destination, ring, 40f * density)
        }
        Row(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledIconButton(
                onClick = { vm.pop() },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = ring, contentColor = MaterialTheme.colorScheme.onSurface),
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Wróć") }
            Surface(shape = RoundedCornerShape(50), color = ring, shadowElevation = 4.dp) {
                Text(
                    if (field == Field.FROM) "Wskaż miejsce startu" else "Wskaż cel podróży",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(20.dp)) {
                Text(label ?: "Wybrane miejsce", style = MaterialTheme.typography.titleLarge)
                Text(
                    center?.let { "%.5f, %.5f".format(java.util.Locale.ROOT, it.lat, it.lon) } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val c = center ?: return@Button
                        val place = Place.AtPoint(c, label?.let { "okolice: $it" } ?: "Punkt na mapie")
                        if (field == Field.FROM) planner.setFrom(place) else planner.setTo(place)
                        vm.record(place)
                        vm.pop()
                    },
                    enabled = center != null,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                ) { Text("Wybierz to miejsce", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}
