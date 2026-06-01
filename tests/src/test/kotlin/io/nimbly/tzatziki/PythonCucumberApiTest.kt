package io.nimbly.tzatziki

import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * Guards the version-fragile IntelliJ APIs that the **Cucumber for Python** plugin
 * (`io.nimbly.cucumber.python`) and the Cucumber+ Python integration compile against.
 *
 * A failure here is the EARLY-WARNING signal for an IntelliJ migration: e.g. the
 * cucumber `CucumberJvmExtensionPoint` changed its method *arities* in 2026.x
 * (`isStepLikeFile`/`isWritableStepLikeFile` 2→1 args, `loadStepsFor` 2→1 args),
 * which silently breaks our `CucumberPythonExtension` with `AbstractMethodError`.
 *
 * We check existence by **method name + parameter count** (not exact types) so the
 * test is both robust to write and sensitive to the kind of signature changes that
 * actually bite us across IDE versions.
 *
 * Same approach as [ReflectionApiTest] (Cucumber+ side).
 */
class PythonCucumberApiTest {

    private fun classOrNull(fqn: String): Class<*>? =
        runCatching { Class.forName(fqn) }.getOrNull()

    private fun hasMethod(fqn: String, name: String, paramCount: Int): Boolean {
        val c = classOrNull(fqn) ?: return false
        return (c.methods.asSequence() + c.declaredMethods.asSequence())
            .any { it.name == name && it.parameterCount == paramCount }
    }

    private fun hasConstructor(fqn: String, paramCount: Int): Boolean {
        val c = classOrNull(fqn) ?: return false
        return (c.constructors.asSequence() + c.declaredConstructors.asSequence())
            .any { it.parameterCount == paramCount }
    }

    /** Mandatory: the class must exist (plugin present on the classpath). */
    private fun requireClass(fqn: String) {
        if (classOrNull(fqn) == null)
            fail("Class not found: $fqn — IntelliJ API removed/moved, or Python plugin missing on classpath.")
    }

    private fun requireMethod(fqn: String, name: String, paramCount: Int) {
        requireClass(fqn)
        assertNotNull(
            "$fqn#$name with $paramCount parameter(s) not found — IntelliJ API changed; migrate the plugin.",
            if (hasMethod(fqn, name, paramCount)) Any() else null,
        )
    }

    // -------------------------------------------------------------------------
    // Cucumber framework extension point (the 2026.x break happened HERE)
    // -------------------------------------------------------------------------
    @Test
    fun `CucumberJvmExtensionPoint keeps its 2025_3 method arities`() {
        val ep = "org.jetbrains.plugins.cucumber.CucumberJvmExtensionPoint"
        requireMethod(ep, "isStepLikeFile", 2)          // (PsiElement, PsiElement)
        requireMethod(ep, "isWritableStepLikeFile", 2)  // (PsiElement, PsiElement)
        requireMethod(ep, "loadStepsFor", 2)            // (PsiFile, Module)
        requireMethod(ep, "getStepDefinitionContainers", 1) // (GherkinFile)
        requireMethod(ep, "getStepFileType", 0)
        requireMethod(ep, "getStepDefinitionCreator", 0)
    }

    @Test
    fun `AbstractStepDefinition extension contract`() {
        val sd = "org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition"
        requireMethod(sd, "getVariableNames", 0)
        requireMethod(sd, "getCucumberRegexFromElement", 1) // protected
    }

    @Test
    fun `AbstractStepDefinitionCreator contract`() {
        val c = "org.jetbrains.plugins.cucumber.AbstractStepDefinitionCreator"
        requireMethod(c, "createStepDefinitionContainer", 2)
        requireMethod(c, "createStepDefinition", 3)
    }

    // -------------------------------------------------------------------------
    // Python run / debug (experimental API — our Run/Debug config relies on it)
    // -------------------------------------------------------------------------
    @Test
    fun `AbstractPythonRunConfiguration usable as a base`() {
        val arc = "com.jetbrains.python.run.AbstractPythonRunConfiguration"
        requireClass(arc)
        assertNotNull(
            "$arc(Project, ConfigurationFactory) constructor not found — migrate BehaveRunConfiguration.",
            if (hasConstructor(arc, 2)) Any() else null,
        )
        requireMethod(arc, "createConfigurationEditor", 0)
        requireMethod(arc, "getSdk", 0)
        requireMethod(arc, "setUseModuleSdk", 1)
    }

    @Test
    fun `PythonCommandLineState Targets-API hooks`() {
        val pcls = "com.jetbrains.python.run.PythonCommandLineState"
        requireMethod(pcls, "buildPythonExecution", 1)      // (HelpersAwareTargetEnvironmentRequest)
        requireMethod(pcls, "createAndAttachConsole", 3)    // (Project, ProcessHandler, Executor)
        requireMethod(pcls, "getTargetPath", 2)             // (TargetEnvironmentRequest, Path)
    }

    @Test
    fun `PythonScriptExecution builder`() {
        requireMethod("com.jetbrains.python.run.PythonScriptExecution", "setPythonScriptPath", 1)
        requireClass("com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest")
    }

    // -------------------------------------------------------------------------
    // Python PSI (step-def discovery + the usages marker)
    // -------------------------------------------------------------------------
    @Test
    fun `Python PSI accessors`() {
        requireMethod("com.jetbrains.python.psi.PyFunction", "getDecoratorList", 0)
        requireMethod("com.jetbrains.python.psi.PyFunction", "getStatementList", 0)
        requireMethod("com.jetbrains.python.psi.PyFile", "getTopLevelFunctions", 0)
    }
}
