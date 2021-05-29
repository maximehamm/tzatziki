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

class MoveTests : AbstractTestCase() {

    fun testMoveColumns() {

        val content = """
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | NAF | Ready | Details |
                      | 78  | Yes   | D1      |
                      | 79  | No    | D2      |
                    Then FInished !"""

        // language=feature
        feature(content)
        setCursor("| NA")

        moveRight()
        checkContent("""
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | Ready | NAF | Details |
                      | Yes   | 78  | D1      |
                      | No    | 79  | D2      |
                    Then FInished !""")
        checkCursorAt("| Ready | ")
        checkHighlighted("| Ready |", "| 79  |")


        moveRight()
        checkContent("""
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | Ready | Details | NAF |
                      | Yes   | D1      | 78  |
                      | No    | D2      | 79  |
                    Then FInished !""")
        checkCursorAt("| Details | ")
        checkHighlighted("| Details |", "| 79  |")


        moveRight()
        checkContent("""
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | Ready | Details | NAF |
                      | Yes   | D1      | 78  |
                      | No    | D2      | 79  |
                    Then FInished !""")
        checkCursorAt("| Details | ")
        checkHighlighted("| Details |", "| 79  |")


        // Go back
        moveLeft()
        checkContent("""
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | Ready | NAF | Details |
                      | Yes   | 78  | D1      |
                      | No    | 79  | D2      |
                    Then FInished !""")
        checkCursorAt("| Ready | ")
        checkHighlighted("| Ready |", "| 79  |")

        moveLeft()
        checkContent("""
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | NAF | Ready | Details |
                      | 78  | Yes   | D1      |
                      | 79  | No    | D2      |
                    Then FInished !""")
        checkCursorAt("| ")
        checkHighlighted("|", "| 79  |")

        moveLeft()
        checkContent("""
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | NAF | Ready | Details |
                      | 78  | Yes   | D1      |
                      | 79  | No    | D2      |
                    Then FInished !""")
        checkCursorAt("| ")
        checkHighlighted("|", "| 79  |")
    }

    fun testMoveLines() {

        val content = """
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | NAF | Ready | Details |
                      | 78  | Yes   | D1      |
                      | 79  | No    | D2      |
                    Then FInished !"""

        // language=feature
        feature(content)
        setCursor("| NA")

        moveDown()
        checkContent("""
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | 78  | Yes   | D1      |
                      | NAF | Ready | Details |
                      | 79  | No    | D2      |
                    Then FInished !""")
        checkCursorAt("| NA")
        checkHighlighted("| D1      |\n      ", "| Details |")

        moveDown()
        checkContent("""
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | 78  | Yes   | D1      |
                      | 79  | No    | D2      |
                      | NAF | Ready | Details |
                    Then FInished !""")
        checkCursorAt("| NA")
        checkHighlighted("| D2      |\n      ", "| Details |")

        moveDown()
        checkContent("""
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | 78  | Yes   | D1      |
                      | 79  | No    | D2      |
                      | NAF | Ready | Details |
                    Then FInished !""")
        checkHighlighted("| D2      |\n      ", "| Details |")

        // GO back
        setCursor("| NA")
        moveUp()
        checkContent("""
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | 78  | Yes   | D1      |
                      | NAF | Ready | Details |
                      | 79  | No    | D2      |
                    Then FInished !""")
        checkCursorAt("| NA")
        checkHighlighted("| D1      |\n      ", "| Details |")

        moveUp()
        checkContent("""
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | NAF | Ready | Details |
                      | 78  | Yes   | D1      |
                      | 79  | No    | D2      |
                    Then FInished !""")
        checkCursorAt("| NA")
        checkHighlighted("Examples:\n      ", "| Details |")

        moveUp()
        checkContent("""
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | NAF | Ready | Details |
                      | 78  | Yes   | D1      |
                      | 79  | No    | D2      |
                    Then FInished !""")
        checkHighlighted("Examples:\n      ", "| Details |")
    }

}
