/*
 * Cucumber for Python
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.cucumber.python.run

import com.intellij.openapi.application.PathManager
import java.io.File

/**
 * Extracts the bundled behave helper scripts (the TeamCity formatter and the
 * `behave_run.py` entry point) from the plugin jar onto disk, so they can be put
 * on PYTHONPATH / launched as a script (the latter lets the Python debugger wrap
 * behave with `--file`).
 */
object BehaveHelper {

    const val FORMATTER_SPEC = "behave_tc_formatter:TeamcityFormatter"

    private const val FORMATTER_RESOURCE = "/io/nimbly/cucumber/python/run/behave_tc_formatter.py"
    private const val RUNNER_RESOURCE = "/io/nimbly/cucumber/python/run/behave_run.py"

    private fun helpersDir(): File =
        File(PathManager.getSystemPath(), "cucumber-python-helpers").apply { mkdirs() }

    private fun extract(resource: String, fileName: String): File {
        val dir = helpersDir()
        val target = File(dir, fileName)
        val bytes = BehaveHelper::class.java.getResourceAsStream(resource)
            ?.use { it.readBytes() }
            ?: error("Bundled helper not found: $resource")
        if (!target.exists() || !target.readBytes().contentEquals(bytes)) {
            target.writeBytes(bytes)
        }
        return target
    }

    /** Directory (to add to PYTHONPATH) that contains `behave_tc_formatter.py`. */
    fun formatterDir(): File {
        extract(FORMATTER_RESOURCE, "behave_tc_formatter.py")
        return helpersDir()
    }

    /** The `behave_run.py` script to launch (also extracts the formatter alongside). */
    fun runnerScript(): File {
        extract(FORMATTER_RESOURCE, "behave_tc_formatter.py")
        return extract(RUNNER_RESOURCE, "behave_run.py")
    }
}
