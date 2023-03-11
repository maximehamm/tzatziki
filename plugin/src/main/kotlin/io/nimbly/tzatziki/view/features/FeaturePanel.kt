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

        //Since Project type objects are not allowed to be used as parent disposable, using a light service instead, which is disposed automatically
        //when implementing the Disposable interface.
        //see: https://plugins.jetbrains.com/docs/intellij/disposers.html#automatically-disposed-objects
        //see: https://plugins.jetbrains.com/docs/intellij/disposers.html#choosing-a-disposable-parent
        PsiManager.getInstance(project).addPsiTreeChangeListener(
            TreeChangeListener(tree, project),
            DisposalService.getInstance(project)
        )
    }
}


class TreeChangeListener(private val tree: GherkinTagTree, private val project: Project) : PsiTreeChangeAdapter() {

    override fun childrenChanged(event: PsiTreeChangeEvent) {
        updateTree(event)
    }

    override fun childAdded(event: PsiTreeChangeEvent) {
        updateTree(event)
    }

    override fun childMoved(event: PsiTreeChangeEvent) {
        updateTree(event)
    }

    override fun childRemoved(event: PsiTreeChangeEvent) {
        updateTree(event)
    }

    override fun childReplaced(event: PsiTreeChangeEvent) {
        val model = tree.model as GherkinTreeModel
        model.findNodes(event.parent)

        updateTree(event)
    }

    override fun propertyChanged(event: PsiTreeChangeEvent) {
        updateTree(event)
    }

    private fun updateTree(event: PsiTreeChangeEvent) {
        val file = event.file

        //file is null when the file has just been deleted
        if (file != null) {
            updateModelAndToolWindow(file)
        }
        else {
//            if (event.child is GherkinFile) {
//                updateModelAndToolWindow(event.child as GherkinFile)
//            } else if (event.child is PsiFile && storyService.isJBehaveStoryFile(event.child as PsiFile)) {
//                updateModelAndToolWindow(storyService.asStoryFile(event.child))
//            }
        }
    }

    private fun updateModelAndToolWindow(file: PsiFile) {

//        (tree.model as GherkinTreeModel)
//        (tree.model as GherkinTreeModel).updateModelForFile(file)
//            val modelRoot: ModelDataRoot = tree.model.getRoot() as ModelDataRoot
//            modelRoot.sort()
//        tree.updateUI()
    }

}

