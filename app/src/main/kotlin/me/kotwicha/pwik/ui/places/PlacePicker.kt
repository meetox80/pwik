package me.kotwicha.pwik.ui.places

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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Signpost
import androidx.compose.material.icons.rounded.Villa
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kotwicha.pwik.core.map.NamedPlace
import me.kotwicha.pwik.core.map.PlaceKind
import me.kotwicha.pwik.core.timetable.Network
import me.kotwicha.pwik.core.timetable.Stop
import me.kotwicha.pwik.data.repo.MapBundle
import me.kotwicha.pwik.data.repo.Place
import me.kotwicha.pwik.ui.components.BadgeSize
import me.kotwicha.pwik.ui.components.EmptyState
import me.kotwicha.pwik.ui.components.IconBubble
import me.kotwicha.pwik.ui.components.LineBadge
import me.kotwicha.pwik.ui.components.SearchField
import me.kotwicha.pwik.ui.components.SectionTitle
import me.kotwicha.pwik.ui.nav.Field
import me.kotwicha.pwik.ui.nav.MainViewModel
import me.kotwicha.pwik.ui.nav.Route
import me.kotwicha.pwik.ui.planner.PlannerViewModel
import me.kotwicha.pwik.ui.theme.LocalExtraColors

private class SearchResults(val stops: List<Stop>, val places: List<NamedPlace>)

@Composable
fun PlacePickerScreen(vm: MainViewModel, planner: PlannerViewModel, network: Network, bundle: MapBundle, field: Field) {
    var query by remember { mutableStateOf("") }
    val recent by vm.recentPlaces.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val extra = LocalExtraColors.current
    LaunchedEffect(Unit) { focus.requestFocus() }

    val engine = vm.container.engine
    val found by produceState(SearchResults(emptyList(), emptyList()), query, network, bundle) {
        value = withContext(Dispatchers.Default) {
            if (query.isBlank()) SearchResults(emptyList(), emptyList())
            else SearchResults(engine.searchStops(network, query, 12), bundle.streets.search(query, 12))
        }
    }
    val stops = found.stops
    val places = found.places

    fun choose(p: Place) {
        if (field == Field.FROM) planner.setFrom(p) else planner.setTo(p)
        vm.record(p)
        vm.pop()
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Wróć") }
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = if (field == Field.FROM) "Skąd? Przystanek, ulica, wieś" else "Dokąd? Przystanek, ulica, wieś",
                focusRequester = focus,
                modifier = Modifier.weight(1f),
            )
        }
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            if (query.isBlank()) {
                item {
                    PickRow(Icons.Rounded.MyLocation, "Moja lokalizacja", "Użyj GPS", extra.origin.copy(alpha = 0.14f), extra.origin) {
                        choose(Place.MyLocation)
                    }
                }
                item {
                    PickRow(Icons.Rounded.Map, "Wskaż na mapie", "Przesuń mapę pod pinezkę", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) {
                        vm.pop()
                        vm.push(Route.MapPicker(field))
                    }
                }
                if (recent.isNotEmpty()) {
                    item { SectionTitle("Ostatnio używane", Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
                    items(recent, key = { it.key }) { p ->
                        val stop = (p as? Place.AtStop)?.let { network.stop(it.stopKey) }
                        PickRow(
                            if (p is Place.AtStop) Icons.Rounded.Signpost else Icons.Rounded.History,
                            p.label,
                            if (stop != null) null else "Miejsce",
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer,
                            lines = stop?.let { network.linesAtStop[it.index].map { l -> l.name } },
                            onRemove = { vm.forget(p) },
                        ) { choose(p) }
                    }
                } else {
                    item {
                        Text(
                            "Tu pojawią się przystanki i miejsca, z których korzystasz — ostatnio używane na górze.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        )
                    }
                }
            } else {
                if (stops.isNotEmpty()) {
                    item { SectionTitle("Przystanki", Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
                    items(stops, key = { "s" + it.key }) { s -> StopPick(network, s) { choose(Place.AtStop(s.key, s.name)) } }
                }
                if (places.isNotEmpty()) {
                    item { SectionTitle("Ulice i miejscowości", Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
                    items(places, key = { "p" + it.kind + it.name }) { p ->
                        PickRow(
                            if (p.kind == PlaceKind.STREET) Icons.Rounded.Place else Icons.Rounded.Villa,
                            p.name,
                            when (p.kind) {
                                PlaceKind.STREET -> "Ulica"
                                PlaceKind.TOWN -> "Miasto"
                                PlaceKind.VILLAGE -> "Miejscowość"
                                PlaceKind.NEIGHBOURHOOD -> "Osiedle, przysiółek"
                            },
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        ) { choose(Place.AtStreet(p.name, p.point, p.kind.name)) }
                    }
                }
                if (stops.isEmpty() && places.isEmpty()) {
                    item { EmptyState(Icons.Rounded.Search, "Nic nie znaleziono", "Sprawdź pisownię albo wskaż miejsce na mapie.") }
                }
            }
        }
    }
}

@Composable
private fun StopPick(network: Network, stop: Stop, onClick: () -> Unit) {
    PickRow(
        Icons.Rounded.Signpost,
        stop.name,
        if (stop.approximate) "Położenie przybliżone" else null,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer,
        lines = network.linesAtStop[stop.index].map { it.name },
        onClick = onClick,
    )
}

@Composable
fun PickRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    bubble: Color,
    tint: Color,
    lines: List<String>? = null,
    onRemove: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon, tint, bubble)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!lines.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    lines.take(8).forEach { LineBadge(it, size = BadgeSize.Small) }
                }
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, "Usuń z historii", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Box(Modifier.width(12.dp))
        }
    }
}
