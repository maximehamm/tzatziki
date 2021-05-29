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

class DeleteTests : AbstractTestCase() {

    fun testDeletionBlocked() {

        // language=feature
        val content = """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF | Ready | Details |
                  | 78  | Yes   |         |
                  | 79  | No    | D2      |
                Then FInished !"""

        feature(content)
        selectAsColumn("Examples:\n     ", "| Yes   |         |")
        pressKey('x')
        checkContent(content)

        feature(content)
        selectAsColumn("Examples:\n    ", "| No    | D2")
        pressKey('x')
        checkContent(content)

        feature(content)
        selectAsColumn("Examples:\n   ", " | 79")
        pressKey('x')
        checkContent(content)

        feature(content)
        selectAsColumn("Examples:\n  ", "| Ready |")
        pressKey('x')
        checkContent(content)

        feature(content)
        selectAsColumn("Examples:\n ", "| 79  |")
        pressKey('x')
        checkContent(content)

        feature(content)
        selectAsColumn("Examples:\n", "| 79  |")
        pressKey('x')
        checkContent(content)



        feature(content)
        setCursor(" Details |")
        backspace()
        checkContent(content)

        feature(content)
        setCursor("| No    |")
        backspace()
        checkContent(content)

        feature(content)
        setCursor("| No    ")
        delete()
        checkContent(content)


        // End of table
        feature(content)
        setCursor("| D2      |")
        backspace()
        checkContent(content)

        // Left of table
        feature(content)
        setCursor("| Details |\n  ")
        backspace()
        delete()
        checkContent(content)

        // Left of first row table
        feature(content)
        setCursor("Examples:\n ")
        backspace()
        delete()
        checkContent(content)
    }

    fun testDeletionCleaning() {

        // language=feature
        val content = """
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | NAF | Ready | Details |
                      | 78  | Yes   |         |
                      | 79  | No    | D2      |
                    Then FInished !"""

        feature(content)
        selectAsColumn("Examples:\n     ", "| Yes   |         |")
        delete()
        // language=feature
        checkContent("""
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      |    |    |    |
                      |    |    |    |
                      | 79 | No | D2 |
                    Then FInished !""")


        feature(content)
        selectAsColumn("Examples:\n    ", "| No    | D2")
        delete()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  |  |  |  |
                  |  |  |  |
                  |  |  |  |
                Then FInished !""")


        feature(content)
        selectAsColumn("Examples:\n   ", " | 79")
        backspace()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  |  | Ready | Details |
                  |  | Yes   |         |
                  |  | No    | D2      |
                Then FInished !""")


