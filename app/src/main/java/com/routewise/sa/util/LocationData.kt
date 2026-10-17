package com.routewise.sa.util

object LocationData {
    val SA_PROVINCES = listOf(
        "Gauteng",
        "Western Cape",
        "KwaZulu-Natal",
        "Eastern Cape",
        "Free State",
        "Limpopo",
        "Mpumalanga",
        "North West",
        "Northern Cape"
    )

    val SA_CITIES = mapOf(
        "Gauteng" to listOf("Johannesburg", "Pretoria", "Centurion", "Sandton", "Midrand", "Soweto", "Ekurhuleni"),
        "Western Cape" to listOf("Cape Town", "Stellenbosch", "George", "Paarl", "Somerset West"),
        "KwaZulu-Natal" to listOf("Durban", "Pietermaritzburg", "Umhlanga", "Ballito", "Richards Bay"),
        "Eastern Cape" to listOf("Gqeberha (PE)", "East London", "Mthatha", "Makhanda"),
        "Free State" to listOf("Bloemfontein", "Welkom", "Sasolburg", "Bethlehem"),
        "Limpopo" to listOf("Polokwane", "Tzaneen", "Thohoyandou", "Mokopane"),
        "Mpumalanga" to listOf("Mbombela (Nelspruit)", "eMalahleni (Witbank)", "Secunda"),
        "North West" to listOf("Rustenburg", "Potchefstroom", "Klerksdorp", "Mahikeng"),
        "Northern Cape" to listOf("Kimberley", "Upington", "Springbok")
    )
}
