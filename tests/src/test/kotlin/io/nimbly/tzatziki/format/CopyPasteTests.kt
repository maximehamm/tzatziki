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

class CopyPasteTests  : AbstractTestCase() {

    fun testCopy() {

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
                Then FInished !
            """)

        selectAsColumn("| NAF |", "| D2      ")
        copy()

        checkClipboard("""
                Ready	Details
                Yes	
                No	D2""")
    }

    fun testCopyPaste() {

        // language=feature
        feature("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF | Ready | Details |
                  | 78  | Yes   |         |
                  | 79  | No    | D2      |
                Examples: Two
                  | Title | Size |
                  | A     | 22   |
                  | C     | 144  |
                Then Finished !""")

        selectAsColumn("|", "| No    ")
        copy()
        setCursor("Title | Si")
        paste()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF | Ready | Details |
                  | 78  | Yes   |         |
                  | 79  | No    | D2      |
                Examples: Two
                  | Title | NAF | Ready |
                  | A     | 78  | Yes   |
                  | C     | 79  | No    |
                Then Finished !""")
        checkHighlighted("| Title ", "| C     | 79  | No    |")

        setCursor("| C     | 79  | No")
        paste()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF | Ready | Details |
                  | 78  | Yes   |         |
                  | 79  | No    | D2      |
                Examples: Two
                  | Title | NAF | Ready |       |
                  | A     | 78  | Yes   |       |
                  | C     | 79  | NAF   | Ready |
                  |       |     | 78    | Yes   |
                  |       |     | 79    | No    |
                Then Finished !""")
        checkHighlighted("| C     | 79  ", "|       |     | 79    | No    |")

    }

    fun testCopyPasteAfterLastColumn() {

        // language=feature
        feature("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF | Ready | Details |
                  | 78  | Yes   |         |
                  | 79  | No    | D2      |
                Then Finished !""")

        selectAsColumn("|", "| No    ")
        copy()

        setCursor("| Details |")
        paste()

        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF | Ready | Details | NAF | Ready |
                  | 78  | Yes   |         | 78  | Yes   |
                  | 79  | No    | D2      | 79  | No    |
                Then Finished !""")
    }

    fun testCopyPasteAfterLastLine() {

        // language=feature
        feature("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF | Ready | Details |
                  | 78  | Yes   |         |
                  | 79  | No    | D2      |
                
                Then Finished !""")

        selectAsColumn("|", "| No    ")
        copy()

        setCursor("| D2      |\n ")
        paste()

        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF | Ready | Details |
                  | 78  | Yes   |         |
                  | 79  | No    | D2      |
                  | NAF | Ready |         |
                  | 78  | Yes   |         |
                  | 79  | No    |         |
                
                Then Finished !""")
    }

    fun testCopyPasteBeforeFirstColumn() {

        // language=feature
        feature("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF | Ready | Details |
                  | 78  | Yes   |         |
                  | 79  | No    | D2      |
                Then Finished !""")

        selectAsColumn("|", "| No    ")
        copy()

        setCursor("Examples: One\n  ")
        paste()

        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF | Ready | NAF | Ready | Details |
                  | 78  | Yes   | 78  | Yes   |         |
                  | 79  | No    | 79  | No    | D2      |
                Then Finished !""")
    }

    fun testCutPaste() {

        // language=feature
        val content = """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF | Ready | Details |
                  | 78  | Yes   |         |
                  | 79  | No    | D2      |
                Examples: Two
                  | Title | Size |
                  | A     | 22   |
                  | C     | 144  |
                Then Finished !"""

        feature(content)
        selectAsColumn("|", "| No    ")
        cut()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  |  |  | Details |
                  |  |  |         |
                  |  |  | D2      |
                Examples: Two
                  | Title | Size |
                  | A     | 22   |
                  | C     | 144  |
                Then Finished !""")


        selectAsColumn("Examples: Two\n      |", "| 144")
        cut()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  |  |  | Details |
                  |  |  |         |
                  |  |  | D2      |
                Examples: Two
                  |  |  |
                  |  |  |
                  |  |  |
                Then Finished !""")
        checkClipboard("""
                Title	Size
                A	22
                C	144""")
    }

    fun testCutPasteOutsideTable() {

        // language=feature
        val content = """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF | Ready | Details |
                  | 78  | Yes   |         |
                  | 79  | No    | D2      |
                Examples: Two
                  | Title | Size |
                  | A     | 22   |
                  | C     | 144  |
                Then Finished !"""
        feature(content)


        select("formatted !\n", "| D2      |\n")
        cut()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: Two
                  | Title | Size |
                  | A     | 22   |
                  | C     | 144  |
                Then Finished !""")

        checkClipboard("""
                Examples: One
                  | NAF | Ready | Details |
                  | 78  | Yes   |         |
                  | 79  | No    | D2      |
            """)
    }

    fun testCopyPasteTinyTable() {

        // language=feature
        feature("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  |
                  
                Examples: Two
                  | Title | NAF | Ready |
                  | A     | 78  | Yes   |
                  | C     | 79  | No    |
                Then Finished !""")

        selectAsColumn("| Title |", "| 79  | No    |")
        copy()

        setCursor("|")
        paste()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF | Ready |
                  | 78  | Yes   |
                  | 79  | No    |
                  
                Examples: Two
                  | Title | NAF | Ready |
                  | A     | 78  | Yes   |
                  | C     | 79  | No    |
                Then Finished !""")
        checkHighlighted("Examples: One\n      ", "| 79  | No    |")



        // language=feature
        feature("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  |    
                  
                Examples: Two
                  | Title | NAF | Ready |
                  | A     | 78  | Yes   |
                  | C     | 79  | No    |
                Then Finished !""")

        selectAsColumn("| Title |", "| 79  | No    |")
        copy()

        setCursor("|    ")
        paste()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF | Ready |
                  | 78  | Yes   |
                  | 79  | No    |    
                  
                Examples: Two
                  | Title | NAF | Ready |
                  | A     | 78  | Yes   |
                  | C     | 79  | No    |
                Then Finished !""")
        checkHighlighted("Examples: One\n      ", "| 79  | No    |")
    }

    fun testCutPasteAfterTable() {

        // language=feature
        val content = """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF | Ready | Details |
                  | 78  | Yes   |         |
                  | 79  | No    | D2      |
                  
                  
            """
        feature(content)

        select("Feature: Tzatziki y Cucumber\n", "| D2      |\n")
        copy()

        setCursor("| 79  | No    | D2      |\n")
        paste()

        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF | Ready | Details |
                  | 78  | Yes   |         |
                  | 79  | No    | D2      |
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF | Ready | Details |
                  | 78  | Yes   |         |
                  | 79  | No    | D2      |
                  
                  
            """)
    }

    fun testCopyPasteMultiCursors() {

        // language=feature
        feature("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF   | Ready | Details |
                  | 78.10 | Yes   |         |
                  | 78.2Z | No    |         |
                  | 88.4B | Maybe |         |
                Then Finished !""")

        selectAsColumn(" 78.10 | ", "| Maybe")
        copy()

        // Select
        selectAsColumn("| 78.10 | Yes   | ", "| 88.4B | Maybe | ")
        paste()

        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF   | Ready | Details |
                  | 78.10 | Yes   | Yes     |
                  | 78.2Z | No    | No      |
                  | 88.4B | Maybe | Maybe   |
                Then Finished !""")
    }

    fun testCopyPasteMultiCursors2() {

        // language=feature
        feature("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF   | Ready | Details |
                  | 78.10 | Yes   |         |
                  | 78.2Z | No    |         |
                  | 88.4B | Maybe |         |
                Then Finished !""")

        selectAsColumn(" 78.", ".4B")
        copy()

        // Select
        selectAsColumn("| 78.10 | Yes   | ", "| 88.4B | Maybe | ")
        paste()

        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF   | Ready | Details |
                  | 78.10 | Yes   | 10      |
                  | 78.2Z | No    | 2Z      |
                  | 88.4B | Maybe | 4B      |
                Then Finished !""")

        // Select
        selectAsColumn("| 10 ", "| 4B ")
        paste()

        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: One
                  | NAF   | Ready | Details |
                  | 78.10 | Yes   | 10 10   |
                  | 78.2Z | No    | 2Z 2Z   |
                  | 88.4B | Maybe | 4B 4B   |
                Then Finished !""")
    }
}

