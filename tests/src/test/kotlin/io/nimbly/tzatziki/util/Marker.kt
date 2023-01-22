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

package io.nimbly.tzatziki.util

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction

interface IMarker {
    val message: String
    val problemGroup: String?
    val intentions: MutableList<IntentionAction>
}

class Marker(hi: HighlightInfo) : IMarker {

    override val message: String
    override val problemGroup: String?
    override val intentions: MutableList<IntentionAction>

    override fun toString(): String {
        return message
    }

    init {
        message = hi.description
        problemGroup = hi.problemGroup?.problemName
        intentions = ArrayList()
        if (hi.quickFixActionMarkers != null) {
            for (pair in hi.quickFixActionMarkers) {
                intentions.add(pair.getFirst().action)
            }
        }
    }
}