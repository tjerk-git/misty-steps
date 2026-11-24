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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.mistysteps.app.BuildConfig
import com.mistysteps.app.R
import com.mistysteps.app.maps.PackDownloads
import com.mistysteps.app.eraser.EraserAccessHolder
import com.mistysteps.app.eraser.EraserBar
import com.mistysteps.app.eraser.EraserButton
import com.mistysteps.app.eraser.EraserSheet
import com.mistysteps.app.eraser.EraserState
import com.mistysteps.app.eraser.BuyResult
import kotlinx.coroutines.launch
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mistysteps.app.maps.FirstRun
import com.mistysteps.app.maps.FirstRunStage
import com.mistysteps.app.stats.Analytics
import com.mistysteps.app.maps.MapsGraph
import com.mistysteps.app.maps.OfferRules
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.mistysteps.app.stats.PillKey
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import com.mistysteps.app.stats.PillRefresh
import com.mistysteps.app.stats.AndroidStatsSource
import androidx.lifecycle.repeatOnLifecycle
import com.mistysteps.app.announce.RatAnnouncement
import com.mistysteps.app.character.Characters
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.data.PinStore
import com.mistysteps.app.pin.PinRules
import com.mistysteps.app.record.RecordingService

private enum class PermissionDialog { RATIONALE, SETTINGS }

