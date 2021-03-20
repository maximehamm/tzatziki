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
        configure(content)
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
        configure(content)
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
