package com.mistysteps.app.ui

import com.mistysteps.app.maps.formatSize
import com.mistysteps.app.maps.formatSpeed
import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.data_download
import com.mistysteps.app.resources.download_detail
import com.mistysteps.app.resources.download_eta_min
import com.mistysteps.app.resources.download_eta_sec
import com.mistysteps.app.resources.download_pill
import com.mistysteps.app.resources.download_speed
import com.mistysteps.app.resources.download_starting
import com.mistysteps.app.resources.eraser_button
import com.mistysteps.app.resources.eraser_hint_zoom
import com.mistysteps.app.resources.eraser_done
import com.mistysteps.app.resources.eraser_undo
import com.mistysteps.app.resources.eraser_sheet_title
import com.mistysteps.app.resources.eraser_sheet_body
import com.mistysteps.app.resources.eraser_sheet_buy
import com.mistysteps.app.resources.eraser_sheet_not_now
import com.mistysteps.app.resources.eraser_taste_used
import com.mistysteps.app.resources.eraser_unavailable
import com.mistysteps.app.resources.eraser_thanks
import com.mistysteps.app.resources.offer_card
import com.mistysteps.app.resources.start_wandering
import com.mistysteps.app.resources.stop_wandering
import com.mistysteps.app.resources.show_my_location
import com.mistysteps.app.resources.drop_a_pin
import com.mistysteps.app.resources.banner_pin_tip
import com.mistysteps.app.resources.banner_start
import com.mistysteps.app.resources.notif_text_vehicle
import com.mistysteps.app.resources.dialog_rationale_title
import com.mistysteps.app.resources.dialog_rationale_text
import com.mistysteps.app.resources.dialog_continue
import com.mistysteps.app.resources.not_now
import com.mistysteps.app.resources.dialog_settings_title
import com.mistysteps.app.resources.ios_dialog_settings_text
import com.mistysteps.app.resources.open_settings
import com.mistysteps.app.resources.toast_location_off
import com.mistysteps.app.resources.toast_precise
import com.mistysteps.app.resources.mist_cleared
import com.mistysteps.app.resources.toast_looking
import com.mistysteps.app.resources.toast_pin_fog
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString

/** Short texts the Swift screens show themselves, from the shared (translated) strings. */
fun pinFogHint(): String = runBlocking { getString(Res.string.toast_pin_fog) }

fun pinLookingHint(): String = runBlocking { getString(Res.string.toast_looking) }

fun startWanderingText(): String = runBlocking { getString(Res.string.start_wandering) }

fun stopWanderingText(): String = runBlocking { getString(Res.string.stop_wandering) }

fun showMyLocationText(): String = runBlocking { getString(Res.string.show_my_location) }

fun dropAPinText(): String = runBlocking { getString(Res.string.drop_a_pin) }

fun bannerPinTipText(): String = runBlocking { getString(Res.string.banner_pin_tip) }

fun bannerStartText(): String = runBlocking { getString(Res.string.banner_start) }

fun notifTextVehicleText(): String = runBlocking { getString(Res.string.notif_text_vehicle) }

fun dialogRationaleTitleText(): String = runBlocking { getString(Res.string.dialog_rationale_title) }

fun dialogRationaleTextText(): String = runBlocking { getString(Res.string.dialog_rationale_text) }

fun dialogContinueText(): String = runBlocking { getString(Res.string.dialog_continue) }

fun notNowText(): String = runBlocking { getString(Res.string.not_now) }

fun dialogSettingsTitleText(): String = runBlocking { getString(Res.string.dialog_settings_title) }

fun iosDialogSettingsTextText(): String = runBlocking { getString(Res.string.ios_dialog_settings_text) }

fun openSettingsText(): String = runBlocking { getString(Res.string.open_settings) }

fun toastLocationOffText(): String = runBlocking { getString(Res.string.toast_location_off) }

fun toastPreciseText(): String = runBlocking { getString(Res.string.toast_precise) }

/** "55,3 km of mist cleared" for a distance in metres. */
fun mistClearedText(distanceM: Double): String = runBlocking { getString(Res.string.mist_cleared, formatDistance(distanceM)) }

/** "55,3 km" in the phone's language, same as the Android hub. */
fun formatDistanceText(distanceM: Double): String = formatDistance(distanceM)

/** "No map of Friesland yet. Download (62 MB)". */
fun offerCardText(province: String, bytes: Long): String = runBlocking { getString(Res.string.offer_card, province, formatSize(bytes)) }

fun dataDownloadText(): String = runBlocking { getString(Res.string.data_download) }

/** "Downloading Friesland". */
fun downloadPillText(name: String): String = runBlocking { getString(Res.string.download_pill, name) }

/** "34% · 20 MB of 58 MB · 3.2 MB/s · about 15 s left"; [secondsLeft] is -1 while the speed is unknown. */
fun downloadDetailText(bytes: Long, total: Long, bytesPerSecond: Long, secondsLeft: Int): String = runBlocking {
    val percent = if (total > 0) (bytes * 100 / total).toInt().coerceIn(0, 100) else 0
    if (bytesPerSecond <= 0) return@runBlocking "$percent% · " + getString(Res.string.download_starting)
    val speed = getString(Res.string.download_speed, formatSpeed(bytesPerSecond))
    val eta = when {
        secondsLeft < 0 -> ""
        secondsLeft < 90 -> " · " + getString(Res.string.download_eta_sec, secondsLeft)
        else -> " · " + getString(Res.string.download_eta_min, (secondsLeft + 59) / 60)
    }
    "$percent% · " + getString(Res.string.download_detail, formatSize(bytes), formatSize(total), speed) + eta
}

// ---- the magic eraser ------------------------------------------------------------------------------------------

fun eraserButtonText(): String = runBlocking { getString(Res.string.eraser_button) }

fun eraserHintZoomText(): String = runBlocking { getString(Res.string.eraser_hint_zoom) }

fun eraserDoneText(): String = runBlocking { getString(Res.string.eraser_done) }

fun eraserUndoText(): String = runBlocking { getString(Res.string.eraser_undo) }

fun eraserSheetTitleText(): String = runBlocking { getString(Res.string.eraser_sheet_title) }

fun eraserSheetBodyText(): String = runBlocking { getString(Res.string.eraser_sheet_body) }

/** "Get it for 3,99" ([price] is what the store shows). */
fun eraserSheetBuyText(price: String): String = runBlocking { getString(Res.string.eraser_sheet_buy, price) }

fun eraserSheetNotNowText(): String = runBlocking { getString(Res.string.eraser_sheet_not_now) }

fun eraserTasteUsedText(): String = runBlocking { getString(Res.string.eraser_taste_used) }

fun eraserUnavailableText(): String = runBlocking { getString(Res.string.eraser_unavailable) }

fun eraserThanksText(): String = runBlocking { getString(Res.string.eraser_thanks) }