/** Set once the long-press pin tip has been closed. */
private const val PIN_TIP_SEEN = "pin_tip_seen"
private const val ONBOARDING_DONE = "onboarding_done"
private const val PICKER_SEEN = "picker_seen"
private const val OFFER_DISMISSED = "offer_dismissed"

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
    var showYou by rememberSaveable { mutableStateOf(false) }
    var postcardWalk by rememberSaveable { mutableStateOf<Long?>(null) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    // First launch: the stage shown on top of the map (a FirstRunStage name): onboarding, the province picker.
    var firstRun by rememberSaveable { mutableStateOf<String?>(null) }
    var existingUser by rememberSaveable { mutableStateOf(false) }
    // Steps finished in this run: they count as done at once, even for the debug `fresh` start that ignores the saved flags.
    var doneSteps by rememberSaveable { mutableStateOf(listOf<String>()) }
    val fresh = BuildConfig.DEBUG && activity?.intent?.getBooleanExtra("fresh", false) == true
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var pickerPreselect by rememberSaveable { mutableStateOf(listOf<String>()) }
    val catalog by rememberCatalog()
    // Installed maps that came before stats files get theirs quietly (unmetered connection only, no prompt).
    LaunchedEffect(catalog) { catalog?.let { PackDownloads.enqueueMissingStats(context, it) } }
    val packManager = rememberPackManager()
    val inVehicle by FogStore.inVehicle.collectAsState()
    var dialog by remember { mutableStateOf<PermissionDialog?>(null) }
    var afterGrant by remember { mutableStateOf(AfterGrant.WANDER) }
    // A pin being placed or looked at. Not saved: a half-made pin is lost if the app is killed.
    var pinSheet by remember { mutableStateOf<PinSheetState?>(null) }

    // The magic eraser: what you may do with it, whether it is in hand, and the purchase sheet.
    val eraserScope = androidx.compose.runtime.rememberCoroutineScope()
    val purchases = EraserAccessHolder.purchases
    val owned by purchases.owned.collectAsState()
    val price by purchases.priceText.collectAsState()
    val tasteLedger = remember { EraserAccessHolder.ledger(context) }
    var tasteUsedM by remember { mutableStateOf(tasteLedger.usedM) }
    val eraserState = EraserAccessHolder.state(context, owned, tasteUsedM)
    var eraserActive by remember { mutableStateOf(false) }
    var eraserCanUndo by remember { mutableStateOf(false) }
    var showEraserSheet by remember { mutableStateOf(false) }
    var eraserTasteUsedUp by remember { mutableStateOf(false) }

    fun currentPlace(): Pair<Double, Double>? =
        map.myLocation()?.let { it.lat to it.lon } ?: FogStore.lastKnown

    // Redraw the fog whenever new ground is uncovered.
    LaunchedEffect(map) {
        FogStore.version.collect { map.refresh() }
    }

    // Pins: open one on tap, leave one with a long-press on ground you've cleared.
    LaunchedEffect(map) {
        map.setPinCallbacks(
            onTap = { pinSheet = PinSheetState.Open(it.id) },
            onCleared = { lat, lon -> pinSheet = PinSheetState.New(lat, lon, null) },
            onFog = { Toast.makeText(context, context.getString(R.string.toast_pin_fog), Toast.LENGTH_SHORT).show() },
        )
    }
    LaunchedEffect(map) { PinStore.pins.collect { map.refresh() } }
    LaunchedEffect(map) { PinStore.justPlaced.collect { map.refresh() } }

    fun dropPin() {
        // Recording only hears from GPS every 5 m, so when you stand still use the map's fix too.
        val now = SystemClock.elapsedRealtimeNanos()
        val fix = PinRules.freshestFix(
            listOfNotNull(FogStore.lastGoodFix, map.lastFix()),
            ageMs = { (now - it.elapsedRealtimeNanos) / 1_000_000 },
            accuracyM = { if (it.hasAccuracy()) it.accuracy else null },
        )
        if (fix == null) {
            Toast.makeText(context, context.getString(R.string.toast_looking), Toast.LENGTH_SHORT).show()
        } else {
            pinSheet = PinSheetState.New(fix.latitude, fix.longitude, FogStore.session?.id)
        }
    }

    // Long-press to leave a pin can't be seen, so after the first real walk say so, once.
    val hints = remember { context.getSharedPreferences("misty", Context.MODE_PRIVATE) }
    var showPinTip by remember { mutableStateOf(false) }
    fun dismissPinTip() {
        showPinTip = false
        hints.edit().putBoolean(PIN_TIP_SEEN, true).apply()
    }

    fun stageNow(): String = FirstRun.stage(
        onboardingDone = "ONBOARDING" in doneSteps || (!fresh && hints.getBoolean(ONBOARDING_DONE, false)),
        pickerSeen = "PICKER" in doneSteps || (!fresh && hints.getBoolean(PICKER_SEEN, false)),
        hasTrailPoints = existingUser,
    ).name

    fun finishStep(step: FirstRunStage) {
        doneSteps = doneSteps + step.name
        firstRun = stageNow()
    }

    // First launch: onboarding for a new install, the picker for everyone who hasn't seen it. Existing users
    // (with trail points) skip the onboarding and get the province they walked most ticked.
    LaunchedEffect(Unit) {
        if (firstRun != null) return@LaunchedEffect
        // Debug builds: `am start ... --ez fresh true` shows the new-install flow without touching the trail data.
        val points = withContext(Dispatchers.IO) { FogStore.loadAllPoints() }
        existingUser = !fresh && points.isNotEmpty()
        pickerPreselect = if (existingUser) listOfNotNull(MapsGraph.provinces.mostWalked(points)?.id) else emptyList()
        firstRun = stageNow()
    }

    // A postcard when a walk ends (skip tiny ones, like a start-stop by mistake).
    LaunchedEffect(Unit) {
        FogStore.finishedWalks.collect { walk ->
            if (walk.distanceM >= MIN_WALK_M && !hints.getBoolean(PIN_TIP_SEEN, false)) showPinTip = true
            if (walk.distanceM >= MIN_WALK_M) {
                showStats = false
                showYou = false
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
                        Toast.makeText(context, context.getString(R.string.toast_looking), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            // Denied for good (or only approximate allowed with "don't ask again"):
            // the system won't show its prompt any more, so offer the settings page.
            !canAskAgain() -> dialog = PermissionDialog.SETTINGS
            else -> Toast.makeText(context, context.getString(R.string.toast_precise), Toast.LENGTH_LONG).show()
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

    SideEffect {
        map.setCovered(showStats || showYou || showAbout || showPicker || postcardWalk != null || firstRun == FirstRunStage.ONBOARDING.name || firstRun == FirstRunStage.PICKER.name)
    }

    // The pill at the top: buckets of mist cleared in your city or town. Recounted while the map is on screen, off the main
    // thread; a failed count keeps the old text.
    var pill by remember { mutableStateOf<PillContent?>(null) }
    var pillHeightPx by remember { mutableStateOf(0) }
    val pageOpen = showStats || showYou || showAbout || showPicker || postcardWalk != null ||
        firstRun == FirstRunStage.ONBOARDING.name || firstRun == FirstRunStage.PICKER.name
    // The banner and the eraser bar go under the pill, however tall it is (two lines, a large font): at least where they always were.
    val bannerTop = maxOf(88.dp, 12.dp + with(LocalDensity.current) { pillHeightPx.toDp() } + 12.dp)
    val pillLifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(pageOpen) {
        if (pageOpen) return@LaunchedEffect
        pillLifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val source = AndroidStatsSource(context)
            var lastKey: PillKey? = null
            while (true) {
                val here = currentPlace()
                // Recount only when the cleared ground or your spot changed: counting reads every point you ever cleared.
                val key = PillRefresh.key(FogStore.version.value.toLong(), here?.first, here?.second)
                if (key != lastKey) {
                    val content = try {
                        pillContent(source.compute(here?.first, here?.second))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    if (content != null) {
                        pill = content
                        lastKey = key
                    }
                }
                delay(PillRefresh.INTERVAL_MS)
            }
        }
    }

    // The eraser: wire what it may do, put it down when another page covers the map, keep it in step with the state.
    LaunchedEffect(Unit) {
        if (EraserAccessHolder.debugOverride(context) == 2) { tasteLedger.reset(); tasteUsedM = 0.0 }
    }
    LaunchedEffect(eraserState) {
        val c = map.eraser
        c.allowance = {
            when (eraserState) {
                is EraserState.Unlocked -> Double.POSITIVE_INFINITY
                is EraserState.Taste -> tasteLedger.metersLeft
                else -> 0.0
            }
        }
        c.onSpent = { metres -> if (eraserState is EraserState.Taste) { tasteLedger.spend(metres); tasteUsedM = tasteLedger.usedM } }
        c.onOutOfAllowance = { eraserActive = false; eraserTasteUsedUp = true; showEraserSheet = true }
        c.onZoomedOut = { Toast.makeText(context, context.getString(R.string.eraser_hint_zoom), Toast.LENGTH_SHORT).show() }
        c.onUndoChanged = { eraserCanUndo = c.canUndo }
    }
    val eraserUsable = eraserState is EraserState.Unlocked || eraserState is EraserState.Taste
    SideEffect { map.eraser.active = eraserActive && eraserUsable }
    LaunchedEffect(showStats, showYou, showAbout, showPicker, postcardWalk) {
        if (showStats || showYou || showAbout || showPicker || postcardWalk != null) eraserActive = false
    }

    // The location button and the pointer to your character (shown when it is off-screen) do the same thing.
    val locate: () -> Unit = {
        if (!RecordingService.hasLocationPermission(context)) {
            askForLocation(AfterGrant.LOCATE)
        } else if (!map.flyToMe()) {
            Toast.makeText(context, context.getString(R.string.toast_looking), Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { map.mapView }, modifier = Modifier.fillMaxSize())
        OffscreenPointerLayer(map.pointer, locate)

        // Controls stay clear of status bar, navigation bar and camera cutouts.
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // Buckets of mist cleared where you are (tap for stats); nothing until the first count is in.
            pill?.let { p ->
                Surface(
                    onClick = { showStats = true },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .onSizeChanged { pillHeightPx = it.height },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                ) {
                    Column(
                        Modifier.widthIn(max = 320.dp).padding(horizontal = 24.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            p.value,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 26.sp,
                            color = Ink,
                        )
                        Text(p.caption, fontSize = 12.sp, lineHeight = 15.sp, color = Dusk, textAlign = TextAlign.Center)
                        p.place?.let { Text(it, fontSize = 12.sp, lineHeight = 15.sp, color = Dusk, textAlign = TextAlign.Center) }
                    }
                }
            }

            // The magic eraser: picks it up, or shows the purchase sheet when it is not yours yet.
            if (eraserState !is EraserState.NotAvailable) {
                EraserButton(
                    active = eraserActive && eraserUsable,
                    onClick = {
                        if (eraserUsable) eraserActive = !eraserActive
                        else { eraserTasteUsedUp = false; showEraserSheet = true }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, bottom = 252.dp),
                )
            }

            // Your character: opens the You page
            CharacterButton(
                onClick = { showYou = true },
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
                Icon(painterResource(R.drawable.ic_stats), contentDescription = "Stats")
            }

            // Fly back to me
            FloatingActionButton(
                onClick = locate,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 108.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Ink,
                shape = CircleShape,
            ) {
                Icon(painterResource(R.drawable.ic_my_location), contentDescription = stringResource(R.string.show_my_location))
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
                    Icon(Icons.Filled.Place, contentDescription = stringResource(R.string.drop_a_pin))
                }
            }

            // One message at a time under the title: in a vehicle, the pin tip, or a welcome for a new map.
            val banner: Pair<String, (() -> Unit)?>? = when {
                eraserActive -> null
                // In a car, train or bus: clearing is paused until you're moving under your own power again.
                recording && inVehicle -> stringResource(R.string.notif_text_vehicle) to null
                showPinTip && !recording -> stringResource(R.string.banner_pin_tip) to ::dismissPinTip
                !recording && distance == 0.0 -> stringResource(R.string.banner_start) to null
                else -> null
            }
            banner?.let { (text, onTap) ->
                Surface(
                    onClick = { onTap?.invoke() },
                    enabled = onTap != null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = bannerTop, start = 32.dp, end = 32.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Ink,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (eraserActive && eraserUsable) {
                EraserBar(
                    canUndo = eraserCanUndo,
                    onUndo = { map.eraser.undo() },
                    onDone = { eraserActive = false },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = bannerTop),
                )
            }
            if (showEraserSheet) {
                EraserSheet(
                    priceText = price,
                    tasteUsed = eraserTasteUsedUp,
                    onBuy = {
                        eraserScope.launch {
                            when (purchases.buy()) {
                                BuyResult.BOUGHT -> {
                                    showEraserSheet = false
                                    Toast.makeText(context, context.getString(R.string.eraser_thanks), Toast.LENGTH_SHORT).show()
                                }
                                BuyResult.UNAVAILABLE -> Toast.makeText(context, context.getString(R.string.eraser_unavailable), Toast.LENGTH_SHORT).show()
                                BuyResult.CANCELLED -> Unit
                            }
                        }
                    },
                    onNotNow = { showEraserSheet = false },
                )
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
                        Text(stringResource(R.string.stop_wandering), fontSize = 16.sp)
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.start_wandering), fontSize = 16.sp)
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

        // "No map of <province> yet": offered once per province while the app is open inside it.
        val packVersion = MapsGraph.packs.version.collectAsState().value
        var dismissed by remember { mutableStateOf(hints.getStringSet(OFFER_DISMISSED, emptySet()).orEmpty().toSet()) }
        val place = remember(firstRun, packVersion, catalog) { currentPlace() }
        val province = place?.let { MapsGraph.provinces.provinceAt(it.first, it.second) }
        val offered = province?.let { catalog?.entry(it.id) }
        if (firstRun == FirstRunStage.DONE.name && !recording && province != null && offered != null &&
            OfferRules.shouldOffer(province, MapsGraph.packs.state(offered), dismissed)
        ) {
            OfferCard(
                province, offered,
                onDownload = { pickerPreselect = listOf(province.id); showPicker = true },
                onNotNow = {
                    dismissed = dismissed + province.id
                    hints.edit().putStringSet(OFFER_DISMISSED, dismissed).apply()
                },
                // Clear of the buttons at both sides and of the Start button.
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 84.dp, end = 84.dp, bottom = 112.dp),
            )
        }

        // While a province downloads: what is happening, how fast and how long is left. Tap opens the maps list.
        DownloadPill(
            catalog?.provinces, packManager,
            onClick = { pickerPreselect = emptyList(); showPicker = true },
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 84.dp, end = 84.dp, bottom = 112.dp),
        )

        if (showStats) {
            AndroidStatsScreen(
                here = remember { currentPlace() },
                onClose = { showStats = false },
                onOpenWalk = { id ->
                    showStats = false
                    postcardWalk = id
                },
                onOpenPin = { id ->
                    PinStore.byId(id)?.let {
                        showStats = false
                        map.flyTo(it.lat, it.lon)
                        pinSheet = PinSheetState.Open(id)
                    }
                },
            )
        }
        if (showYou) {
            AndroidYouScreen(
                onClose = { showYou = false },
                onOpenAbout = { showAbout = true },
                onOpenPicker = { pickerPreselect = emptyList(); showPicker = true },
            )
        }
        postcardWalk?.let { walkId ->
            PostcardScreen(walkId = walkId, host = rememberPostcardHost(), onClose = { postcardWalk = null })
        }
        if (showAbout) {
            AboutScreen(versionName = BuildConfig.VERSION_NAME, feedbackDetails = feedbackDetails(), onClose = { showAbout = false })
        }
        if (showPicker) {
            ProvincePickerScreen(catalog?.provinces, packManager, pickerPreselect.toSet(), existingUser = false, onDone = { showPicker = false })
        }
        if (firstRun == FirstRunStage.ONBOARDING.name) {
            val next = { hints.edit().putBoolean(ONBOARDING_DONE, true).apply(); finishStep(FirstRunStage.ONBOARDING) }
            OnboardingScreen(onDone = next, onSkip = next)
        }
        if (firstRun == FirstRunStage.PICKER.name) {
            ProvincePickerScreen(
                catalog?.provinces, packManager, pickerPreselect.toSet(), existingUser,
                onDone = {
                    hints.edit().putBoolean(ONBOARDING_DONE, true).putBoolean(PICKER_SEEN, true).apply()
                    finishStep(FirstRunStage.PICKER)
                },
            )
        }
    }

    // The one-time Christmas card that offers the Santa Rat, once the first-run steps are done.
    var ratSeen by remember { mutableStateOf(hints.getBoolean(RatAnnouncement.SEEN_KEY, false)) }
    val firstRunStage = firstRun?.let { FirstRunStage.valueOf(it) }
    if (firstRunStage != null && RatAnnouncement.due(firstRunStage, ratSeen)) {
        fun markRatSeen() {
            hints.edit().putBoolean(RatAnnouncement.SEEN_KEY, true).apply()
            ratSeen = true
        }
        RatCard(
            onTry = {
                Characters.all.firstOrNull { it.id == RatAnnouncement.CHARACTER_ID }?.let { Characters.select(it) }
                markRatSeen()
            },
            onLater = ::markRatSeen,
        )
    }

    when (val sheet = pinSheet) {
        is PinSheetState.New -> NewPinSheet(
            onPlace = { key, note ->
                PinStore.add(sheet.lat, sheet.lon, key, note, sheet.sessionId)
                if (sheet.sessionId == null && showPinTip) dismissPinTip() // found it
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
            title = { Text(stringResource(R.string.dialog_rationale_title)) },
            text = {
                Text(stringResource(R.string.dialog_rationale_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    permissionLauncher.launch(locationPermissions)
                }) { Text(stringResource(R.string.dialog_continue)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.not_now)) } },
        )
        PermissionDialog.SETTINGS -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.dialog_settings_title)) },
            text = {
                Text(stringResource(R.string.dialog_settings_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    openAppSettings(context)
                }) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.not_now)) } },
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
    // This button opens the "You" page, where the settings live: a screen reader needs to hear that.
    val label = stringResource(R.string.you_title)
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
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
        Toast.makeText(context, context.getString(R.string.toast_location_off), Toast.LENGTH_LONG).show()
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }
    RecordingService.start(context)
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

