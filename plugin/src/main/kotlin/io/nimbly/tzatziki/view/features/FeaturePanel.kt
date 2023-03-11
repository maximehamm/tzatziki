package io.nimbly.tzatziki.view.features

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import javax.swing.JPanel

class FeaturePanel(val project: Project) : JPanel() {
    init {
        layout = BorderLayout()

        val model = GherkinTreeModel(project)
        val tree = GherkinTagTree(model)

        add(JBScrollPane(tree))
        TreeSpeedSearch(tree)
    }
}
