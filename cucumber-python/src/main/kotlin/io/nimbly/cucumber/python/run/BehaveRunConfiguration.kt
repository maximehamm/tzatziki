/*
 * Cucumber for Python
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.cucumber.python.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.PythonCommandLineState
import com.jetbrains.python.run.PythonExecution
import com.jetbrains.python.run.PythonScriptExecution
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import org.jdom.Element
import java.io.File
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Runs (and debugs) a behave feature file using the configured Python SDK.
 *
 * Extends [AbstractPythonRunConfiguration] (rather than a plain
 * LocatableConfigurationBase) on purpose: the Python plugin's `PyDebugRunner`
 * only offers the **Debug** executor for Python run configurations, and
 * [PythonCommandLineState] gives us SDK resolution + pydevd injection for free.
 * behave is launched as a script (`behave_run.py`) so the debugger can wrap it
 * with `--file`; our TeamCity formatter feeds the SMTRunner test tree.
 */
class BehaveRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
) : AbstractPythonRunConfiguration<BehaveRunConfiguration>(project, factory) {

    var featurePath: String = ""
    /** 1-based line of the targeted scenario, or -1 for the whole feature.
     *  Used only for run-config identity (behave ignores the `feature:line` form). */
    var lineNumber: Int = -1
    /** Scenario name to run via behave `-n`; blank = run the whole feature. */
    var scenarioName: String = ""

    override fun createConfigurationEditor(): SettingsEditor<BehaveRunConfiguration> =
        object : SettingsEditor<BehaveRunConfiguration>() {
            override fun resetEditorFrom(s: BehaveRunConfiguration) {}
            override fun applyEditorTo(s: BehaveRunConfiguration) {}
            override fun createEditor(): JComponent = JPanel()
        }

    override fun checkConfiguration() {
        super.checkConfiguration()
        if (featurePath.isBlank()) throw RuntimeConfigurationError("No feature file specified")
        if (!File(featurePath).exists()) throw RuntimeConfigurationError("Feature file not found: $featurePath")
    }

    override fun getState(executor: Executor, env: ExecutionEnvironment): RunProfileState =
        BehaveCommandLineState(this, env)

    /** behave must run from the directory that holds `features/`; fall back to it
     *  when no working directory was persisted. The SDK is resolved by the base
     *  class from the bound module (set by the producer) — no PythonSdkUtil. */
    override fun getWorkingDirectory(): String? {
        val wd = super.getWorkingDirectory()
        if (!wd.isNullOrBlank()) return wd
        return featureRootDir()
    }

    private fun featureRootDir(): String? {
        if (featurePath.isBlank()) return null
        var dir: File? = File(featurePath).parentFile
        while (dir != null) {
            if (dir.name == "features") return dir.parentFile?.path ?: dir.path
            dir = dir.parentFile
        }
        return File(featurePath).parentFile?.path
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute("featurePath", featurePath)
        element.setAttribute("lineNumber", lineNumber.toString())
        element.setAttribute("scenarioName", scenarioName)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        featurePath = element.getAttributeValue("featurePath") ?: ""
        lineNumber = element.getAttributeValue("lineNumber")?.toIntOrNull() ?: -1
        scenarioName = element.getAttributeValue("scenarioName") ?: ""
    }
}

/**
 * Builds the behave command line on top of [PythonCommandLineState] (which sets
 * the interpreter, PYTHONPATH and — under the Debug executor — injects pydevd),
 * and renders results in the SMTRunner test tree via our TeamCity formatter.
 */
private class BehaveCommandLineState(
    private val config: BehaveRunConfiguration,
    env: ExecutionEnvironment,
) : PythonCommandLineState(config, env) {

    // Modern (Targets API) execution path — the legacy buildCommandLineParameters
    // path is no longer used by PythonCommandLineState in 2025.3+.
    override fun buildPythonExecution(helpersAwareRequest: HelpersAwareTargetEnvironmentRequest): PythonExecution {
        val targetRequest = helpersAwareRequest.targetEnvironmentRequest

        // Launch behave as a SCRIPT (behave_run.py) so the debugger wraps it with
        // `--file`. Python auto-adds the script's directory to sys.path, so the
        // co-located behave_tc_formatter.py is importable WITHOUT setting PYTHONPATH.
        val runner = BehaveHelper.runnerScript()

        val exec = PythonScriptExecution()
        exec.pythonScriptPath = getTargetPath(targetRequest, Path.of(runner.absolutePath))
        // behave ignores the `feature.feature:LINE` form — select a single scenario
        // by name via `-n` instead.
        exec.addParameter(config.featurePath)
        if (config.scenarioName.isNotBlank()) {
            exec.addParameters("-n", config.scenarioName)
        }
        exec.addParameters("--no-summary", "--no-capture", "-f", BehaveHelper.FORMATTER_SPEC)
        exec.charset = EncodingProjectManager.getInstance(config.project).defaultCharset
        return exec
    }

    override fun createAndAttachConsole(
        project: Project,
        processHandler: ProcessHandler,
        executor: Executor,
    ): ConsoleView {
        // Provide a FILE test locator so each test's `file://<abs>:<line>` locationHint
        // resolves to its Gherkin element — that's what powers Cucumber+'s gutter
        // test-progression decoration on the .feature file.
        val props = object : SMTRunnerConsoleProperties(config, "behave", executor) {
            override fun getTestLocator() = com.intellij.execution.testframework.sm.FileUrlProvider.INSTANCE
        }
        val console = SMTestRunnerConnectionUtil.createConsole("behave", props)
        console.attachToProcess(processHandler)
        return console
    }
}
