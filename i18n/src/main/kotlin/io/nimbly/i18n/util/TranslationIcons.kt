/*
 * I18N +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
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
package io.nimbly.i18n.util

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.util.IconUtil
import io.nimbly.i18n.translation.engines.IEngine
import javax.swing.Icon

interface TranslationIcons {

    companion object {

        fun getFlag(locale: String, scaleRatio: Double = 0.8, engine: IEngine? = null): ZIcon {

            var iso = locale
            if (engine!=null) {
                val l = engine.languagesToIso639()[locale]
                iso = l ?: iso
            }

            var icon = FLAGS[iso + scaleRatio]
            if (icon != null) return icon

            try {
                var ico = findFlagIcon("io/nimbly/i18n/icons/languages/svg/$iso.svg")
                if (!ico.exists()) {
                    ico = findFlagIcon("io/nimbly/i18n/icons/languages/svg/${iso.substringBefore("-")}.svg")
                    if (!ico.exists()) {
                        ico = IconLoader.findIcon("io/nimbly/i18n/icons/languages/$iso.png", TranslationIcons::class.java)
                        if (!ico.exists()) {
                            ico = IconLoader.findIcon("io/nimbly/i18n/icons/languages/${iso.substringBefore("-")}.png", TranslationIcons::class.java)
                            if (!ico.exists()) {
                                throw NullPointerException()
                            }
                        }
                    }
                }
                if (ico == null)
                    throw NullPointerException()
                ico = IconUtil.scale(ico, scaleRatio)
                icon = ZIcon(locale, ico, true)
            } catch (ignored: Throwable) {
                val ticon = textToIcon(locale.uppercase(), (scaleRatio * 11f).toFloat(), -1, JBColor.GRAY)
                icon = ZIcon(locale, ticon, false)
            }
            FLAGS[iso + scaleRatio] = icon!!

            return icon
        }

        private val FLAGS: MutableMap<String, ZIcon?> = HashMap()
    }
}

private fun Icon?.exists(): Boolean {
    try {
        if ((this?.iconWidth ?: 0) < 16)
            return false
        else
            return true
    } catch (e: Throwable) {
        return false
    }
}

/**
 * Finds an SVG flag icon, but pre-validates it to avoid passing JSvg-incompatible
 * files through IntelliJ's icon loading (which fires a fatal Logger.error on
 * "use is over-nested: 7"). Returns null when the SVG would exceed JSvg's nesting
 * limit; the caller then falls back to the plain-text icon.
 */
private fun findFlagIcon(path: String): Icon? {
    val stream = TranslationIcons::class.java.classLoader.getResourceAsStream(path)
        ?: return null
    val content = try {
        stream.bufferedReader().use { it.readText() }
    } catch (_: Throwable) {
        return null
    }
    if (svgUseChainTooDeep(content)) return null
    return IconLoader.findIcon(path, TranslationIcons::class.java)
}

/**
 * Returns true if the SVG contains a `<use>` reference chain deeper than 6 levels,
 * which is the threshold that makes JSvg throw "use is over-nested: 7".
 *
 * Builds a map of every element with an `id` to the set of `<use href="#X">` refs
 * found inside its subtree (including via descendant `<g>` containers), then traces
 * the longest chain.
 */
private fun svgUseChainTooDeep(svg: String, maxAllowed: Int = 4): Boolean {
    // Collect: for each element with id, the use-refs that occur within it.
    val tagRe = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*?)(/)?>")
    val idRe = Regex("""\bid\s*=\s*"([^"]+)"""")
    val hrefRe = Regex("""(?:xlink:)?href\s*=\s*"#([^"]+)"""")

    val idToInnerUses = mutableMapOf<String, MutableSet<String>>()
    // Stack of currently open elements — each entry is the set we should add `<use>`s to.
    val openStack = ArrayDeque<MutableSet<String>>()

    for (m in tagRe.findAll(svg)) {
        val isClose = m.groupValues[1] == "/"
        val tag = m.groupValues[2]
        val attrs = m.groupValues[3]
        val isSelfClose = m.groupValues[4] == "/"

        if (isClose) {
            if (openStack.isNotEmpty()) openStack.removeLast()
            continue
        }

        if (tag == "use") {
            val href = hrefRe.find(attrs)?.groupValues?.get(1)
            if (href != null) openStack.forEach { it.add(href) }
            // <use> is a leaf (typically self-closing); nothing to push.
            continue
        }

        if (!isSelfClose) {
            val bucket = mutableSetOf<String>()
            openStack.addLast(bucket)
            val id = idRe.find(attrs)?.groupValues?.get(1)
            if (id != null) idToInnerUses[id] = bucket
        }
    }

    // Memoised depth-first traversal.
    val cache = mutableMapOf<String, Int>()
    fun depth(id: String, visiting: Set<String>): Int {
        if (id in visiting) return 1 // cycle: cap to 1
        cache[id]?.let { return it }
        val refs = idToInnerUses[id]
        val d = if (refs.isNullOrEmpty()) 1
                else 1 + (refs.maxOf { depth(it, visiting + id) })
        cache[id] = d
        return d
    }

    val maxDepth = idToInnerUses.keys.maxOfOrNull { depth(it, emptySet()) } ?: 0
    return maxDepth > maxAllowed
}

class ZIcon(
    val locale: String,
    icon: Icon,
    val flag: Boolean = true
) : Icon by icon
