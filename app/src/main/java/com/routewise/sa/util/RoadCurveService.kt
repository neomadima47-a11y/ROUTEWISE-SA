package com.routewise.sa.util

import org.osmdroid.util.GeoPoint
import kotlin.math.*

object RoadCurveService {
    /**
     * Catmull-Rom spline interpolation to generate smooth curves between road coordinates
     */
    fun smoothRoadCurvature(points: List<GeoPoint>, subdivisions: Int = 4): List<GeoPoint> {
        if (points.size < 3) return points

        val smoothed = mutableListOf<GeoPoint>()

        for (i in 0 until points.size - 1) {
            val p0 = points[max(0, i - 1)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[min(points.size - 1, i + 2)]

            smoothed.add(p1)

            for (step in 1 until subdivisions) {
                val t = step.toDouble() / subdivisions
                val t2 = t * t
                val t3 = t2 * t

                val lat = 0.5 * (
                    2 * p1.latitude +
                    (-p0.latitude + p2.latitude) * t +
                    (2 * p0.latitude - 5 * p1.latitude + 4 * p2.latitude - p3.latitude) * t2 +
                    (-p0.latitude + 3 * p1.latitude - 3 * p2.latitude + p3.latitude) * t3
                )

                val lng = 0.5 * (
                    2 * p1.longitude +
                    (-p0.longitude + p2.longitude) * t +
                    (2 * p0.longitude - 5 * p1.longitude + 4 * p2.longitude - p3.longitude) * t2 +
                    (-p0.longitude + 3 * p1.longitude - 3 * p2.longitude + p3.longitude) * t3
                )

                smoothed.add(GeoPoint(lat, lng))
            }
        }

        smoothed.add(points.last())
        return smoothed
    }

    /**
     * Compute smooth compass bearing (heading 0°-360°) between two coordinates
     */
    fun calculateBearing(start: GeoPoint, end: GeoPoint): Float {
        val startLat = Math.toRadians(start.latitude)
        val startLng = Math.toRadians(start.longitude)
        val endLat = Math.toRadians(end.latitude)
        val endLng = Math.toRadians(end.longitude)

        val dLng = endLng - startLng

        val y = sin(dLng) * cos(endLat)
        val x = cos(startLat) * sin(endLat) - sin(startLat) * cos(endLat) * cos(dLng)

        val brng = Math.toDegrees(atan2(y, x))
        return (((brng + 360) % 360)).toFloat()
    }
}
