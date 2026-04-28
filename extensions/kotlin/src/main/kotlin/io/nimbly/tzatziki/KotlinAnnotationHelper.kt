package io.nimbly.tzatziki

import org.jetbrains.kotlin.psi.KtAnnotationEntry

/**
 * K2-friendly check that an annotation entry refers to an io.cucumber.java.* annotation.
 *
 * The previous implementation called `resolveToDescriptorIfAny()`, which is K1-only and
 * forces `plugin-withKotlin.xml` to be skipped under the K2 (FIR) mode of the Kotlin plugin.
 * This implementation only relies on PSI: either the type reference is fully qualified
 * (`@io.cucumber.java.en.Given(...)`), or the file imports a matching FQ name.
 */
fun KtAnnotationEntry.isCucumberJavaAnnotation(): Boolean {
    val typeText = typeReference?.text ?: return false
    if (typeText.startsWith("io.cucumber.java")) return true

    val shortName = shortName?.asString() ?: return false
    return containingKtFile.importDirectives.any { import ->
        val fqn = import.importedFqName?.asString() ?: return@any false
        fqn.startsWith("io.cucumber.java") &&
            (fqn.endsWith(".$shortName") || fqn.endsWith(".*"))
    }
}
