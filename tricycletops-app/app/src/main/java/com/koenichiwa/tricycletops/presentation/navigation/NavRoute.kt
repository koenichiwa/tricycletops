package com.koenichiwa.tricycletops.presentation.navigation

sealed class NavRoute(
    val route: String,
) {
    data object Scanner : NavRoute("scanner")

    data object Control : NavRoute("control")
}
