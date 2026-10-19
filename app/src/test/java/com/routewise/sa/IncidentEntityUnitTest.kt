package com.routewise.sa

import com.routewise.sa.data.model.IncidentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentEntityUnitTest {

    @Test
    fun testIncidentEntityCreation() {
        val incident = IncidentEntity(
            id = "test_inc_1",
            type = "Pothole",
            roadName = "N1 North past Allandale",
            city = "Midrand",
            province = "Gauteng",
            latitude = -25.99880,
            longitude = 28.12650,
            timestamp = System.currentTimeMillis(),
            reportedBy = "Neo Madima",
            description = "Deep hole in middle lane",
            severity = "High",
            statusBox = "Active Alert",
            upvotes = 5
        )

        assertEquals("Pothole", incident.type)
        assertEquals("Midrand", incident.city)
        assertEquals("Neo Madima", incident.reportedBy)
        assertEquals(5, incident.upvotes)
        assertEquals("High", incident.severity)
    }

    @Test
    fun testHaversineDistanceCalculation() {
        val lat1 = -25.74950 // Pretoria CBD
        val lng1 = 28.19250
        val lat2 = -25.99880 // Midrand N1
        val lng2 = 28.12650

        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val distanceKm = earthRadiusKm * c

        assertTrue(distanceKm > 20.0 && distanceKm < 35.0)
    }
}
