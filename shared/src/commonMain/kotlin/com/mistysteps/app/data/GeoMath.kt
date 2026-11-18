package com.mistysteps.app.data

/** Degree/radian conversion with the same constants as java.lang.Math, so results match on every platform. */
fun toRadians(degrees: Double): Double = degrees * 0.017453292519943295

fun toDegrees(radians: Double): Double = radians * 57.29577951308232
