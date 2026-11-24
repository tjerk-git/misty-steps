package com.mistysteps.app.ui

import com.mistysteps.app.data.Session

/** Which walks you can swipe through on the postcard screen. */
object PostcardPaging {
    /**
     * The finished walks of the log ([walksToShow]; a walk still going cannot be opened from the log, so it is no page either), newest first (a walk's id is the time it started), plus the walk you opened
     * [startId], which is always among them even when the log would not list it (a walk of 40 m that just ended).
     */
    fun walkIds(walks: List<Session>, walksWithPins: Set<Long>, startId: Long): List<Long> =
        (walksToShow(walks, walksWithPins).filter { it.endTime != null }.map { it.id } + startId).distinct().sortedDescending()
}
