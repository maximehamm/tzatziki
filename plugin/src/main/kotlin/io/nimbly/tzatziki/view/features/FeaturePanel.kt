package io.nimbly.tzatziki.view.features

import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import io.nimbly.tzatziki.view.features.node.FeatureNodeX
import io.nimbly.tzatziki.view.features.node.GherkinFileNodeX
import io.nimbly.tzatziki.view.features.node.ProjectNodeX
import io.nimbly.tzatziki.view.features.node.ScenarioNodeX
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import java.awt.BorderLayout
import javax.swing.JPanel

// See com.intellij.ide.bookmark.ui.BookmarksView
class FeaturePanel(val project: Project) : JPanel(), Disposable {

    val structure = GherkinTreeStructure(this)
    val model = StructureTreeModel(structure, this)
//    val tree = DnDAwareTree(AsyncTreeModel(model, this))
    val tree = DnDAwareTree(AsyncTreeModel(model, this))

    init {
        layout = BorderLayout()

        //Since Project type objects are not allowed to be used as parent disposable, using a light service instead, which is disposed automatically
        //when implementing the Disposable interface.
        //see: https://plugins.jetbrains.com/docs/intellij/disposers.html#automatically-disposed-objects
        //see: https://plugins.jetbrains.com/docs/intellij/disposers.html#choosing-a-disposable-parent
        PsiManager.getInstance(project).addPsiTreeChangeListener(
            PsiChangeListener(this),
            DisposalService.getInstance(project)
        )


        add(JBScrollPane(tree))
        TreeSpeedSearch(tree)

    }

    override fun dispose() {
        //TODO
    }
}

class PsiChangeListener(val panel: FeaturePanel) : PsiTreeChangeListener {

    override fun beforeChildAddition(event: PsiTreeChangeEvent) {
    }

    override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
    }

    override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
    }

    override fun beforeChildMovement(event: PsiTreeChangeEvent) {
    }

    override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
    }

    override fun beforePropertyChange(event: PsiTreeChangeEvent) {
    }

    override fun childAdded(event: PsiTreeChangeEvent) {
        refresh(event)
    }

    override fun childMoved(event: PsiTreeChangeEvent) {
        refresh(event)
    }

    override fun childRemoved(event: PsiTreeChangeEvent) {
        refresh(event)
    }

    override fun childReplaced(event: PsiTreeChangeEvent) {
        refresh(event)
    }

    override fun childrenChanged(event: PsiTreeChangeEvent) {
        refresh(event)
    }

    override fun propertyChanged(event: PsiTreeChangeEvent) {
        refresh(event)
    }

    private fun refresh(event: PsiTreeChangeEvent) {

//        if (event.parent is GherkinFeature)

//        panel.structure.getParentElement(event.parent)
//        panel.structure.revalidateElement(event.parent)
//        panel.tree.updateUI()

//        panel.model.invalidateAsync()

        val elt = panel.structure.getParentElement(event.parent)
        if (elt != null)
            panel.model.invalidateAsync(elt, true)

    }
}

// See com.intellij.ide.bookmark.ui.tree.BookmarksTreeStructure
class GherkinTreeStructure(val panel: FeaturePanel) : AbstractTreeStructure() {
    private val root = ProjectNodeX(panel.project)

    override fun commit() = Unit
    override fun hasSomethingToCommit() = false

    override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?)
        = element as NodeDescriptor<*>

    override fun getRootElement(): Any = root

    override fun getParentElement(element: Any): Any? {
        if (element is GherkinFile) {
            return ProjectNodeX(element.project)
        } else if (element is GherkinFeature) {
            return GherkinFileNodeX(element.project, element.parent as GherkinFile)
        } else if (element is GherkinStepsHolder) {
            return FeatureNodeX(element.project, element.parent as GherkinFeature)
        }
        return null
    }

    override fun getChildElements(element: Any): Array<Any> {
        if (element is AbstractTreeNode<*>)
            return element.children.toTypedArray()

//        val node = element as? AbstractTreeNode<*>
//        if (panel.isPopup && node !is RootNode && node !is GroupNode) return emptyArray()
//        val children = node?.children?.ifEmpty { null } ?: return emptyArray()
//        val parent = node.parentFolderNode ?: return children.toTypedArray()
//        val provider = CompoundTreeStructureProvider.get(panel.project) ?: return children.toTypedArray()
//        return provider.modify(node, children, parent.settings).toTypedArray()

        return emptyArray<Any>()
    }



    override fun revalidateElement(element: Any): Any {
        return super.revalidateElement(element)
    }
}

internal val Any.asAbstractTreeNode
    get() = this as? AbstractTreeNode<*>
