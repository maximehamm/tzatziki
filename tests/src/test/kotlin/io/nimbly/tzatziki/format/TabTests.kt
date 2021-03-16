package io.nimbly.tzatziki.format

import io.nimbly.tzatziki.AbstractTestCase

class TabTests  : AbstractTestCase() {

    fun testTab() {

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
                Then FInished !""")

        //
        setCursor("| NAF")

        // expected sequence
        val sequence = arrayOf(
            "Ready", "Details",
            "78", "Yes", "",
            "79", "No", "D2",
            "NAF")

        // go forward
        for (i in sequence.indices)
            navigate(TAB, sequence[i])

        // go backward
        for (i in sequence.size - 2 downTo 0)
            navigate(BACK, sequence[i])

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
                Then FInished !"""
        )
    }

}

