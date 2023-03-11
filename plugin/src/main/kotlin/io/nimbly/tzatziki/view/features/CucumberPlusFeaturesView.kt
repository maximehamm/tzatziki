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
package io.nimbly.tzatziki.view.features

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

class CucumberPlusFeaturesView(private val project: Project) : SimpleToolWindowPanel(true, false) {

    init {
        setContent(initPanel())
    }

    private fun initPanel(): JPanel {

        val p = JBPanelWithEmptyText()
        p.layout = BorderLayout(10, 10)
        p.border = JBUI.Borders.empty(10)
        p.withEmptyText("No feature found")

        p.add(FeaturePanel(project))

        return p
    }


}