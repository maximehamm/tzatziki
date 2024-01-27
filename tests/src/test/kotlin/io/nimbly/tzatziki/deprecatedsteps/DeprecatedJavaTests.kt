/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
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

import io.nimbly.tzatziki.AbstractJavaTestCase

class DeprecatedJavaTests : AbstractJavaTestCase() {

    fun testDeprecatedMethodJavaDoc() {

        // language=Java
        configure("""
            package io.nimbly;
            public class Easy {
                @io.cucumber.java.en.Given("This is given")
                public void thisIsGiven() {
                }
                /**
                 * @deprecated  As of release 1.x
                 */
                @io.cucumber.java.en.Then("This is easy as pie")
                public void thisIsEasyAsPie() {
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

    fun testDeprecatedMethodAnnotation() {

        // language=Java
        configure("""
            package io.nimbly;
            public class Easy {
                @io.cucumber.java.en.Given("This is given")
                public void thisIsGiven() {
                }
                @Deprecated
                @io.cucumber.java.en.Then("This is easy as pie")
                public void thisIsEasyAsPie() {
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

        // language=Java
        configure("""
            package io.nimbly;
            /**
             * @deprecated  As of release 1.x
             */
            public class Easy {
                @io.cucumber.java.en.Given("This is given")
                public void thisIsGiven() {
                }
                @io.cucumber.java.en.Then("This is easy as pie")
                public void thisIsEasyAsPie() {
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