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

package io.nimbly.tzatziki.navigation

import io.nimbly.tzatziki.AbstractTestCase

class EnterTests  : AbstractTestCase() {

    fun testEnter1() {

        // language=feature
        feature("""
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

        // Navigate
        navigate(ENTER, "78")
        navigate(ENTER, "79")
        navigate(ENTER, "")

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
                  |     |       |         |
                Then FInished !"""
        )
    }

    fun testEnter2() {

        // language=feature
        feature("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF | Ready | Details |
                  | 78  | Yes   | D1      |
                  | 79  | No    | D2      |
                Then FInished !""")

        //
        setCursor("| D1      |")

        // Navigate
         navigate(ENTER, "")

        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF | Ready | Details |
                  | 78  | Yes   | D1      |
                  |     |       |         |
                  | 79  | No    | D2      |
                Then FInished !"""
        )
    }

    fun testEnterEOF() {

        // language=feature
        feature("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF | Ready | Details |
                  | 78  | Yes   | D1      |
                  | 79  | No    | D2      |""")

        //
        setCursor("| D2      |")

        // Navigate
        pressKey(ENTER)
        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF | Ready | Details |
                  | 78  | Yes   | D1      |
                  | 79  | No    | D2      |
                """)
    }

    fun testEnterLEFT() {

        // language=feature
        feature(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF | Ready | Details |
                  | 78  | Yes   | D1      |
                  | 79  | No    | D2      |
                """
        )


        setCursor("Examples:\n ")
        pressKey(ENTER)
        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
             
                  | NAF | Ready | Details |
                  | 78  | Yes   | D1      |
                  | 79  | No    | D2      |
                """)
        checkCursorAt("Examples:\n \n ")


        setCursor("| D1      |\n ")
        pressKey(ENTER)
        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
             
                  | NAF | Ready | Details |
                  | 78  | Yes   | D1      |
             
                  | 79  | No    | D2      |
                """)
        checkCursorAt("| D1      |\n \n ")
    }

}

