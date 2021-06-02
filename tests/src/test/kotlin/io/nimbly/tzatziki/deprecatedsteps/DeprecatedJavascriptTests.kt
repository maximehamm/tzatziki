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

import io.nimbly.tzatziki.AbstractJavascriptTestCase
import org.junit.Ignore

@Ignore
class DeprecatedJavascriptTests : AbstractJavascriptTestCase() {

    fun testDeprecatedFunction() {

        // language=Js
        configure("""
            easy(function ({ Given, When, Then }) {
                Given('This is given', function () {
                    return 2;
                });
                Given('This is easy as pie',
                    easyAsPie);
                /**
                 * @deprecated Will be deleted soon.
                 */
                function easyAsPie() {
                    return 1;
                }
            });""")

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

    fun testDeprecatedAll() {

        // language=Js
        configure("""
            /**
             * @deprecated Will be deleted soon.
             */
            easy(function ({ Given, When, Then }) {
                Given('This is given', function () {
                    return 2;
                });
                Given('This is easy as pie',
                    easyAsPie);
                function easyAsPie() {
                    return 1;
                }
            });""")

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