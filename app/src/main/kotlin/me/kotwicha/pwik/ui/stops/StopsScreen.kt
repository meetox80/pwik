package me.kotwicha.pwik.ui.stops

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Signpost
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDateTime
import me.kotwicha.pwik.core.timetable.Network
import me.kotwicha.pwik.core.timetable.Stop
import me.kotwicha.pwik.ui.components.BadgeSize
import me.kotwicha.pwik.ui.components.EmptyState
import me.kotwicha.pwik.ui.components.IconBubble
import me.kotwicha.pwik.ui.components.LineBadge
import me.kotwicha.pwik.ui.components.SearchField
import me.kotwicha.pwik.ui.components.SectionTitle
import me.kotwicha.pwik.ui.countdown
import me.kotwicha.pwik.ui.nav.MainViewModel
import me.kotwicha.pwik.ui.nav.Tab
import me.kotwicha.pwik.ui.nav.rememberNow
import me.kotwicha.pwik.ui.theme.LocalExtraColors

@Composable
fun StopsScreen(vm: MainViewModel, network: Network, padding: PaddingValues) {
    var query by rememberSaveable { mutableStateOf("") }
    val recent by vm.recentStops.collectAsStateWithLifecycle()
    val now = rememberNow()
    val results = remember(query, network) { vm.container.engine.searchStops(network, query, 40) }
    val recentStops = remember(recent, network) { recent.mapNotNull { network.stop(it.stopKey) } }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Text(
            "Przystanki",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 14.dp),
        )
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Szukaj przystanku",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            if (query.isBlank()) {
                if (recentStops.isEmpty()) {
                    item {
                        EmptyState(
                            Icons.Rounded.History,
                            "Tu pojawią się Twoje przystanki",
                            "Wyszukaj przystanek albo wybierz go na mapie. Te, z których korzystasz, zobaczysz tutaj — ostatnio używany na samej górze.",
                            action = {
                                FilledTonalButton(onClick = { vm.tab = Tab.MAP }) {
                                    Icon(Icons.Rounded.Map, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Mapa przystanków")
                                }
                            },
                        )
                    }
                } else {
                    item {
                        SectionTitle("Ostatnio używane", Modifier.padding(horizontal = 24.dp)) {
                            TextButton(onClick = { vm.clearHistory() }) { Text("Wyczyść") }
                        }
                    }
                    items(recentStops, key = { "r" + it.key }) { s -> StopRow(vm, network, s, now) }
                }
            } else if (results.isEmpty()) {
                item { EmptyState(Icons.Rounded.SearchOff, "Brak takiego przystanku", "Spróbuj innej nazwy, np. ulicy, przy której stoi.") }
            } else {
                items(results, key = { "q" + it.key }) { s -> StopRow(vm, network, s, now) }
            }
        }
    }
}

@Composable
fun StopRow(vm: MainViewModel, network: Network, stop: Stop, now: LocalDateTime) {
    val extra = LocalExtraColors.current
    val next = remember(stop, network, now.hour, now.minute) {
        val day = vm.container.engine.serviceDay(network, now.toLocalDate())
        day.departures(stop.index, now.hour * 60 + now.minute, 2)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { vm.openStop(stop.key, stop.name) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(Icons.Rounded.Signpost, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stop.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                network.linesAtStop[stop.index].forEach { LineBadge(it.name, size = BadgeSize.Small) }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            val first = next.firstOrNull()
            if (first != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineBadge(first.line.name, size = BadgeSize.Small)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        countdown(first.minute - (now.hour * 60 + now.minute)),
                        style = MaterialTheme.typography.labelLarge,
                        color = extra.live,
                    )
                }
                Text(
                    "→ ${first.headsign.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp),
                )
            } else {
                Text("Dziś już nic", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
