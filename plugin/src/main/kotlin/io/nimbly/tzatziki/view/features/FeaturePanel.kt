package io.nimbly.tzatziki.view.features

import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.services.Tag
import io.nimbly.tzatziki.services.TagComparator
import io.nimbly.tzatziki.services.TzPersistenceStateService
import io.nimbly.tzatziki.services.TzTagService
import io.nimbly.tzatziki.view.features.actions.FilterTagAction
import io.nimbly.tzatziki.view.features.actions.GroupByTagAction
import io.nimbly.tzatziki.view.features.actions.RunTestAction
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

// See com.intellij.ide.bookmark.ui.BookmarksView
class FeaturePanel(val project: Project) : SimpleToolWindowPanel(true), Disposable {

    val structure: GherkinTreeTagStructure
    val model: StructureTreeModel<GherkinTreeTagStructure>
    val tree: DnDAwareTree
    init {

        val state = ServiceManager.getService(project, TzPersistenceStateService::class.java)
        val grouping = state.groupTag == true

        structure = GherkinTreeTagStructure(this).apply { groupByTags = grouping }
        model = StructureTreeModel(structure, this)
        tree = DnDAwareTree(AsyncTreeModel(model, this))

        layout = BorderLayout()

        add(JBScrollPane(tree))
        TreeSpeedSearch(tree)
        val treeExpander = DefaultTreeExpander(tree)

        val toolbarGroup = DefaultActionGroup()
        toolbarGroup.add(CommonActionsManager.getInstance().createExpandAllAction(treeExpander, this))
        toolbarGroup.add(CommonActionsManager.getInstance().createCollapseAllAction(treeExpander, this))
        toolbarGroup.add(GroupByTagAction(this))
        toolbarGroup.add(FilterTagAction(this))

        toolbarGroup.add(RunTestAction(this))

        val toolbar = ActionManager.getInstance().createActionToolbar("CucumberPlusFeatureTree", toolbarGroup, false)
        toolbar.targetComponent = tree
        setToolbar(toolbar.component)
        tree.addMouseListener(MouseListening(tree, project))

        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

        initTagService()
        forceRefresh()
    }

    private fun initTagService() {
        val state = ServiceManager.getService(project, TzPersistenceStateService::class.java)
        val tagService = project.getService(TzTagService::class.java)
        tagService.updateTagsFilter(state.tagExpression())
    }

    private fun forceRefresh() {
        DumbService.getInstance(project).smartInvokeLater {
            PsiDocumentManager.getInstance(project).performWhenAllCommitted {

                // First tag list initialization
                val filterActivated = ServiceManager.getService(project, TzPersistenceStateService::class.java).filterByTags == true
                val tagService = project.getService(TzTagService::class.java)
                refreshTags(
                    tagService.getTags(),
                    if (filterActivated) tagService.getTagsFilter() else null
                )
            }
        }
    }

    override fun dispose() {
        //NA
    }

    fun filterByTag(filter: Boolean) {
        val state = ServiceManager.getService(project, TzPersistenceStateService::class.java)
        state.filterByTags = filter

        if (filter)
            this.structure.filterByTags = state.tagExpression()
        else
            this.structure.filterByTags = null

        forceRefresh()
    }

    fun groupByTag(grouping: Boolean) {

        val state = ServiceManager.getService(project, TzPersistenceStateService::class.java)
        state.groupTag = grouping

        this.structure.groupByTags = grouping

        forceRefresh()
    }

    fun refreshTags(tags: SortedMap<String, Tag>) {
        if (structure.groupByTags || structure.filterByTags != null) {
            val stags = tags
                .map { it.key to it.value.gFiles.toList() }
                .toMap()
                .toSortedMap(TagComparator)
            structure.tags = stags
        }
        invalidateAsync()
    }

    fun refreshTags(tagsFilter: Expression?) {

        val filterActivated = ServiceManager.getService(project, TzPersistenceStateService::class.java).filterByTags == true
        if (filterActivated) {
            structure.filterByTags = tagsFilter
            invalidateAsync()
        }
    }

    @Deprecated("Intellij 2022.2.4 compatibilty")
    private fun invalidateAsync() {

        // New method
        // model.invalidateAsync()

        // Old method compatibity
        val future: CompletableFuture<Any> = CompletableFuture<Any>()
        model.invoker.compute<Any> {
            val result = model.invalidate()
            future.complete(result)

            if (!future.isDone) future.completeExceptionally(Exception("Canceled"))
            null
        }.onError { ex: Throwable? -> future.completeExceptionally(ex) }
    }

    private fun refreshTags(tags: SortedMap<String, Tag>, tagsFilter: Expression?) {
        structure.filterByTags = tagsFilter
        refreshTags(tags)
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

