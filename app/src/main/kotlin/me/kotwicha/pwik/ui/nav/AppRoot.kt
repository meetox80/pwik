package me.kotwicha.pwik.ui.nav

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Signpost
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.kotwicha.pwik.core.timetable.Network
import me.kotwicha.pwik.data.repo.MapBundle
import me.kotwicha.pwik.data.repo.SyncOutcome
import me.kotwicha.pwik.data.repo.TimetableState
import me.kotwicha.pwik.ui.components.EmptyState
import me.kotwicha.pwik.ui.map.MapCameraState
import me.kotwicha.pwik.ui.map.TileCache
import me.kotwicha.pwik.ui.map.rememberMapTheme
import me.kotwicha.pwik.ui.map.tilesCovering
import me.kotwicha.pwik.ui.places.MapPickerScreen
import me.kotwicha.pwik.ui.places.PlacePickerScreen
import me.kotwicha.pwik.ui.planner.JourneyScreen
import me.kotwicha.pwik.ui.planner.PlannerScreen
import me.kotwicha.pwik.ui.planner.PlannerViewModel
import me.kotwicha.pwik.ui.settings.SettingsScreen
import me.kotwicha.pwik.ui.stops.BoardScreen
import me.kotwicha.pwik.ui.stops.StopsScreen
import me.kotwicha.pwik.ui.stopsmap.NetworkGeometry
import me.kotwicha.pwik.ui.stopsmap.RouteTileOverlay
import me.kotwicha.pwik.ui.stopsmap.StopsMapScreen
import me.kotwicha.pwik.ui.theme.LocalExtraColors
import me.kotwicha.pwik.ui.theme.PwikTheme

@Composable
fun rememberNow(): LocalDateTime {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(LocalDateTime.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = LocalDateTime.now()
                delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
            }
        }
    }
    return now
}

@Composable
fun AppRoot(vm: MainViewModel, planner: PlannerViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    PwikTheme(settings.themeMode, settings.dynamicColor) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val state by vm.timetable.collectAsStateWithLifecycle()
            val map = vm.map
            val s = state
            val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
            val heroOnTop = when {
                s is TimetableState.Failed -> false
                s !is TimetableState.Ready || map == null -> true
                else -> vm.current is Route.Home && vm.tab == Tab.PLAN
            }
            val view = LocalView.current
            SideEffect {
                val window = (view.context as? Activity)?.window ?: return@SideEffect
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme && !heroOnTop
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
            Crossfade(targetState = s is TimetableState.Ready && map != null, animationSpec = tween(500), label = "root") { ready ->
                when {
                    ready && s is TimetableState.Ready && map != null -> Navigator(vm, planner, s.network, map)
                    s is TimetableState.Failed -> EmptyState(
                        Icons.Rounded.ErrorOutline,
                        "Nie udało się wczytać rozkładu",
                        s.message,
                        Modifier.fillMaxSize().padding(top = 120.dp),
                    )
                    s is TimetableState.Missing -> {
                        val sync by vm.sync.collectAsStateWithLifecycle()
                        val online by vm.online.collectAsStateWithLifecycle()
                        FirstDownload(sync, online, vm::syncNow)
                    }
                    else -> Splash("Wczytuję rozkład jazdy")
                }
            }
        }
    }
}

@Composable
private fun FirstDownload(sync: SyncUi, online: Boolean, retry: () -> Unit) {
    val status = sync.status
    when {
        status.running -> Splash("Pobieram rozkład jazdy ze strony PWiK")
        !online -> Splash("Brak internetu. Rozkład jazdy pobierze się sam, gdy telefon połączy się z siecią.", busy = false)
        status.lastOutcome == SyncOutcome.FAILED -> Splash(
            listOfNotNull("Nie udało się pobrać rozkładu jazdy.", status.message).joinToString("\n"),
            busy = false,
            retry = retry,
        )
        else -> Splash("Pobieram rozkład jazdy ze strony PWiK")
    }
}

