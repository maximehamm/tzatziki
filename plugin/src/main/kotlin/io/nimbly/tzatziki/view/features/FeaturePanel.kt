package io.nimbly.tzatziki.view.features

import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode

// See com.intellij.ide.bookmark.ui.BookmarksView
class FeaturePanel(val project: Project) : JPanel(), Disposable {

    val structure = GherkinTreeStructure(this)
    val model = StructureTreeModel(structure, this)
    val tree = DnDAwareTree(AsyncTreeModel(model, this))

    init {
        layout = BorderLayout()

        PsiManager.getInstance(project).addPsiTreeChangeListener(
            PsiChangeListener(this),
            DisposalService.getInstance(project)
        )

        add(JBScrollPane(tree))
        TreeSpeedSearch(tree)

        tree.addMouseListener(MouseListening(tree, project))
    }

    override fun dispose() {
        model.dispose()
    }
}

class PsiChangeListener(val panel: FeaturePanel) : PsiTreeChangeListener {

    override fun beforeChildAddition(event: PsiTreeChangeEvent) = Unit
    override fun beforeChildRemoval(event: PsiTreeChangeEvent) = Unit
    override fun beforeChildReplacement(event: PsiTreeChangeEvent) = Unit
    override fun beforeChildMovement(event: PsiTreeChangeEvent) = Unit
    override fun beforeChildrenChange(event: PsiTreeChangeEvent) = Unit
    override fun beforePropertyChange(event: PsiTreeChangeEvent) = Unit

    override fun childAdded(event: PsiTreeChangeEvent) = refresh(event)
    override fun childMoved(event: PsiTreeChangeEvent) = refresh(event)
    override fun childRemoved(event: PsiTreeChangeEvent) = refresh(event)
    override fun childReplaced(event: PsiTreeChangeEvent) = refresh(event)
    override fun childrenChanged(event: PsiTreeChangeEvent) = refresh(event)
    override fun propertyChanged(event: PsiTreeChangeEvent) = refresh(event)

    private fun refresh(event: PsiTreeChangeEvent) {
        val parent = event.parent
            ?: return
        val elt = panel.structure.getParentElement(parent)
        if (elt != null)
            panel.model.invalidateAsync(elt, true)
    }
}

// See com.intellij.ide.bookmark.ui.tree.BookmarksTreeStructure
class GherkinTreeStructure(val panel: FeaturePanel) : AbstractTreeStructure() {
    private val root = ProjectNode(panel.project)

    override fun commit() = Unit
    override fun hasSomethingToCommit() = false

    override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?)
        = element as NodeDescriptor<*>

    override fun getRootElement(): Any = root

    override fun getParentElement(element: Any): Any? {
        if (element is GherkinFile) {
            return ProjectNode(element.project)
        } else if (element is GherkinFeature) {
            return GherkinFileNode(element.project, element.parent as GherkinFile)
        } else if (element is GherkinStepsHolder) {
            return FeatureNode(element.project, element.parent as GherkinFeature)
        }
        return null
    }

    override fun getChildElements(element: Any): Array<Any> {
        if (element is AbstractTreeNode<*>)
            return element.children.toTypedArray()
        return emptyArray<Any>()
    }
}


class MouseListening(val tree: DnDAwareTree, private val project: Project) : MouseAdapter() {

    override fun mouseClicked(e: MouseEvent) {

        if (e.clickCount == 2) {
            val elt = tree.lastSelectedPathComponent
            if (elt is DefaultMutableTreeNode) {
                val psiElt = (elt.userObject as? AbstractTreeNode<*>)?.value as? PsiElement
                if (psiElt != null) {

                    val fileEditorManager = FileEditorManager.getInstance(project)
                    val virtualFile = psiElt.containingFile.virtualFile
                    fileEditorManager.openFile(virtualFile, true, fileEditorManager.isFileOpen(virtualFile))
                    fileEditorManager.setSelectedEditor(virtualFile, "text-editor")

                    (psiElt as? Navigatable)?.navigate(true)
                }
            }
        }
    }
}