        feature(content)
        selectAsColumn("Examples:\n  ", "| Ready |")
        backspace()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  |    |     | Details |
                  | 78 | Yes |         |
                  | 79 | No  | D2      |
                Then FInished !""")



        feature(content)
        selectAsColumn("Examples:\n ", "| 79  |")
        delete()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  |  | Ready | Details |
                  |  | Yes   |         |
                  |  | No    | D2      |
                Then FInished !""")



        feature(content)
        selectAsColumn("Examples:\n", "| 79  |")
        delete()
        // language=feature
        checkContent("""
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      |  | Ready | Details |
                      |  | Yes   |         |
                      |  | No    | D2      |
                    Then FInished !""")




        feature(content)
        setCursor("| NAF ")
        backspace()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF | Ready | Details |
                  | 78  | Yes   |         |
                  | 79  | No    | D2      |
                Then FInished !""")
        checkCursorAt("| NAF")
    }

    fun testDeletionCleaningMulti() {

        // language=feature
        val content = """
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | NAF | Ready | Details | Go   | Size | Origin |
                      | 78  | Yes   | D1      | No   | 43   | Paris  |
                      | 79  | No    | D2      | Yes! | 17   | Berlin |
                    Then FInished !"""

        feature(content)
        selectAsColumn("| NAF |", "| D2      |")
        delete()
        // language=feature
        checkContent("""
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | NAF |  |  | Go   | Size | Origin |
                      | 78  |  |  | No   | 43   | Paris  |
                      | 79  |  |  | Yes! | 17   | Berlin |
                    Then FInished !""")
        checkSelectionColumn("| NAF |", "| 79  |  |  |")

        // language=feature
        backspace()
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF | Go   | Size | Origin |
                  | 78  | No   | 43   | Paris  |
                  | 79  | Yes! | 17   | Berlin |
                Then FInished !""")
        checkSelectionColumn("| NAF |", "| 17   |")
    }

    fun testDeletionInCellOk() {

        // language=feature
        feature(
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
        setCursor("| NAF")
        backspace()
        // language=feature
        checkContent(
            """
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | NA | Ready | Details |
                      | 78 | Yes   |         |
                      | 79 | No    | D2      |
                    Then FInished !"""
        )

        backspace()
        // language=feature
        checkContent(
            """
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | N  | Ready | Details |
                      | 78 | Yes   |         |
                      | 79 | No    | D2      |
                    Then FInished !"""
        )

        backspace(5)
        // language=feature
        checkContent(
            """
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      |    | Ready | Details |
                      | 78 | Yes   |         |
                      | 79 | No    | D2      |
                    Then FInished !"""
        )


        select("Then The Cucumber", "Then FInished")
        delete()
        checkContent(
            """
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber !"""
        )
    }

    fun testDeletionInCellOutsideTableIsOk() {

        // language=feature
        feature(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:zz
                  | Q170 | elligibilite | motif |
                  | 10%  | OUI          | ok    |
                  | 39%  | OUI          | ok    |
                  | 40%  | OUI          | ok    |
                
                
                Then Bla, bla
                Examples: xx
                  | Q170 | elligibilite | motif |
                  | 10%  | OUI          | ok    |
                  | 39%  | OUI          | ok    |
            
                Then End""")

        setCursor("      | 40%  | OUI          | ok    |\n" +
            "    \n")

        backspace()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:zz
                  | Q170 | elligibilite | motif |
                  | 10%  | OUI          | ok    |
                  | 39%  | OUI          | ok    |
                  | 40%  | OUI          | ok    |
                
                Then Bla, bla
                Examples: xx
                  | Q170 | elligibilite | motif |
                  | 10%  | OUI          | ok    |
                  | 39%  | OUI          | ok    |
            
                Then End""")

        delete()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:zz
                  | Q170 | elligibilite | motif |
                  | 10%  | OUI          | ok    |
                  | 39%  | OUI          | ok    |
                  | 40%  | OUI          | ok    |
                    Then Bla, bla
                Examples: xx
                  | Q170 | elligibilite | motif |
                  | 10%  | OUI          | ok    |
                  | 39%  | OUI          | ok    |
            
                Then End""")

    }

    fun testDeletionInCellOutsideTableIsOk2() {

        // language=feature
        feature("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
            
            
                Examples:zz
                  | Q170 | elligibilite | motif |
                  | 10%  | OUI          | ok    |
                  | 39%  | OUI          | ok    |
                  | 40%  | OUI          | ok    |
            
            
                Then End""")

        select("formatted !\n", "| 40%  | OUI          | ok    |")
        backspace()
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
            
            
            
                Then End""")

    }

    fun testDeletionJustAfterIsBlocked() {

        // language=feature
        val content = """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !

                Examples:zz
                  | Q170 | elligibilite | motif |
                  | 10%  | OUI          | ok    |
                  | 39%  | OUI          | ok    |
                  | 40%  | OUI          | stop  |




                Then close to end !   
                Then End end !"""
        feature(content)

        select("| stop  |", "| stop  |\n\n")
        backspace(2)
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !

                Examples:zz
                  | Q170 | elligibilite | motif |
                  | 10%  | OUI          | ok    |
                  | 39%  | OUI          | ok    |
                  | 40%  | OUI          | stop  |


                Then close to end !   
                Then End end !""")

        select("| stop  |", "| stop  |\n\n")
        delete()
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !

                Examples:zz
                  | Q170 | elligibilite | motif |
                  | 10%  | OUI          | ok    |
                  | 39%  | OUI          | ok    |
                  | 40%  | OUI          | stop  |
                Then close to end !   
                Then End end !""")

        //
        feature(content)
        select("| stop  |", "Then close to end !")
        backspace()
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !

                Examples:zz
                  | Q170 | elligibilite | motif |
                  | 10%  | OUI          | ok    |
                  | 39%  | OUI          | ok    |
                  | 40%  | OUI          | stop  |   
                Then End end !""")
    }

    fun testDeletionOfPartOfColum() {

        // language=feature
        val content = """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  | Q170 | OK? | Percent | Reason | Count |
                  | 39%  | OUI | 3%      | OUI    | 14%   |
                  | 10%  | OUI | 1%      | OUI    | 10%   |
                  | 39%  | OUI | 3%      | OUI    | 39%   |
                  | 10%  | OUI | 1%      | OUI    | 10%   |
                  | 39%  | OUI | 3%      | OUI    | 39%   |
                  | 10%  | OUI | 1%      | OUI    | 10%   |
                  | 39%  | OUI | 3%      | OUI    | 39%   |"""

        feature(content)
        selectAsColumn("| 3%      | OUI    | 14",
            "| 3%      | OUI    | 39%")
        delete()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  | Q170 | OK? | Percent | Reason | Count |
                  | 39%  | OUI | 3%      | OUI    | 14    |
                  | 10%  | OUI | 1%      | OUI    | 10    |
                  | 39%  | OUI | 3%      | OUI    | 39    |
                  | 10%  | OUI | 1%      | OUI    | 10%   |
                  | 39%  | OUI | 3%      | OUI    | 39%   |
                  | 10%  | OUI | 1%      | OUI    | 10%   |
                  | 39%  | OUI | 3%      | OUI    | 39%   |""")
    }

    fun testDeletionOfEmptyColumns() {

        // language=feature
        val content = """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  | Q170 | OK? | Percent | Test | Count |
                  | 39%  | OUI | 39%     | aa   | 14%   |
                  | 10%  | OUI | 10%     | bb   | 10%   |
                  | 39%  | OUI | 39%     | cc   | 39%   |
                  | 20%  | OUI | 11%     | dd   | 10%   |
                  | 99%  | OUI | 89%     | ee   | 69%   |"""
        feature(content)

        selectAsColumn("| Percent |", "| ee   |")
        delete()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  | Q170 | OK? | Percent |  | Count |
                  | 39%  | OUI | 39%     |  | 14%   |
                  | 10%  | OUI | 10%     |  | 10%   |
                  | 39%  | OUI | 39%     |  | 39%   |
                  | 20%  | OUI | 11%     |  | 10%   |
                  | 99%  | OUI | 89%     |  | 69%   |""")
        checkSelectionColumn("| Percent |", "| 89%     |  |")

        delete()
        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  | Q170 | OK? | Percent | Count |
                  | 39%  | OUI | 39%     | 14%   |
                  | 10%  | OUI | 10%     | 10%   |
                  | 39%  | OUI | 39%     | 39%   |
                  | 20%  | OUI | 11%     | 10%   |
                  | 99%  | OUI | 89%     | 69%   |""")
        checkSelectionColumn("| Percent |", "| 69%   |")

        backspace()
        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  | Q170 | OK? | Percent |  |
                  | 39%  | OUI | 39%     |  |
                  | 10%  | OUI | 10%     |  |
                  | 39%  | OUI | 39%     |  |
                  | 20%  | OUI | 11%     |  |
                  | 99%  | OUI | 89%     |  |""")
        checkSelectionColumn("| Percent |", "| 89%     |  |")

        delete()
        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  | Q170 | OK? | Percent |
                  | 39%  | OUI | 39%     |
                  | 10%  | OUI | 10%     |
                  | 39%  | OUI | 39%     |
                  | 20%  | OUI | 11%     |
                  | 99%  | OUI | 89%     |""")
        checkSelectionColumn("| OK? |", "| 89%     |")

        delete()
        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  | Q170 | OK? |  |
                  | 39%  | OUI |  |
                  | 10%  | OUI |  |
                  | 39%  | OUI |  |
                  | 20%  | OUI |  |
                  | 99%  | OUI |  |""")
        checkSelectionColumn("| OK? |", "| 99%  | OUI |  |")

        delete()
        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  | Q170 | OK? |
                  | 39%  | OUI |
                  | 10%  | OUI |
                  | 39%  | OUI |
                  | 20%  | OUI |
                  | 99%  | OUI |""")
        checkSelectionColumn("| Q170 |", "| 99%  | OUI |")


        delete(2)
        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  | Q170 |
                  | 39%  |
                  | 10%  |
                  | 39%  |
                  | 20%  |
                  | 99%  |""")
        checkSelectionColumn("\n      ", "| 99%  |")


        delete()
        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  |  |
                  |  |
                  |  |
                  |  |
                  |  |
                  |  |""")

        delete()
        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  """)
    }

    fun testDeletionOfEmptyLines() {

        // language=feature
        val content = """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  | Q170 | OK? | Percent |  | Count |
                  | 10%  | OUI | 10%     |  | 10%   |
                  | 20%  | OUI | 11%     |  | 10%   |
                  | 39%  | OUI | 39%     |  | 39%   |
                  | 20%  | OUI | 11%     |  | 10%   |"""
        feature(content)
        selectAsColumn("| 10%     |  | 10%   |\n      ", "| 20%  | OUI | 11%     |  | 10%   |")
        delete()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  | Q170 | OK? | Percent |  | Count |
                  | 10%  | OUI | 10%     |  | 10%   |
                  |      |     |         |  |       |
                  | 39%  | OUI | 39%     |  | 39%   |
                  | 20%  | OUI | 11%     |  | 10%   |""")
        checkSelectionColumn("| 10%     |  | 10%   |\n      ", "|      |     |         |  |       |")

        delete()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  | Q170 | OK? | Percent |  | Count |
                  | 10%  | OUI | 10%     |  | 10%   |
                  | 39%  | OUI | 39%     |  | 39%   |
                  | 20%  | OUI | 11%     |  | 10%   |""")
        checkSelectionColumn("| 10%     |  | 10%   |\n      ", "| 39%   |")

        delete()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  | Q170 | OK? | Percent |  | Count |
                  | 10%  | OUI | 10%     |  | 10%   |
                  |      |     |         |  |       |
                  | 20%  | OUI | 11%     |  | 10%   |""")
        checkSelectionColumn("| 10%   |\n      ", "|      |     |         |  |       |")

        delete()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  | Q170 | OK? | Percent |  | Count |
                  | 10%  | OUI | 10%     |  | 10%   |
                  | 20%  | OUI | 11%     |  | 10%   |""")
        checkSelectionColumn("| 10%     |  | 10%   |\n      ", "| 20%  | OUI | 11%     |  | 10%   |")


        delete()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  | Q170 | OK? | Percent |  | Count |
                  | 10%  | OUI | 10%     |  | 10%   |
                  |      |     |         |  |       |""")
        checkSelectionColumn("| 10%     |  | 10%   |\n      ", "|      |     |         |  |       |")


        delete()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  | Q170 | OK? | Percent |  | Count |
                  | 10%  | OUI | 10%     |  | 10%   |""")
        checkSelectionColumn("| Count |\n      ", "| 10%  | OUI | 10%     |  | 10%   |")


        delete()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  | Q170 | OK? | Percent |  | Count |
                  |      |     |         |  |       |""")
        checkSelectionColumn("| Count |\n      ", "|      |     |         |  |       |")


        delete()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  | Q170 | OK? | Percent |  | Count |""")
        checkSelectionColumn("\n      ", "| Count |")


        delete()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  |  |  |  |  |  |""")
        checkSelectionColumn("\n      ", "|  |  |  |  |  |")


        delete()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  """)
    }

    fun testDeletionOfSinglePipe() {

        // language=feature
        val content = """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  |
            """
        feature(content)

        setCursor("|")
        backspace()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character
                Then The Cucumber table is automatically formatted !
                Examples:
                  
            """)
    }
}
