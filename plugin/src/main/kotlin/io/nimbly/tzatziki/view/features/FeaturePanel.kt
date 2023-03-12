package io.nimbly.tzatziki.view.features

import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import icons.CollaborationToolsIcons
import io.nimbly.tzatziki.services.TzPersistenceStateService
import io.nimbly.tzatziki.util.findAllTags
import io.nimbly.tzatziki.util.getGherkinScope
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.tree.DefaultMutableTreeNode

// See com.intellij.ide.bookmark.ui.BookmarksView
class FeaturePanel(val project: Project) : SimpleToolWindowPanel(true), Disposable {

//    val structure = GherkinTreeStructure(this)
    val structure = GherkinTreeTagStructure(this)

    val model = StructureTreeModel(structure, this)

    val tree = DnDAwareTree(AsyncTreeModel(model, this))
    val treeExpander = DefaultTreeExpander(tree)

    init {
        layout = BorderLayout()

        PsiManager.getInstance(project).addPsiTreeChangeListener(
            PsiChangeListener(this),
            DisposalService.getInstance(project)
        )

        add(JBScrollPane(tree))
        TreeSpeedSearch(tree)

        val toolbarGroup = DefaultActionGroup()
        toolbarGroup.add(CommonActionsManager.getInstance().createExpandAllAction(treeExpander, this))
        toolbarGroup.add(CommonActionsManager.getInstance().createCollapseAllAction(treeExpander, this))
        toolbarGroup.add(GroupByTagAction(this))

        val toolbar = ActionManager.getInstance().createActionToolbar("CucumberPlusFeatureTree", toolbarGroup, false)
        toolbar.targetComponent = tree

        setToolbar(toolbar.component)

        tree.addMouseListener(MouseListening(tree, project))
    }

    override fun dispose() {
        model.dispose()
    }

    fun groupByTag(grouping: Boolean) {

        val state = ServiceManager.getService(project, TzPersistenceStateService::class.java)
        state.groupTag = grouping
    }

    fun refreshTags(tags: List<String>) {
        val tags: Map<String, List<GherkinFile>> = findAllTags(project, project.getGherkinScope())
            .sortedBy { it.name }
            .map { it.name to it.tag.containingFile  }
            .groupBy { it.first }
            .map { it.key to
                    it.value.toMap().values.map { it as GherkinFile }.sortedBy { it.name } }
            .toMap()
        structure.tags = tags
        model.invalidateAsync()
    }

    class GroupByTagAction(val panel: FeaturePanel) : ToggleAction() {
        init {
            this.templatePresentation.text = "Group by tags"
            this.templatePresentation.icon = CollaborationToolsIcons.Review.Branch
        }
        override fun isSelected(e: AnActionEvent): Boolean {
            val state = ServiceManager.getService(panel.project, TzPersistenceStateService::class.java)
            return state.groupTag == true
        }
        override fun setSelected(e: AnActionEvent, state: Boolean) = panel.groupByTag(state)
        override fun getActionUpdateThread() = ActionUpdateThread.BGT
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
        if (elt != null) {
            panel.model.invalidateAsync(elt, true)
        } else if (parent is PsiDirectory){
            panel.model.invalidateAsync()
        }
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