@Composable
private fun Splash(message: String, busy: Boolean = true, retry: (() -> Unit)? = null) {
    val extra = LocalExtraColors.current
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        0.94f, 1.04f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val shift by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "shift")
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(extra.heroTop, extra.heroBottom), start = androidx.compose.ui.geometry.Offset(0f, shift * 400f))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(
                Modifier
                    .size(108.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(34.dp))
                    .background(extra.onHero.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.DirectionsBus, null, Modifier.size(58.dp), tint = extra.onHero)
            }
            Spacer(Modifier.height(28.dp))
            Text("Autobusy Jarosław", style = MaterialTheme.typography.headlineMedium, color = extra.onHero)
            Spacer(Modifier.height(10.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = extra.onHero.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            if (busy) {
                Spacer(Modifier.height(28.dp))
                LinearProgressIndicator(
                    Modifier.width(180.dp).clip(RoundedCornerShape(50)),
                    color = extra.onHero,
                    trackColor = extra.onHero.copy(alpha = 0.2f),
                )
            }
            if (retry != null) {
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = retry,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = extra.onHero, contentColor = extra.heroTop),
                ) { Text("Spróbuj ponownie") }
            }
        }
    }
}

@Composable
private fun Navigator(vm: MainViewModel, planner: PlannerViewModel, network: Network, map: MapBundle) {
    val stopsCamera = remember { MapCameraState() }
    val mapTheme = rememberMapTheme()
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density)
    LaunchedEffect(map, network, mapTheme, window) {
        val geometry = withContext(Dispatchers.Default) { NetworkGeometry.of(map.map, network) }
        val bounds = geometry.bounds ?: return@LaunchedEffect
        if (window.width == 0 || window.height == 0) return@LaunchedEffect
        val probe = MapCameraState().apply {
            viewport = window
            attach(map.map)
        }
        val (x, y, z) = probe.zoomForBounds(map.map, bounds, 24f * density.density, statusTop + 64f * density.density, 96f * density.density)
        TileCache.obtain(map.map, mapTheme, RouteTileOverlay(geometry, mapTheme))
            .prefetch(tilesCovering(map.map, x, y, z, window.width * 3 / 2, window.height * 3 / 2))
    }
    val depth = remember { mutableStateOf(vm.stack.size) }
    BackHandler(enabled = vm.stack.size > 1 || vm.tab != Tab.PLAN) {
        if (!vm.pop()) vm.tab = Tab.PLAN
    }
    AnimatedContent(
        targetState = vm.current,
        transitionSpec = {
            val forward = vm.stack.size >= depth.value
            depth.value = vm.stack.size
            if (forward) {
                (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(320)) { -it / 6 } + fadeOut(tween(200)))
            } else {
                (slideInHorizontally(tween(320)) { -it / 6 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(320)) { it / 3 } + fadeOut(tween(200)))
            }
        },
        label = "nav",
    ) { route ->
        when (route) {
            Route.Home -> Home(vm, planner, network, map, stopsCamera)
            is Route.Picker -> PlacePickerScreen(vm, planner, network, map, route.field)
            is Route.MapPicker -> MapPickerScreen(vm, planner, map, route.field)
            is Route.JourneyDetail -> JourneyScreen(vm, map, route.journey, route.date)
            is Route.StopBoard -> BoardScreen(vm, planner, network, map, route.stopKey, stopsCamera)
        }
    }
}

@Composable
private fun Home(vm: MainViewModel, planner: PlannerViewModel, network: Network, map: MapBundle, camera: MapCameraState) {
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                val items = listOf(
                    Triple(Tab.PLAN, "Trasa", Icons.Rounded.Route),
                    Triple(Tab.MAP, "Mapa", Icons.Rounded.Map),
                    Triple(Tab.STOPS, "Przystanki", Icons.Rounded.Signpost),
                    Triple(Tab.SETTINGS, "Ustawienia", Icons.Rounded.Settings),
                )
                for ((tab, label, icon) in items) {
                    NavigationBarItem(
                        selected = vm.tab == tab,
                        onClick = { vm.tab = tab },
                        icon = { Icon(icon, null) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Crossfade(targetState = vm.tab, animationSpec = tween(220), label = "tabs") { tab ->
            val inner = PaddingValues(bottom = padding.calculateBottomPadding())
            when (tab) {
                Tab.PLAN -> PlannerScreen(vm, planner, network, inner)
                Tab.MAP -> StopsMapScreen(vm, planner, network, map, camera, inner)
                Tab.STOPS -> StopsScreen(vm, network, inner)
                Tab.SETTINGS -> SettingsScreen(vm, inner)
            }
        }
    }
}
