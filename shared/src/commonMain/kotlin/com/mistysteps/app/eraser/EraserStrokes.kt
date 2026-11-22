package com.mistysteps.app.eraser

import com.mistysteps.app.data.TrailPoint
import com.mistysteps.app.map.LatLon

/** The spots of an eraser drag as stored ones: tagged with the stroke [id], never with a walk, one millisecond apart from [now] on so each is unique. */
fun strokeFor(id: Long, now: Long, path: List<LatLon>, radiusM: Double): EraserStroke =
    EraserStroke(id, spotsToPoints(id, now, EraserRules.spotsAlong(path, radiusM), radiusM))

/** [spots] as stored points of stroke [id], the first at time [firstTime], the next one millisecond later each. */
fun spotsToPoints(id: Long, firstTime: Long, spots: List<LatLon>, radiusM: Double): List<TrailPoint> =
    spots.mapIndexed { i, s -> TrailPoint(s.lat, s.lon, firstTime + i, radiusM, null, id) }
