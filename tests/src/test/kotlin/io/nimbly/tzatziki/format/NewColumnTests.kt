package io.nimbly.tzatziki.format

import io.nimbly.tzatziki.AbstractTestCase

class NewColumnTests  : AbstractTestCase() {

    fun testNewColumnFromHeader() {

        // language=feature
        configure("""
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

    fun testNewColumnFromAnyLine() {

        // language=feature
        configure("""
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

