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
                Then FInished !
            """

        configure(content)
        selectAsColumn("Examples:\n     ", "| Yes   |         |")
        pressKey('x')
        checkContent(content)

        configure(content)
        selectAsColumn("Examples:\n    ", "| No    | D2")
        pressKey('x')
        checkContent(content)

        configure(content)
        selectAsColumn("Examples:\n   ", " | 79")
        pressKey('x')
        checkContent(content)

        configure(content)
        selectAsColumn("Examples:\n  ", "| Ready |")
        pressKey('x')
        checkContent(content)

        configure(content)
        selectAsColumn("Examples:\n ", "| 79  |")
        pressKey('x')
        checkContent(content)

        configure(content)
        selectAsColumn("Examples:\n", "| 79  |")
        pressKey('x')
        checkContent(content)



        configure(content)
        setCursor(" Details |")
        backspace()
        checkContent(content)

        configure(content)
        setCursor("| No    |")
        backspace()
        checkContent(content)

        configure(content)
        setCursor("| No    ")
        delete()
        checkContent(content)


        // End of table
        configure(content)
        setCursor("| D2      |")
        backspace()
        checkContent(content)

        // Left of table
        configure(content)
        setCursor("| Details |\n  ")
        backspace()
        delete()
        checkContent(content)

        // Left of first row table
        configure(content)
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
                        Then FInished !
                    """.trimIndent()

        configure(content)
        selectAsColumn("Examples:\n     ", "| Yes   |         |")
        delete()
        // language=feature
        checkContent("""
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | NAF | Ready | Details |
                      |     |       |         |
                      | 79  | No    | D2      |
                    Then FInished !
                """)


        configure(content)
        selectAsColumn("Examples:\n    ", "| No    | D2")
        delete()
        // language=feature
        backspace()
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF | Ready | Details |
                  |     |       |         |
                  |     |       |         |
                Then FInished !
            """)


        configure(content)
        selectAsColumn("Examples:\n   ", " | 79")
        backspace()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF | Ready | Details |
                  |     | Yes   |         |
                  |     | No    | D2      |
                Then FInished !
            """)


        configure(content)
        selectAsColumn("Examples:\n  ", "| Ready |")
        backspace()
        checkContent(content)



        configure(content)
        selectAsColumn("Examples:\n ", "| 79  |")
        delete()
        // language=feature
        checkContent("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF | Ready | Details |
                  |     | Yes   |         |
                  |     | No    | D2      |
                Then FInished !
            """)



        configure(content)
        selectAsColumn("Examples:\n", "| 79  |")
        delete()
        // language=feature
        checkContent("""
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber table is formatted !
                    Examples:
                      | NAF | Ready | Details |
                      |     | Yes   |         |
                      |     | No    | D2      |
                    Then FInished !
                """)



        configure(content)
        select(" Details |", " Details |\n")
        backspace()
        checkContent(content)



        configure(content)
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
                Then FInished !
            """)
        checkCursorAt("| NAF")
    }


    fun testDeletionInCellOk() {

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
                """
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
                    Then FInished !
                """
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
                    Then FInished !
                """
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
                    Then FInished !
                """
        )


        select("Then The Cucumber", "Then FInished")
        delete()
        checkContent(
            """
                Feature: Tzatziki y Cucumber
                  Scenario Outline: Auto formating
                    When I enter any character into <NAF> or <Ready> or <Details>
                    Then The Cucumber !
                """
        )
    }


    fun testDeletionInCellOutsideTableIsOk() {

        // language=feature
        configure(
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
        configure("""
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

}
