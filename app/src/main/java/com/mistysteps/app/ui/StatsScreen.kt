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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistysteps.app.BuildConfig
import com.mistysteps.app.R
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.data.PinStore
import com.mistysteps.app.pin.Pin
import com.mistysteps.app.pin.PinLabel
import com.mistysteps.app.pin.textRes
import com.mistysteps.app.pin.PinRules
import com.mistysteps.app.data.Session
import com.mistysteps.app.debug.BatteryLog
import com.mistysteps.app.stats.AreaKind
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
    onOpenPin: (Long) -> Unit,
    onOpenPicker: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val catalog by rememberCatalog()
    val context = LocalContext.current
    val repo = remember { StatsRepository.get(context) }
    var stats by remember { mutableStateOf<Stats?>(null) }
    var walks by remember { mutableStateOf<List<Session>>(emptyList()) }
    var hood by remember { mutableStateOf<NeighbourhoodResult?>(null) }
    // Bumped after an import, so everything is counted again.
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(here, refresh) {
        stats = repo.compute(here?.first, here?.second)
        if (here != null) hood = repo.neighbourhood(here.first, here.second)
    }
    // Streets come last: counting them against your trail takes a moment.
    var streets by remember { mutableStateOf<StreetStats?>(null) }
    var streetsMissing by remember { mutableStateOf(false) }
    val hoodBoundary = hood?.stat?.boundary
    LaunchedEffect(hoodBoundary, refresh) {
        if (hoodBoundary == null) return@LaunchedEffect
        streets = repo.streets(hoodBoundary)
        streetsMissing = streets == null
    }
    LaunchedEffect(refresh) { walks = withContext(Dispatchers.IO) { FogStore.loadSessions() } }
    val pins by PinStore.pins.collectAsState()
    val pinsByWalk = remember(pins) {
        pins.filter { it.sessionId != null }.sortedBy { it.time }.groupBy { it.sessionId!! }
    }

    Box(
        Modifier
            .fillMaxSize()
            .blockTouchesBelow()
            .background(Brush.verticalGradient(listOf(Color(0xFFF4F5FA), Color(0xFFDCE1EE))))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .scrollingPage()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_to_map), tint = Ink)
                }
                Text(
                    stringResource(R.string.stats_title),
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
                        Text(stringResource(R.string.stats_measuring), color = Dusk)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallStat(stringResource(R.string.stat_wandered), formatDistance(s.distanceM), Modifier.weight(1f))
                    SmallStat(stringResource(R.string.stat_cleared), formatArea(s.clearedM2), Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                if (here != null) {
                    NeighbourhoodCard(hood, streets, streetsMissing)
                    Spacer(Modifier.height(12.dp))
                }
                s.city?.let { AreaCard(it); Spacer(Modifier.height(12.dp)) }
                s.country?.let { AreaCard(it); Spacer(Modifier.height(12.dp)) }
                AreaCard(s.world) {
                    Text(stringResource(R.string.stats_plenty_left), color = Dusk, fontSize = 13.sp)
                }
                s.note?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(it.text), color = Dusk, fontSize = 14.sp)
                }
                val shown = remember(walks, pinsByWalk) { walksToShow(walks, pinsByWalk.keys) }
                if (shown.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.stats_walks_header).uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = Dusk)
                    Spacer(Modifier.height(8.dp))
                    WalkLog(shown, pinsByWalk, onOpenWalk)
                }
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.stats_places_header).uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = Dusk)
                Spacer(Modifier.height(8.dp))
                YourPlaces(pins, onOpenPin)
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.maps_title).uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = Dusk)
                Spacer(Modifier.height(8.dp))
                YourMapsCard(catalog, onOpenPicker)
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.stats_privacy),
                    color = Dusk,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(stringResource(R.string.stats_more_header).uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = Dusk)
            Spacer(Modifier.height(8.dp))
            BackupCard(onImported = { refresh++ })
            Spacer(Modifier.height(12.dp))
            UsageCountsSwitch()
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
                Text(stringResource(R.string.feedback_row_title), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.feedback_row_sub), color = Dusk, fontSize = 12.sp)
            }
            Icon(Icons.Filled.Email, contentDescription = null, tint = Ink)
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
                Text(stringResource(R.string.made_by_title), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.made_by_sub), color = Dusk, fontSize = 12.sp)
                Text(stringResource(R.string.version_app, BuildConfig.VERSION_NAME), color = Dusk, fontSize = 12.sp)
            }
            Icon(Icons.Filled.FavoriteBorder, contentDescription = null, tint = Ember)
        }
    }
}

