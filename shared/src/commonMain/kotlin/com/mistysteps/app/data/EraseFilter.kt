package com.mistysteps.app.data

/** The spots a person walked (or that date from before walks were logged): what the trail line is drawn from. Erased spots are not walked. */
fun walkedOnly(points: List<TrailPoint>): List<TrailPoint> = points.filter { it.eraseStroke == null }
