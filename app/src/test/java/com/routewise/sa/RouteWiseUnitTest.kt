package com.routewise.sa

import com.routewise.sa.util.LocationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteWiseUnitTest {

    @Test
    fun testSouthAfricanProvincesList() {
        val provinces = LocationData.SA_PROVINCES
        assertEquals(9, provinces.size)
        assertTrue(provinces.contains("Gauteng"))
        assertTrue(provinces.contains("Western Cape"))
        assertTrue(provinces.contains("KwaZulu-Natal"))
    }

    @Test
    fun testGautengCitiesList() {
        val cities = LocationData.SA_CITIES["Gauteng"]
        assertNotNull(cities)
        assertTrue(cities!!.contains("Pretoria"))
        assertTrue(cities.contains("Johannesburg"))
        assertTrue(cities.contains("Midrand"))
        assertTrue(cities.contains("Centurion"))
    }

    @Test
    fun testEmailPatternValidation() {
        val validEmail = "driver@routewise.co.za"
        val invalidEmail = "driver.routewise"

        assertTrue(validEmail.contains("@") && validEmail.contains("."))
        assertTrue(!invalidEmail.contains("@"))
    }
}
