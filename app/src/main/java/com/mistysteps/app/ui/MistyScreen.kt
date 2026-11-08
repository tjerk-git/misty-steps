package com.mistysteps.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mistysteps.app.character.Characters
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.data.PinStore
import com.mistysteps.app.pin.PinRules
import com.mistysteps.app.record.RecordingService

private enum class PermissionDialog { RATIONALE, SETTINGS }

/** Walks shorter than this don't pop up a postcard (still in the walk log). */
private const val MIN_POSTCARD_M = 50.0

/** What to do once location has been granted. */
private enum class AfterGrant { WANDER, LOCATE }

private val locationPermissions: Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
fun MistyScreen() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val map = remember { MapController(context) }
    val recording by RecordingService.recording.collectAsState()
    val distance by FogStore.distanceMeters.collectAsState()
    var showStats by rememberSaveable { mutableStateOf(false) }
    var showCharacters by rememberSaveable { mutableStateOf(false) }
    var postcardWalk by rememberSaveable { mutableStateOf<Long?>(null) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val inVehicle by FogStore.inVehicle.collectAsState()
    var dialog by remember { mutableStateOf<PermissionDialog?>(null) }
    var afterGrant by remember { mutableStateOf(AfterGrant.WANDER) }
    // A pin being placed or looked at. Not saved: a half-made pin is lost if the app is killed.
    var pinSheet by remember { mutableStateOf<PinSheetState?>(null) }

    fun currentPlace(): Pair<Double, Double>? =
        map.me.myLocation?.let { it.latitude to it.longitude } ?: FogStore.lastKnown

    // Redraw the fog whenever new ground is uncovered.
    LaunchedEffect(map) {
        FogStore.version.collect { map.refresh() }
    }

    // Pins: open one on tap, leave one with a long-press on ground you've cleared.
    LaunchedEffect(map) {
        map.setPinCallbacks(
            onTap = { pinSheet = PinSheetState.Open(it.id) },
            onCleared = { lat, lon -> pinSheet = PinSheetState.New(lat, lon, null) },
            onFog = { Toast.makeText(context, "Walk here first to leave a pin", Toast.LENGTH_SHORT).show() },
        )
    }
    LaunchedEffect(map) { PinStore.pins.collect { map.refresh() } }
    LaunchedEffect(map) { PinStore.justPlaced.collect { map.refresh() } }

    fun dropPin() {
        // Recording only hears from GPS every 5 m, so when you stand still use the map's fix too.
        val now = SystemClock.elapsedRealtimeNanos()
        val fix = PinRules.freshestFix(
            listOfNotNull(FogStore.lastGoodFix, map.me.lastFix),
            ageMs = { (now - it.elapsedRealtimeNanos) / 1_000_000 },
            accuracyM = { if (it.hasAccuracy()) it.accuracy else null },
        )
        if (fix == null) {
            Toast.makeText(context, "Looking for you in the mist…", Toast.LENGTH_SHORT).show()
        } else {
            pinSheet = PinSheetState.New(fix.latitude, fix.longitude, FogStore.session?.id)
        }
    }

    // A postcard when a walk ends (skip tiny ones, like a start-stop by mistake).
    LaunchedEffect(Unit) {
        FogStore.finishedWalks.collect { walk ->
            if (walk.distanceM >= MIN_POSTCARD_M) {
                showStats = false
                showCharacters = false
                postcardWalk = walk.id
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, map) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.resume(RecordingService.hasLocationPermission(context))
                Lifecycle.Event.ON_PAUSE -> map.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            map.destroy()
        }
    }

    fun canAskAgain() =
        activity?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) == true

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        when {
            granted[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                map.enableLocation()
                when (afterGrant) {
                    AfterGrant.WANDER -> startWandering(context)
                    AfterGrant.LOCATE -> if (!map.flyToMe()) {
                        Toast.makeText(context, "Looking for you in the mist…", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            // Denied for good (or only approximate allowed with "don't ask again"):
            // the system won't show its prompt any more, so offer the settings page.
            !canAskAgain() -> dialog = PermissionDialog.SETTINGS
            else -> Toast.makeText(context, "Precise location is needed to clear the fog", Toast.LENGTH_LONG).show()
        }
    }

    fun askForLocation(then: AfterGrant) {
        afterGrant = then
        if (canAskAgain()) dialog = PermissionDialog.RATIONALE
        else permissionLauncher.launch(locationPermissions)
    }

    fun onRecordClick() {
        when {
            recording -> RecordingService.stop(context)
            RecordingService.hasLocationPermission(context) -> startWandering(context)
            else -> askForLocation(AfterGrant.WANDER)
        }
    }

    SideEffect { map.setCovered(showStats || showCharacters || showAbout || postcardWalk != null) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { map.mapView }, modifier = Modifier.fillMaxSize())

        // Controls stay clear of status bar, navigation bar and camera cutouts.
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // Title + distance (tap for stats)
            Surface(
                onClick = { showStats = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
            ) {
                Column(
                    Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Misty Steps",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        letterSpacing = 1.5.sp,
                    )
                    Text(
                        "${formatDistance(distance)} of mist cleared",
                        fontSize = 12.sp,
                        color = Dusk,
                    )
                }
            }

            // Your character: opens character select
            CharacterButton(
                onClick = { showCharacters = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 180.dp),
            )

            // Stats
            FloatingActionButton(
                onClick = { showStats = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 108.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Ink,
                shape = CircleShape,
            ) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Stats")
            }

            // Fly back to me
            FloatingActionButton(
                onClick = {
                    if (!RecordingService.hasLocationPermission(context)) {
                        askForLocation(AfterGrant.LOCATE)
                    } else if (!map.flyToMe()) {
                        Toast.makeText(context, "Looking for you in the mist…", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 108.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Ink,
                shape = CircleShape,
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = "Show my location")
            }

            // Drop a pin where you stand (only while wandering).
            if (recording) {
                FloatingActionButton(
                    onClick = ::dropPin,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 180.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = Ink,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Filled.Place, contentDescription = "Drop a pin")
                }
            }

            // In a car, train or bus: clearing is paused until you're moving under your own power again.
            if (recording && inVehicle) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        "Too fast for the mist: it only clears under your own steam",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 13.sp,
                        color = Ink,
                    )
                }
            }

            // Start / stop wandering
            ExtendedFloatingActionButton(
                onClick = ::onRecordClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
                containerColor = if (recording) Ember else Ink,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(50),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    if (recording) {
                        PulsingDot()
                        Spacer(Modifier.width(12.dp))
                        Text("Stop wandering", fontSize = 16.sp)
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start wandering", fontSize = 16.sp)
                    }
                }
            }

            // Required by the OpenStreetMap tile usage policy.
            Text(
                "© OpenStreetMap contributors",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
                fontSize = 10.sp,
                color = Dusk,
            )
        }

        if (showStats) {
            StatsScreen(
                here = remember { currentPlace() },
                onClose = { showStats = false },
                onOpenWalk = { id ->
                    showStats = false
                    postcardWalk = id
                },
                onOpenAbout = { showAbout = true },
                onOpenPin = { id ->
                    PinStore.byId(id)?.let {
                        showStats = false
                        map.flyTo(it.lat, it.lon)
                        pinSheet = PinSheetState.Open(id)
                    }
                },
            )
        }
        if (showCharacters) {
            CharacterScreen(onClose = { showCharacters = false })
        }
        if (showAbout) {
            AboutScreen(onClose = { showAbout = false })
        }
        postcardWalk?.let { id ->
            PostcardScreen(walkId = id, onClose = { postcardWalk = null })
        }
    }

    when (val sheet = pinSheet) {
        is PinSheetState.New -> NewPinSheet(
            onPlace = { key, note ->
                PinStore.add(sheet.lat, sheet.lon, key, note, sheet.sessionId)
                pinSheet = null
            },
            onDismiss = { pinSheet = null },
        )
        is PinSheetState.Open -> {
            val pin = PinStore.byId(sheet.pinId)
            if (pin == null) {
                LaunchedEffect(sheet) { pinSheet = null }
            } else {
                PinDetailSheet(
                    pin = pin,
                    onSave = { key, note ->
                        PinStore.update(pin.id, key, note)
                        pinSheet = null
                    },
                    onDelete = {
                        PinStore.delete(pin.id)
                        pinSheet = null
                    },
                    onDismiss = { pinSheet = null },
                )
            }
        }
        null -> Unit
    }

    when (dialog) {
        PermissionDialog.RATIONALE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Your steps clear the fog") },
            text = {
                Text(
                    "Misty Steps needs your precise location while you wander, so it can lift the fog " +
                        "50 m around you. Your trail is only stored on this phone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    permissionLauncher.launch(locationPermissions)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Not now") } },
        )
        PermissionDialog.SETTINGS -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Location is switched off") },
            text = {
                Text(
                    "To clear the fog, allow Misty Steps to use your precise location in Settings " +
                        "(Permissions → Location → Allow only while using the app, with \"Use precise location\" on)."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    openAppSettings(context)
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Not now") } },
        )
        null -> Unit
    }
}

@Composable
private fun CharacterButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val character by Characters.selected.collectAsState()
    val frames = remember(character) { character?.let { SpriteFrames(it) } }
    // Always standing here, and the idle pose only changes every IDLE_FRAME_MS: no need to redraw more often.
    val ticker = rememberTicker(stepMs = Characters.IDLE_FRAME_MS)
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = CircleShape,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .drawBehind { frames?.let { drawSprite(it.at(walking = false, timeMs = ticker.value)) } }
        )
    }
}

@Composable
private fun PulsingDot() {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val a by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        Modifier
            .size(10.dp)
            // Read the animated value in the draw phase, so the dot doesn't recompose every frame.
            .graphicsLayer { alpha = a }
            .background(Color.White, CircleShape)
    )
}

private fun startWandering(context: Context) {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        Toast.makeText(context, "Turn on location to clear the fog", Toast.LENGTH_LONG).show()
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }
    RecordingService.start(context)
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

