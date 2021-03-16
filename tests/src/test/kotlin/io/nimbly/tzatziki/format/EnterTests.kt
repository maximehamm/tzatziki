package io.nimbly.tzatziki.format

import io.nimbly.tzatziki.AbstractTestCase

class EnterTests  : AbstractTestCase() {

    fun testEnter1() {

        // language=feature
        configure("""
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
                Then FInished !
            """
        )
    }

    fun testEnter2() {

        // language=feature
        configure("""
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples:
                  | NAF | Ready | Details |
                  | 78  | Yes   | D1      |
                  | 79  | No    | D2      |
                Then FInished !
            """)

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
                Then FInished !
            """
        )
    }

    fun testEnterEOF() {

        // language=feature
        configure("""
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
        navigate(ENTER)
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
            """
        )

        // Navigate
        navigate(ENTER)
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
            
            """
        )
    }

}

