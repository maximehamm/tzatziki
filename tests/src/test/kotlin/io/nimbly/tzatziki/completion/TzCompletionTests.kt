/*
 * CUCUMBER +
 * Copyright (C) 2021  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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

package io.nimbly.tzatziki.completion

import io.nimbly.tzatziki.AbstractCompletionTests

class TzCompletionTests : AbstractCompletionTests() {

    fun testCompletionHeader() {

        // language=feature
        feature(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: Main
                  | NAF   | Ready | Details |
                  | 78.10 | Yes   | Aaa     |
                  | 78.2Z | No    | Bbb     |
                  | 88.4B | Maybe | Ccc     |
                Examples: Others
                  | 
                """
        )

        var proposals = createProposals("Others\n      |")

        var p = proposalExists(proposals, "NAF")
        proposalExists(proposals, "Ready")
        proposalExists(proposals, "Details")
        proposalCount(proposals, 3)

        applyProposal(p, TAB)

        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: Main
                  | NAF   | Ready | Details |
                  | 78.10 | Yes   | Aaa     |
                  | 78.2Z | No    | Bbb     |
                  | 88.4B | Maybe | Ccc     |
                Examples: Others
                  | NAF |  | 
                """
        )
        checkCursorAt("Others\n      | NAF | ")


        // ***********************


        proposals = createProposals()

        proposalExists(proposals, "NAF")
        proposalExists(proposals, "Ready")
        p = proposalExists(proposals, "Details")
        proposalCount(proposals, 3)

        applyProposal(p, TAB)

        // language=feature
        checkContent(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: Main
                  | NAF   | Ready | Details |
                  | 78.10 | Yes   | Aaa     |
                  | 78.2Z | No    | Bbb     |
                  | 88.4B | Maybe | Ccc     |
                Examples: Others
                  | NAF | Details | 
                """
        )
        checkCursorAt("Others\n      | NAF | Details")

    }




    fun testCompletionCell() {

        // language=feature
        feature(
            """
            Feature: Tzatziki y Cucumber
              Scenario Outline: Auto formating
                When I enter any character into <NAF> or <Ready> or <Details>
                Then The Cucumber table is formatted !
                Examples: Main
                  | NAF   | Ready | Details |
                  | 78.10 | Yes   | Aaa     |
                  | 78.2Z | No    | Bbb     |
                  | 88.4B | Maybe | Ccc     |
                Examples: Others
                  | Ready | Details |
                  |       |         |
                """
        )

        val proposals = createProposals("|       | ")

        val p = proposalExists(proposals, "Aaa")
        proposalExists(proposals, "Bbb")
        proposalExists(proposals, "Ccc")
        proposalCount(proposals, 3)

    }


}