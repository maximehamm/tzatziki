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
    }

}
