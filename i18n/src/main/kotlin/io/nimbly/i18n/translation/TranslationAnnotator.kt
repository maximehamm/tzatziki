package io.nimbly.i18n.translation

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import io.nimbly.i18n.util.*
import java.awt.Color
import javax.swing.SwingUtilities


class TranslationAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {

        if (!RefactoringSetup().useRefactoring)
            return

        val document = element.getDocument()
            ?: return

        val editor = EditorFactory.getInstance().getEditors(document, element.project).firstOrNull()
            ?: return

        val hint = editor.inlayModel.getBlockElementsInRange(element.startOffset, element.endOffset)
            .map { it.renderer }
            .filterIsInstance<EditorHint>()
            .firstOrNull { it.element?.element == element }
            ?: return

        // val inlay = editor.inlayModel.getBlockElementsInRange(element.startOffset, element.endOffset)
        //     .filter { (it.renderer as? EditorHint)?.element == element }
        //     .firstOrNull()
        //     as Inlay<EditorHint>?
        //     ?: return

        SwingUtilities.invokeLater {
            val uses = findUsages(element, editor)
            if (uses.isNotEmpty()) {

                // val icon = textToIcon("x${uses.size}", 9.0f, -1, Color.GRAY)
                val icon = textToIcon("${uses.size} slot${if (uses.size>1) "s" else "" }", 9.0f, -1, Color.GRAY)

                val info = RelatedItemLineMarkerInfo<PsiElement>(
                    element,
                    element.textRange,
                    icon,
                    { "test 1" },
                    { event, elt -> (elt as? Navigatable)?.navigate(true) },
                    GutterIconRenderer.Alignment.RIGHT,
                    { uses.map { GotoRelatedItem(it.first) } }
                )

                holder
                    .newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element.textRange)
                    .gutterIconRenderer(LineMarkerInfo.LineMarkerGutterIconRenderer(info))
                    .tooltip(hint.translation)
                    .create()
            }
        }
    }
}