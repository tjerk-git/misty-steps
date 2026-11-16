package com.mistysteps.app.maps

object OfferRules {
    /**
     * Whether the "No map of <province> yet" card shows: only inside a known province whose map is not on
     * the phone, not being downloaded, and that the user has not said "Not now" to.
     */
    fun shouldOffer(here: Province?, state: PackState?, dismissed: Set<String>): Boolean =
        here != null && state is PackState.NotDownloaded && here.id !in dismissed
}
