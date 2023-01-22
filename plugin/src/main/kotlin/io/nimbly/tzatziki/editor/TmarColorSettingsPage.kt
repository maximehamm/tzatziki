/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
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

package io.nimbly.tzatziki.editor

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import icons.ActionIcons.CUCUMBER_PLUS_16
import io.nimbly.tzatziki.util.TZATZIKI_NAME
import org.jetbrains.plugins.cucumber.psi.GherkinSyntaxHighlighter
import org.jetbrains.plugins.cucumber.psi.i18n.JsonGherkinKeywordProvider

val TEST_KO = TextAttributesKey.createTextAttributesKey("CCP_TEST_KO", DefaultLanguageHighlighterColors.STRING)
val TEST_OK = TextAttributesKey.createTextAttributesKey("CCP_TEST_OK", DefaultLanguageHighlighterColors.STRING)
val TEST_IGNORED = TextAttributesKey.createTextAttributesKey("CCP_TEST_IGNORED", DefaultLanguageHighlighterColors.STRING)

// Markdown
val BOLD = TextAttributesKey.createTextAttributesKey("CCP_MD_BOLD", DefaultLanguageHighlighterColors.STRING)
val ITALIC = TextAttributesKey.createTextAttributesKey("CCP_MD_ITALIC", DefaultLanguageHighlighterColors.STRING)

// Deprecation
val DEPRECATED = TextAttributesKey.createTextAttributesKey("CCP_DEPRECATED", DefaultLanguageHighlighterColors.STRING)

class TzColorSettingsPage : ColorSettingsPage {

    override fun getDisplayName()
        = TZATZIKI_NAME

    override fun getHighlighter(): SyntaxHighlighter {
        return GherkinSyntaxHighlighter(JsonGherkinKeywordProvider.getKeywordProvider())
    }

    override fun getDemoText() ="""
        Feature $TZATZIKI_NAME
            Scenario: Creating an empty order
               Given Romeo who wants to buy a drink
               When <OK>an order is declared for Juliette</OK>
               Then <KO>there is 0 cocktails in the order</KO>
               But <IG>but there is nothing left</IG>
            
            Scenario Outline: Sending a message with an order
               When an order is declared for <to>
               Then a message saying <message> is added
               And the ticket must say <expected>
               And <DEP>this step is deprecated</DEP>
               Examples:
                 | to       | message     | expected                            |
                 | <OK>Juliette</OK> | <KO>Wanna chat?</KO> | <IG>From Romeo to Juliette: Wanna chat?</IG> |
                 | <OK>Juliette</OK> | <KO>Wanna chat?</KO> | <IG>From Romeo to Jerry: Hei!</IG>           |
                 | <OK>Jerry</OK>    | <OK>Hei!</OK>        | <KO>From Romeo to Jerry: Hei!</KO>           |
               """.trimIndent()

    override fun getAttributeDescriptors()
        = listOf(AttributesDescriptor("Test passed", TEST_OK),
                AttributesDescriptor("Test deffect", TEST_KO),
                AttributesDescriptor("Test ignored", TEST_IGNORED),
                AttributesDescriptor("Step is deprecated", DEPRECATED)).toTypedArray()

    override fun getAdditionalHighlightingTagToDescriptorMap()
        = mapOf("OK" to TEST_OK,
                "KO" to TEST_KO,
                "IG" to TEST_IGNORED,
                "DEP" to DEPRECATED)

    override fun getColorDescriptors(): Array<ColorDescriptor>
        = ColorDescriptor.EMPTY_ARRAY

    override fun getIcon()
        = CUCUMBER_PLUS_16

}