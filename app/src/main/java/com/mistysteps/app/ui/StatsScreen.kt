package com.mistysteps.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.data.Session
import com.mistysteps.app.debug.BatteryLog
import com.mistysteps.app.stats.AreaStat
import com.mistysteps.app.stats.NeighbourhoodResult
import com.mistysteps.app.stats.StreetStats
import com.mistysteps.app.stats.Stats
import com.mistysteps.app.stats.StatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max

@Composable
fun StatsScreen(
    here: Pair<Double, Double>?,
    onClose: () -> Unit,
    onOpenWalk: (Long) -> Unit,
    onOpenAbout: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val repo = remember { StatsRepository.get(context) }
    var stats by remember { mutableStateOf<Stats?>(null) }
    var walks by remember { mutableStateOf<List<Session>>(emptyList()) }
    var hood by remember { mutableStateOf<NeighbourhoodResult?>(null) }
    // Bumped after an import, so everything is counted again.
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(here, refresh) {
        stats = repo.compute(here?.first, here?.second)
        // Slower (borders and streets may need fetching), so it comes in after the rest.
        if (here != null) hood = repo.neighbourhood(here.first, here.second)
    }
    // Streets come last: they can take a while when OpenStreetMap's servers are busy.
    var streets by remember { mutableStateOf<StreetStats?>(null) }
    var streetsFailed by remember { mutableStateOf(false) }
    var streetsAttempt by remember { mutableStateOf(0) }
    val hoodBoundary = hood?.stat?.boundary
    LaunchedEffect(hoodBoundary, streetsAttempt, refresh) {
        if (hoodBoundary == null) return@LaunchedEffect
        streetsFailed = false
        streets = try {
            repo.streets(hoodBoundary)
        } catch (e: Exception) {
            streetsFailed = true
            null
        }
    }
    LaunchedEffect(refresh) { walks = withContext(Dispatchers.IO) { FogStore.loadSessions() } }

    Box(
        Modifier
            .fillMaxSize()
            .blockTouchesBelow()
            .background(Brush.verticalGradient(listOf(Color(0xFFF4F5FA), Color(0xFFDCE1EE))))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // Pad inside the scroll so content scrolls behind the system bars.
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to map", tint = Ink)
                }
                Text(
                    "Your mist map",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    color = Ink,
                )
            }
            Spacer(Modifier.height(16.dp))

            val s = stats
            if (s == null) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Ink)
                        Spacer(Modifier.height(12.dp))
                        Text("Measuring the mist…", color = Dusk)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallStat("Wandered", formatDistance(s.distanceM), Modifier.weight(1f))
                    SmallStat("Cleared", formatArea(s.clearedM2), Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                if (here != null) {
                    NeighbourhoodCard(hood, streets, streetsFailed, onRetryStreets = { streetsAttempt++ })
                    Spacer(Modifier.height(12.dp))
                }
                s.city?.let { AreaCard(it); Spacer(Modifier.height(12.dp)) }
                s.country?.let { AreaCard(it); Spacer(Modifier.height(12.dp)) }
                AreaCard(s.world)
                s.problem?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = Ember, fontSize = 14.sp)
                }
                if (walks.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text("YOUR WALKS", fontSize = 11.sp, letterSpacing = 1.5.sp, color = Dusk)
                    Spacer(Modifier.height(8.dp))
                    WalkLog(walks, onOpenWalk)
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "Borders and streets come from OpenStreetMap. Only your rough location (about 1 km) " +
                        "is sent to look up your city and country, once per area. Neighbourhoods and streets are " +
                        "fetched by city and neighbourhood, never by your position. Borders and your trails stay on this phone.",
                    color = Dusk,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            Spacer(Modifier.height(20.dp))
            BackupCard(onImported = { refresh++ })
            Spacer(Modifier.height(12.dp))
            MadeBy(onOpenAbout)
            Spacer(Modifier.height(12.dp))
            FeedbackRow()
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** A bug or an idea: straight to the maker's inbox. */
@Composable
private fun FeedbackRow() {
    val context = LocalContext.current
    Surface(
        onClick = { sendFeedback(context) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Frost,
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Send feedback", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("A bug, an idea, a street that's wrong: I read everything", color = Dusk, fontSize = 12.sp)
            }
            Text("✉", fontSize = 22.sp, color = Ink)
        }
    }
}

/** Who made this, and a way to say thanks. */
@Composable
private fun MadeBy(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Frost,
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Made by Potloodgum", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Who made this, and why · buy me a coffee", color = Dusk, fontSize = 12.sp)
            }
            Text("☕", fontSize = 22.sp)
        }
    }
}

@Composable
private fun WalkLog(walks: List<Session>, onOpenWalk: (Long) -> Unit) {
    val when_ = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Frost, shadowElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            for ((i, w) in walks.withIndex()) {
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE3E7F1)))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = w.endTime != null) { onOpenWalk(w.id) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(when_.format(Date(w.startTime)), color = Ink, fontSize = 15.sp)
                        val running = w.endTime == null
                        val details = buildList {
                            add(if (running) "wandering now" else formatDuration(w.endTime!! - w.startTime))
                            w.newAreaM2?.let { add("${formatArea(it)} new mist") }
                            // Battery use per walk, for test runs.
                            if (BatteryLog.enabled && w.batteryStart != null && w.batteryEnd != null) {
                                add("battery ${w.batteryStart}% → ${w.batteryEnd}%")
                            }
                        }
                        Text(details.joinToString(" · "), color = Dusk, fontSize = 12.sp)
                    }
                    Text(formatDistance(w.distanceM), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SmallStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = Frost, shadowElevation = 2.dp) {
        Column(Modifier.padding(16.dp)) {
            Text(label.uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = Dusk)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
    }
}

