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

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionProgressIndicator
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.ui.UIUtil

abstract class AbstractCompletionTests : AbstractTestCase() {

    protected fun applyProposal(p: ICompletionProposal?, completionChar: Char) {
        CommandProcessor.getInstance().executeCommand(project, {
            val lookup = LookupManager.getActiveLookup(myFixture.editor) as LookupImpl
            lookup.finishLookup(completionChar, p!!.lookupElement)
            PsiDocumentManager.getInstance(module.project).commitAllDocuments()
        }, null, null)
    }

    protected fun proposalExists(proposals: List<ICompletionProposal>, lookupString: String): ICompletionProposal? {
        return proposalExists(proposals, lookupString, null)
    }

    protected fun proposalExists(
        proposals: List<ICompletionProposal>,
        display: String,
        lookup: String?
    ): ICompletionProposal? {

        val tofound = 1 + if (lookup != null) 1 else 0
        for (proposal in proposals) {
            var f = 0
            val ds = proposal.displayString ?: ""
            if (ds.startsWith("$display ") || ds == display) {
                f++
            }
            if (lookup != null) {
                val l: String = proposal.lookup
                if (l.startsWith("$lookup ") || l == lookup) {
                    f++
                }
            }
            if (tofound == f) return proposal
        }
        val sb = StringBuffer()
        for (proposal in proposals) {
            sb.append("\n$proposal")
            sb.append(" (" + proposal.displayString + ")")
        }
        fail(
            """Expected to find proposal '$display${if (lookup != null) " ($lookup)" else ""}' within ${proposals.size} proposals but not found.
                All Proposals:$sb"""
        )

        return null
    }

    protected fun proposalCount(proposals: List<ICompletionProposal>, count: Int) {
        var length = 0
        for (proposal in proposals) {
            if (proposal.displayString?.startsWith("classpath:") == true) continue
            length++
        }
        if (length != count) {
            val sb = StringBuffer()
            for (proposal in proposals) {
                sb.append("\n$proposal")
            }
            fail("expecting $count completion choice, found $length.\nAll Proposals:$sb")
        }
    }

    protected fun createProposals(lookFor: String? = null): List<ICompletionProposal> {
        myFixture.disableInspections()
        return createProposals(configuredFile!!, lookFor)
    }

    private fun createProposals(file: PsiFile, lookFor: String? = null): List<ICompletionProposal> {

        // Search completion offset
        if (lookFor != null)
            moveTo(lookFor)

        // Build completion
        complete()
        val lookupElts = myFixture.lookupElements

        // Build report
        val proposals = mutableListOf<ICompletionProposal>()
        if (lookupElts != null) {
            for (lke in lookupElts) {
                var l = lke
                if (l is PrioritizedLookupElement<*>) {
                    l = l.delegate
                }
                proposals.add(CompletionProposal(l))
            }
        }
        return proposals
    }

    /**
     * complete
     */
    private fun complete(): Array<LookupElement?>? {
        //assertInitialized();
        //myEmptyLookup = false;
        val lookups: MutableList<LookupElement> = java.util.ArrayList()
        return UIUtil.invokeAndWaitIfNeeded<Array<LookupElement?>> {
            CommandProcessor.getInstance().executeCommand(project, {
                val handler: CodeCompletionHandlerBase = object :
                    CodeCompletionHandlerBase(CompletionType.BASIC) {
                    override fun completionFinished(
                        indicator: CompletionProgressIndicator,
                        hasModifiers: Boolean
                    ) {
                        lookups.addAll(indicator.lookup.items)
                    }
                }
                val editor = myFixture.editor
                handler.invokeCompletion(project, editor, 1)
                //PsiDocumentManager.getInstance(getProject()).commitAllDocuments(); // to compare with file text
            }, null, null)
            // return myFixture.getLookupElements();
            lookups.toTypedArray()
        }
    }

}

interface ICompletionProposal {

    val lookup: String

    val lookupElement: LookupElement
    val displayString: String?
}

class CompletionProposal(override val lookupElement: LookupElement) : ICompletionProposal {

    override val lookup: String
        get() {
            return lookupElement.lookupString
        }

    override val displayString: String?
        get() {
            val lep = LookupElementPresentation()
            lookupElement.renderElement(lep)
            return lep.itemText
        }

    override fun toString(): String {
        return lookup
    }
}