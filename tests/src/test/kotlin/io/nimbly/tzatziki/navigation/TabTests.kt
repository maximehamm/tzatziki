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

package io.nimbly.tzatziki.navigation

import io.nimbly.tzatziki.AbstractTestCase

class TabTests  : AbstractTestCase() {

    fun testTab() {

        // language=feature
        configure("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF | Ready | Details |
                  | 78  | Yes   |         |
                  | 79  | No    | D2      |
                Then FInished !""")

        //
        setCursor("| NAF")

        // expected sequence
        val sequence = arrayOf(
            "Ready", "Details",
            "78", "Yes", "",
            "79", "No", "D2",
            "NAF")

        // go forward
        for (i in sequence.indices)
            navigate(TAB, sequence[i])

        // go backward
        for (i in sequence.size - 2 downTo 0)
            navigate(BACK, sequence[i])

        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF | Ready | Details |
                  | 78  | Yes   |         |
                  | 79  | No    | D2      |
                Then FInished !"""
        )
    }

    fun testTabHeader() {

        // language=feature
        configure("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF | Ready | Details |
                Then FInished !""")

        //
        setCursor("| NAF")

        // go forward
        navigate(TAB, "Ready")
        navigate(TAB, "Details")
        navigate(TAB, "")

        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF | Ready | Details |  |
                Then FInished !""")

        checkCursorAt("| Details | ")
    }

}