@Composable
private fun NeighbourhoodCard(
    result: NeighbourhoodResult?,
    streets: StreetStats?,
    streetsFailed: Boolean,
    onRetryStreets: () -> Unit,
) {
    val stat = result?.stat
    when {
        stat != null -> AreaCard(stat.area) {
            when {
                streets != null -> Streets(streets)
                streetsFailed -> {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Couldn't reach OpenStreetMap for the streets. Its servers are often busy.",
                        color = Dusk, fontSize = 13.sp,
                    )
                    TextButton(onClick = onRetryStreets, contentPadding = PaddingValues(0.dp)) {
                        Text("Try again", color = Ember)
                    }
                }
                else -> {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(14.dp).width(14.dp), color = Dusk, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Counting streets…", color = Dusk, fontSize = 13.sp)
                    }
                }
            }
        }
        result == null -> Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Frost, shadowElevation = 2.dp) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), color = Ink, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Finding your neighbourhood…", color = Dusk, fontSize = 14.sp)
            }
        }
        result.problem != null -> Text(result.problem, color = Ember, fontSize = 14.sp)
        else -> Unit // no neighbourhoods mapped here
    }
}

@Composable
private fun ColumnScope.Streets(st: StreetStats) {
    if (st.total == 0) return
    Spacer(Modifier.height(16.dp))
    Text("STREETS", fontSize = 11.sp, letterSpacing = 1.5.sp, color = Dusk)
    Text(
        "${st.walked} of ${st.total} streets walked",
        fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink,
    )
    Spacer(Modifier.height(6.dp))
    LinearProgressIndicator(
        progress = { st.walked.toFloat() / st.total },
        modifier = Modifier.fillMaxWidth().height(6.dp),
        color = Ember,
        trackColor = Color(0xFFD5DAE8),
        drawStopIndicator = {},
    )
    val next = st.toExplore.take(6)
    if (next.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text("Still in the mist", fontSize = 13.sp, color = Dusk)
        for (p in next) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(p.name, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (p.walkedFraction > 0.05) {
                    Text("${(p.walkedFraction * 100).toInt()}% walked", color = Dusk, fontSize = 13.sp)
                }
            }
        }
        if (st.toExplore.size > next.size) {
            Text("and ${st.toExplore.size - next.size} more", color = Dusk, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun AreaCard(stat: AreaStat, extra: @Composable ColumnScope.() -> Unit = {}) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Frost, shadowElevation = 2.dp) {
        Column(Modifier.padding(20.dp)) {
            Text(stat.label.uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = Dusk)
            Text(stat.name, fontFamily = FontFamily.Serif, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.height(10.dp))
            val f = stat.fraction
            if (f == null) {
                Text("OpenStreetMap has no border for this area, so there's nothing to measure against.", color = Dusk, fontSize = 14.sp)
            } else {
                Text(formatPercent(f), fontSize = 34.sp, fontWeight = FontWeight.Light, color = Ink)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { max(f.toFloat(), if (f > 0) 0.01f else 0f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Ink,
                    trackColor = Color(0xFFD5DAE8),
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${formatArea(stat.clearedM2)} of ${formatArea(stat.totalM2!!)} cleared",
                    color = Dusk,
                    fontSize = 13.sp,
                )
            }
            extra()
        }
    }
}

/** Two significant digits, however tiny: 12%, 0.48%, 0.0000031%. */
internal fun formatPercent(fraction: Double): String {
    val pct = fraction * 100
    if (pct <= 0) return "0%"
    if (pct >= 10) return String.format(Locale.getDefault(), "%.0f%%", pct)
    val decimals = max(1, 1 - floor(log10(pct)).toInt())
    return String.format(Locale.getDefault(), "%.${decimals}f%%", pct)
}

internal fun formatArea(m2: Double): String = when {
    m2 < 10_000 -> String.format(Locale.getDefault(), "%.0f m²", m2)
    m2 < 1_000_000 -> String.format(Locale.getDefault(), "%.1f ha", m2 / 10_000)
    m2 < 100_000_000 -> String.format(Locale.getDefault(), "%.2f km²", m2 / 1_000_000)
    else -> String.format(Locale.getDefault(), "%,.0f km²", m2 / 1_000_000)
}

internal fun formatDistance(m: Double): String =
    if (m < 1000) "${m.toInt()} m" else String.format(Locale.getDefault(), "%.1f km", m / 1000)

/** "under a minute", "12 min", "1 h 05 min". */
internal fun formatDuration(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> "under a minute"
        min < 60 -> "$min min"
        else -> String.format(Locale.getDefault(), "%d h %02d min", min / 60, min % 60)
    }
}
