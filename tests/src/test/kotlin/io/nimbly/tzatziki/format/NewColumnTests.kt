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
        pressKey('|')

        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF   | Ready | Details |
                  | 78.10 | YesX  |  xxx       |
                  | 78.2Z |       |         |
            """
        )
        checkCursorAt("| YesX")


    }

}

