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

class NewColumnTests  : AbstractTestCase() {

    fun testNewColumnFromHeader() {

        // language=feature
        feature("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF   | Ready | Details |
                  | 78.10 | Yes   | D1      |
                  | 78.2Z | No    | D2      |
            """)
        // insert pipe
        pressKey('|', "| Read")
        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF   | Ready |  | Details |
                  | 78.10 | Yes   |  | D1      |
                  | 78.2Z | No    |  | D2      |
            """
        )
        checkCursorAt("Ready | ")

        // insert pipe
        pressKey('|', "Details |")
        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF   | Ready |  | Details |  |
                  | 78.10 | Yes   |  | D1      |  |
                  | 78.2Z | No    |  | D2      |  |
            """
        )
        checkCursorAt("Details | ")

        // insert pipe
        pressKey('|', "    Examples:\n  ")
        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  |  | NAF   | Ready |  | Details |  |
                  |  | 78.10 | Yes   |  | D1      |  |
                  |  | 78.2Z | No    |  | D2      |  |
            """
        )
        checkCursorAt("| ")

        // insert pipe
        pressKey('|', "| NAF   | ")
        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  |  | NAF   |  | Ready |  | Details |  |
                  |  | 78.10 |  | Yes   |  | D1      |  |
                  |  | 78.2Z |  | No    |  | D2      |  |
            """
        )
        checkCursorAt("| NAF   | ")
    }

    fun testNewColumnFromHeader2A() {

        // language=feature
        feature("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF
            """)

        // insert pipe
        pressKey('|', "| NAF")

        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF |  |
            """
        )
        checkCursorAt("| NAF | ")
    }

    fun testNewColumnFromHeader2B() {

        // language=feature
        feature("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF 
            """)

        // insert pipe
        pressKey('|', "| NAF ")

        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF |  | 
            """
        )
        checkCursorAt("| NAF | ")
    }

    fun testBackTabSideEffectFix() {

        // language=feature
        feature("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF |  |
            """)

        setCursor("| NAF |  ")
        navigate(BACK)

        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF |  |
            """
        )
        checkCursorAt("| ")
    }

    fun testNewColumnFromAnyLine() {

        // language=feature
        feature("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF   | Ready | Details |
                  | 78.10 | Yes   | D1      |
                  | 78.2Z | No    | D2      |
            """)
        // insert pipe
        pressKey('|', "| Yes")
        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF   | Ready |  | Details |
                  | 78.10 | Yes   |  | D1      |
                  | 78.2Z | No    |  | D2      |
            """
        )
        checkCursorAt("| Yes   | ")

        // insert pipe
        pressKey('|', "| D2      |")
        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF   | Ready |  | Details |  |
                  | 78.10 | Yes   |  | D1      |  |
                  | 78.2Z | No    |  | D2      |  |
            """
        )
        checkCursorAt("| D2      | ")

        // insert pipe
        pressKey('|', "D1      |  |\n  ")
        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  |  | NAF   | Ready |  | Details |  |
                  |  | 78.10 | Yes   |  | D1      |  |
                  |  | 78.2Z | No    |  | D2      |  |
            """
        )
        checkCursorAt("D1      |  |\n      | ")
    }
}

