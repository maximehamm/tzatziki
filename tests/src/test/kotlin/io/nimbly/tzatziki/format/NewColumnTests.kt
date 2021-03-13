package io.nimbly.tzatziki.format

import io.nimbly.tzatziki.AbstractTestCase

class NewColumnTests  : AbstractTestCase() {

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
        backspace(1, "| Berlutti")

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

}

