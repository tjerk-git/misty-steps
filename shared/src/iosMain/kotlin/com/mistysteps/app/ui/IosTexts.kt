package com.mistysteps.app.ui

import com.mistysteps.app.maps.formatSize
import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.data_download
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
