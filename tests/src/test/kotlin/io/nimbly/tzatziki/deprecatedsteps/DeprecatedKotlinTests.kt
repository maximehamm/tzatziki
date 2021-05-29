/*
 * CUCUMBER +
 * Copyright (C) 2021  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
 *
 * This document is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This work is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package io.nimbly.tzatziki.deprecatedsteps

import io.nimbly.tzatziki.AbstractKotlinTestCase

class DeprecatedKotlinTests : AbstractKotlinTestCase() {

    fun testDeprecatedMethod() {

        // language=Kt
        configure("""
            package easy
            import io.cucumber.java.en.Given
            import io.cucumber.java.en.Then
            class Easy {
                @Given("This is given")
                fun thisIsGiven() {
                }
                @kotlin.Deprecated("")
                @Then("This is easy as pie")
                fun thisIsEasyAsPie() {
                }
            }""")

        // language=feature
        feature("""
            Feature: Some feature
              Scenario: A scenario
                Given This is given
                Then This is easy as pie
            """)

        // Check markers
        val markers = createJavaMarkers()
        markerExists(markers, "Deprecated step", 1)
        markersCount(markers, 1)
    }

    fun testDeprecatedClass() {

        // language=Kt
        configure("""
            package easy
            import io.cucumber.java.en.Given
            import io.cucumber.java.en.Then
            @kotlin.Deprecated("")
            class Easy {
                @Given("This is given")
                fun thisIsGiven() {
                }
                @Then("This is easy as pie")
                fun thisIsEasyAsPie() {
                }
            }""")

        // language=feature
        feature("""
            Feature: Some feature
              Scenario: A scenario
                Given This is given
                Then This is easy as pie
            """)

        // Check markers
        val markers = createJavaMarkers()
        markerExists(markers, "Deprecated step", 2)
        markersCount(markers, 2)
    }

}