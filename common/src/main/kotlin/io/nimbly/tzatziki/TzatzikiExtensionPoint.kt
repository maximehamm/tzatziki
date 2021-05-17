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

package io.nimbly.tzatziki

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement

private const val nameSpace = "io.nimbly.tzatziki"

interface TzatzikiExtensionPoint {
    fun isDeprecated(element: PsiElement): Boolean
}

object MAIN {
    operator fun invoke(): ExtensionPointName<TzatzikiExtensionPoint> =
        ExtensionPointName.create("$nameSpace.io.nimbly.tzatziki.main")
}
