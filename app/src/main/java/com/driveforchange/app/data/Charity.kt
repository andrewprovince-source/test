package com.driveforchange.app.data

data class Charity(
    val id: String,
    val name: String,
    val cause: String
)

object Charities {
    val starterList = listOf(
        Charity("red_cross", "American Red Cross", "Disaster relief"),
        Charity("st_jude", "St. Jude Children's Research Hospital", "Children's health"),
        Charity("feeding_america", "Feeding America", "Hunger"),
        Charity("wwf", "World Wildlife Fund", "Environment"),
        Charity("aspca", "ASPCA", "Animal welfare"),
        Charity("doctors_without_borders", "Doctors Without Borders", "Global health"),
    )

    val default = starterList.first()

    fun byId(id: String?): Charity =
        starterList.firstOrNull { it.id == id } ?: default
}
