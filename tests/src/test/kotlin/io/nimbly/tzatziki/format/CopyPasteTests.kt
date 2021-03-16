package io.nimbly.tzatziki.format

import io.nimbly.tzatziki.AbstractTestCase

class CopyPasteTests  : AbstractTestCase() {

    fun testCopy() {

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

        selectAsColumn("| NAF |", "| D2      ")
        copy()

        checkClipboard("""
                Ready	Details
                Yes	
                No	D2""")
    }

    fun testCopyPaste() {

        // language=feature
        configure("""
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
        configure("""
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
        configure("""
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
        configure("""
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

        configure(content)
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
        configure(content)


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
}

