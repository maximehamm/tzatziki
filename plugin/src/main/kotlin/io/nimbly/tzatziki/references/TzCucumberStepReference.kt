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
package io.nimbly.tzatziki.references

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.cucumber.CucumberJvmExtensionPoint
import org.jetbrains.plugins.cucumber.psi.impl.GherkinStepImpl
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.cucumber.steps.CucumberStepHelper
import java.util.*
import java.util.stream.Collectors

val LAST_VALID = Key<Array<ResolveResult>>("LAST_VALID")

// Cf. https://github.com/JetBrains/intellij-plugins/blob/master/cucumber/src/org/jetbrains/plugins/cucumber/steps/reference/CucumberStepReference.java
class TzCucumberStepReference(private val myStep: PsiElement, private val myRange: TextRange) : PsiPolyVariantReference {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {

        val resolved = ResolveCache.getInstance(element.project)
            .resolveWithCaching(this, RESOLVER, false, incompleteCode)

        if (resolved.isEmpty()) {
            val lastValid = element.getCopyableUserData(LAST_VALID)
            if (lastValid !=null)
                return lastValid
        }

        element.putCopyableUserData(LAST_VALID, resolved)
        return resolved
    }

    override fun getElement(): PsiElement {
        return myStep
    }

    override fun getRangeInElement(): TextRange {
        return myRange
    }

    override fun resolve(): PsiElement? {
        val result = multiResolve(true)
        return if (result.size == 1) result[0].element else null
    }

    override fun getCanonicalText(): String {
        return myStep.text
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        return myStep
    }

    override fun bindToElement(element: PsiElement): PsiElement {
        return myStep
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        val resolvedResults = multiResolve(false)
        for (rr in resolvedResults) {
            if (getElement().manager.areElementsEquivalent(rr.element, element)) {
                return true
            }
        }
        return false
    }

    override fun isSoft(): Boolean {
        return false
    }

    fun resolveToDefinition(): AbstractStepDefinition? {
        val definitions = this.resolveToDefinitions()
        return if (definitions.isEmpty()) null
            else definitions.iterator().next()
    }

    private fun resolveToDefinitions(): Collection<AbstractStepDefinition?> {
        return CucumberStepHelper.findStepDefinitions(
            myStep.containingFile, (myStep as GherkinStepImpl))
    }
    private fun multiResolveInner(): Array<ResolveResult> {

        val module = ModuleUtilCore.findModuleForPsiElement(myStep)
            ?: return ResolveResult.EMPTY_ARRAY

        val frameworks = CucumberJvmExtensionPoint.EP_NAME.extensionList
        val stepVariants: Collection<String?> =
            frameworks.stream().map { e: CucumberJvmExtensionPoint -> e.getStepName(myStep) }
                .filter { obj: String? -> Objects.nonNull(obj) }.collect(Collectors.toSet())
        if (stepVariants.isEmpty())
            return ResolveResult.EMPTY_ARRAY

        val featureFile = myStep.containingFile
        val stepDefinitions = CachedValuesManager.getCachedValue(featureFile) {
            val allStepDefinition: MutableList<AbstractStepDefinition> = ArrayList()
            for (e in frameworks) {
                allStepDefinition.addAll(e.loadStepsFor(featureFile, module))
            }
            CachedValueProvider.Result.create<List<AbstractStepDefinition>>(
                allStepDefinition,
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }

        val resolved = mutableSetOf<PsiElement>()
        for (stepDefinition in stepDefinitions) {
            if (stepDefinition.supportsStep(myStep)) {
                for (stepVariant in stepVariants) {
                    val element = stepDefinition.element
                    if (stepDefinition.matches(stepVariant!!) && element != null && !resolved.contains(element)) {
                        resolved.add(element)
                        break
                    }
                }
            }
        }
        return resolved
            .map { PsiElementResolveResult(it) }
            .toTypedArray()
    }

    private class MyResolver : ResolveCache.PolyVariantResolver<TzCucumberStepReference> {
        override fun resolve(ref: TzCucumberStepReference, incompleteCode: Boolean): Array<ResolveResult> {
            return ref.multiResolveInner()
        }
    }

    companion object {
        private val RESOLVER = MyResolver()
    }
}