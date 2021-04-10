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

package io.nimbly.tzatziki.format

import io.nimbly.tzatziki.AbstractTestCase

class FormatTests  : AbstractTestCase() {

    fun testFormat() {

        // language=feature
        configure("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF   | Ready | Details |
                  | 78.10 | Yes   |         |
                  | 78.2Z |       |         |
                """)

        // insert char and check
        insert("X", "| Yes")

        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF   | Ready | Details |
                  | 78.10 | YesX  |         |
                  | 78.2Z |       |         |
                """
        )
        checkCursorAt("| YesX")

        // insert char and check
        insert("ABCD", "| YesX")

        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF   | Ready    | Details |
                  | 78.10 | YesXABCD |         |
                  | 78.2Z |          |         |
                """
        )
        checkCursorAt("| YesXABCD")

        // insert char and check
        backspace(1, "| YesXABCD")

        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF   | Ready   | Details |
                  | 78.10 | YesXABC |         |
                  | 78.2Z |         |         |
                """
        )
        checkCursorAt("| YesXABC")
    }

    fun testMultiCursorFormat() {

        // language=feature
        configure(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF   | Ready | Details |
                  | 78.10 | Yes   |         |
                  | 78.2Z | No    |         |
                  | 88.4B | Maybe |         |
                """
        )

        // Select
        selectAsColumn("| 78.10 | Yes   | ", "| 88.4B | Maybe | ")

        // insert char and check
        insert("X")

        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF   | Ready | Details |
                  | 78.10 | Yes   | X       |
                  | 78.2Z | No    | X       |
                  | 88.4B | Maybe | X       |
                """
        )
    }

}

