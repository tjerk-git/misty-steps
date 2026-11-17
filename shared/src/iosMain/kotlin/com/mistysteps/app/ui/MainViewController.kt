package com.mistysteps.app.ui

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import platform.UIKit.UIViewController

/** The shared Compose UI as a UIKit view controller, for the iOS app to host. */
fun sharedHelloViewController(): UIViewController = ComposeUIViewController { SharedHello() }

/** The consent page (anonymous open count) as a full-screen view controller; [onAnswer] gets the person's choice. */
fun consentViewController(onAnswer: (Boolean) -> Unit): UIViewController =
    ComposeUIViewController { MistyTheme { ConsentScreen(onAnswer) } }

/** The three first-run pages; [onDone] after the last page, [onSkip] from the Skip link. */
fun onboardingViewController(onDone: () -> Unit, onSkip: () -> Unit): UIViewController =
    ComposeUIViewController { MistyTheme { OnboardingScreen(onDone, onSkip) } }

/** The about page; [versionName] and [feedbackDetails] come from the app (version, device model). */
fun aboutViewController(versionName: String, feedbackDetails: String, onClose: () -> Unit): UIViewController =
    ComposeUIViewController { MistyTheme { AboutScreen(versionName, feedbackDetails, onClose) } }

/** The anonymous open-count switch, kept in NSUserDefaults. */
fun usageCardViewController(versionName: String): UIViewController = ComposeUIViewController {
    MistyTheme {
        val defaults = platform.Foundation.NSUserDefaults.standardUserDefaults
        var on by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(defaults.boolForKey("usage_opt_in")) }
        UsageCountsCard(optedIn = on, onOptedInChange = { on = it; defaults.setBool(it, forKey = "usage_opt_in") }, versionName = versionName)
    }
}

/** The "choose your wanderer" page. */
fun characterViewController(onClose: () -> Unit): UIViewController =
    ComposeUIViewController { MistyTheme { CharacterScreen(onClose) } }

/** "Which provinces do you want to explore?"; [provinces] null means the catalog could not be loaded. */
fun provincePickerViewController(
    provinces: List<com.mistysteps.app.maps.PackEntry>?,
    packs: com.mistysteps.app.maps.PackManager,
    preselected: Set<String>,
    existingUser: Boolean,
    onDone: () -> Unit,
): UIViewController = ComposeUIViewController {
    MistyTheme { ProvincePickerScreen(provinces, packs, preselected, existingUser, onDone) }
}
