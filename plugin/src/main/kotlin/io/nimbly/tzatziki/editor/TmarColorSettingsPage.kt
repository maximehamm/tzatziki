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

package io.nimbly.tzatziki.editor

import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import icons.ActionIcons.CUCUMBER_PLUS_16
import io.nimbly.tzatziki.testdiscovery.TEST_KO
import io.nimbly.tzatziki.testdiscovery.TEST_OK
import org.jetbrains.plugins.cucumber.psi.GherkinSyntaxHighlighter
import org.jetbrains.plugins.cucumber.psi.i18n.JsonGherkinKeywordProvider

class TzColorSettingsPage : ColorSettingsPage {

    override fun getDisplayName()
        = "Cucumber+"

    override fun getHighlighter(): SyntaxHighlighter {
        return GherkinSyntaxHighlighter(JsonGherkinKeywordProvider.getKeywordProvider())
    }

    override fun getDemoText() =
        """Feature Cucumber+
             Scenario: Creating an empty order
               Given Romeo who wants to buy a drink
               When <OK>an order is declared for Juliette</OK>
               Then <KO>there is 0 cocktails in the order</KO>""".trimIndent()

    override fun getAttributeDescriptors()
        = listOf(AttributesDescriptor("Test is OK", TEST_OK),
                AttributesDescriptor("Test is KO", TEST_KO)).toTypedArray()

    override fun getAdditionalHighlightingTagToDescriptorMap()
        = mapOf("OK" to TEST_OK,
                "KO" to TEST_KO)

    override fun getColorDescriptors(): Array<ColorDescriptor>
        = ColorDescriptor.EMPTY_ARRAY

    override fun getIcon()
        = CUCUMBER_PLUS_16

}