@Composable
private fun WalkLog(walks: List<Session>, pinsByWalk: Map<Long, List<Pin>>, onOpenWalk: (Long) -> Unit) {
    val when_ = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val durationText = rememberDurationText()
    var showAll by remember { mutableStateOf(false) }
    val visible = if (showAll) walks else walks.take(RECENT_WALKS)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Frost, shadowElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            for ((i, w) in visible.withIndex()) {
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
                            add(if (running) stringResource(R.string.walk_now) else formatDuration(w.endTime!! - w.startTime, durationText))
                            w.newAreaM2?.let { add(stringResource(R.string.walk_new_mist, formatArea(it))) }
                            // Battery use per walk, for test runs.
                            if (BatteryLog.enabled && w.batteryStart != null && w.batteryEnd != null) {
                                add("battery ${w.batteryStart}% → ${w.batteryEnd}%")
                            }
                        }
                        Text(details.joinToString(" · "), color = Dusk, fontSize = 12.sp)
                        pinsByWalk[w.id]?.let { walkPins ->
                            val (shown, more) = PinRules.walkBadge(walkPins)
                            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                for (label in shown) {
                                    PinIcon(label, 16)
                                    Spacer(Modifier.width(4.dp))
                                }
                                if (more > 0) Text("+$more", color = Dusk, fontSize = 12.sp)
                            }
                        }
                    }
                    Text(formatDistance(w.distanceM), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (walks.size > RECENT_WALKS) {
                TextButton(onClick = { showAll = !showAll }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (showAll) stringResource(R.string.walks_show_fewer) else stringResource(R.string.walks_show_all, walks.size), color = Ember)
                }
            }
        }
    }
}

/** How many walks the log shows before "Show all". */
private const val RECENT_WALKS = 5

/** Walks shorter than this are left out of the log and get no postcard: a start-stop by mistake. */
internal const val MIN_WALK_M = 50.0

/** The walks worth listing: not the tiny ones, unless one is still going or has pins on it. */
internal fun walksToShow(walks: List<Session>, walksWithPins: Set<Long>): List<Session> =
    walks.filter { it.endTime == null || it.distanceM >= MIN_WALK_M || it.id in walksWithPins }

/** Your pins, grouped by label. Tap a group to open it, tap a pin to see it on the map. */
@Composable
private fun YourPlaces(pins: List<Pin>, onOpenPin: (Long) -> Unit) {
    val day = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val groups = remember(pins) { PinRules.groupByLabel(pins) }
    var open by remember { mutableStateOf<PinLabel?>(null) }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Frost, shadowElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            if (groups.isEmpty()) {
                Text(
                    stringResource(R.string.pins_empty),
                    color = Dusk,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            for ((i, group) in groups.withIndex()) {
                val (label, labelPins) = group
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE3E7F1)))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { open = if (open == label) null else label }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PinIcon(label, 28)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${stringResource(label.textRes)} (${labelPins.size})",
                        color = Ink,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(if (open == label) "–" else "+", color = Dusk, fontSize = 18.sp)
                }
                if (open == label) {
                    for (pin in labelPins) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenPin(pin.id) }
                                .padding(start = 40.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PinIcon(pin.label, 18)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                pin.note ?: stringResource(pin.label.textRes),
                                color = Ink,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(day.format(Date(pin.time)), color = Dusk, fontSize = 12.sp)
                        }
                    }
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
    streetsMissing: Boolean,
) {
    val stat = result?.stat
    when {
        stat != null -> AreaCard(stat.area) {
            when {
                streets != null -> Streets(streets)
                streetsMissing -> Unit // never saved on this phone: just the area
                else -> {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(14.dp).width(14.dp), color = Dusk, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.streets_counting), color = Dusk, fontSize = 13.sp)
                    }
                }
            }
        }
        result == null -> Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Frost, shadowElevation = 2.dp) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), color = Ink, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.hood_finding), color = Dusk, fontSize = 14.sp)
            }
        }
        result.note != null -> Text(stringResource(result.note.text), color = Dusk, fontSize = 14.sp)
        else -> Unit // no neighbourhoods mapped here
    }
}

@Composable
private fun ColumnScope.Streets(st: StreetStats) {
    if (st.total == 0) return
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.streets_header).uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = Dusk)
    Text(
        stringResource(R.string.streets_walked, st.walked, st.total),
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
        Text(stringResource(R.string.streets_still), fontSize = 13.sp, color = Dusk)
        for (p in next) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(p.name, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (p.walkedFraction > 0.05) {
                    Text(stringResource(R.string.streets_pct_walked, (p.walkedFraction * 100).toInt()), color = Dusk, fontSize = 13.sp)
                }
            }
        }
        if (st.toExplore.size > next.size) {
            Text(stringResource(R.string.streets_more, st.toExplore.size - next.size), color = Dusk, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun AreaCard(stat: AreaStat, extra: @Composable ColumnScope.() -> Unit = {}) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Frost, shadowElevation = 2.dp) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(stat.kind.label).uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = Dusk)
            Text(areaName(stat), fontFamily = FontFamily.Serif, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.height(10.dp))
            val f = stat.fraction
            if (f == null) {
                Text(stringResource(R.string.stats_note_outside), color = Dusk, fontSize = 14.sp)
            } else {
                Text(if (f < ONE_IN_BELOW) stringResource(R.string.area_one_in, formatOneIn(f)) else formatPercent(f), fontFamily = FontFamily.Serif, fontSize = 34.sp, color = Ink)
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
                    stringResource(R.string.area_cleared_of, formatArea(stat.clearedM2), formatArea(stat.totalM2!!)),
                    color = Dusk,
                    fontSize = 13.sp,
                )
            }
            extra()
        }
    }
}

/** The name shown on an area card: Earth, the country in the phone's language, or the place's own name. */
@Composable
private fun areaName(stat: AreaStat): String = when {
    stat.name == null -> stringResource(R.string.area_earth)
    // Only the Netherlands has borders, and the border data names it in English.
    stat.kind == AreaKind.COUNTRY ->
        Locale.Builder().setRegion("NL").build().getDisplayCountry(Locale.getDefault()).ifBlank { stat.name }
    else -> stat.name
}
