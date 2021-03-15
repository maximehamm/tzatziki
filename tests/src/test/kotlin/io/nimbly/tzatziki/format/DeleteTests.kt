package io.nimbly.tzatziki.format

import io.nimbly.tzatziki.AbstractTestCase

class DeleteTests  : AbstractTestCase() {

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
                Then FInished !
            """

        configure(content)
        selectAsColumn("Examples:\n     ", "| Yes   |         |")
        delete()
        backspace()
        checkContent(content)


        configure(content)
        selectAsColumn("Examples:\n    ", "| No    | D2")
        delete()
        backspace()
        checkContent(content)


        configure(content)
        selectAsColumn("Examples:\n   ", " | 79")
        delete()
        backspace()
        checkContent(content)


        configure(content)
        selectAsColumn("Examples:\n  ", "| Ready |")
        delete()
        backspace()
        checkContent(content)



        configure(content)
        selectAsColumn("Examples:\n ", "| 79  |")
        delete()
        backspace()
        checkContent(content)



        configure(content)
        selectAsColumn("Examples:\n", "| 79  |")
        delete()
        backspace()
        checkContent(content)



        configure(content)
        selectAsColumn(" Details |", " Details |\n")
        delete()
        backspace()
        checkContent(content)
    }

    fun testDeletionOk() {

        // language=feature
        configure(
            """
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
        selectAsColumn("|", "| NA")
        delete()
        // language=feature
        checkContent(
            """
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | NA  | Ready | Details |
                      | 78  | Yes   |         |
                      | 79  | No    | D2      |
                    Then FInished !
                """)

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
                    Then FInished !
                """)

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
                    Then FInished !
                """)


        select("Then The Cucumber", "Then FInished")
        delete()
        // language=feature
        checkContent(
            """
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber !
                """)
    }

}

