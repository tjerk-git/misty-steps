package com.mistysteps.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.mistysteps.app.BuildConfig

/** The shared "You" page on Android: your character and the settings, with the cards that need files and downloads. */
@Composable
fun AndroidYouScreen(
    onClose: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPicker: () -> Unit,
) {
    val context = LocalContext.current
    val catalog by rememberCatalog()
    val packManager = rememberPackManager()
    YouScreen(
        slots = YouSlots(
            mapsCard = { YourMapsCard(catalog?.provinces, packManager, onOpenPicker) },
            backupCard = { onImported -> BackupCard(rememberAndroidBackupHost(), onImported) },
            usageCard = { UsageCountsSwitch() },
            onFeedback = { sendFeedback(context) },
        ),
        versionName = BuildConfig.VERSION_NAME,
        onClose = onClose,
        onOpenAbout = onOpenAbout,
    )
}
