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

package io.nimbly.tzatziki.util

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.editor.colors.TextAttributesKey
import io.nimbly.tzatziki.editor.TEST_IGNORED
import io.nimbly.tzatziki.editor.TEST_KO
import io.nimbly.tzatziki.editor.TEST_OK
import io.nimbly.tzatziki.testdiscovery.EXAMPLE_REGEX

val SMTestProxy.textAttribut: TextAttributesKey
    get() {
        return when {
            isIgnored -> TEST_IGNORED
            isDefect -> TEST_KO
            else -> TEST_OK
        }
    }

val SMTestProxy.index get()
    = parent.children.indexOf(this)

val SMTestProxy.isExample get()
    = EXAMPLE_REGEX.find(name) != null