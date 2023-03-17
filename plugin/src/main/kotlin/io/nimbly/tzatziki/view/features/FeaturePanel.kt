package io.nimbly.tzatziki.view.features

import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.services.Tag
import io.nimbly.tzatziki.services.TagComparator
import io.nimbly.tzatziki.services.tagService
import io.nimbly.tzatziki.util.file
import io.nimbly.tzatziki.util.parentOfTypeIs
import io.nimbly.tzatziki.view.features.actions.FilterTagAction
import io.nimbly.tzatziki.view.features.actions.GroupByModuleAction
import io.nimbly.tzatziki.view.features.actions.GroupByTagAction
import io.nimbly.tzatziki.view.features.actions.LocateAction
import io.nimbly.tzatziki.view.features.actions.RunTestAction
import io.nimbly.tzatziki.view.features.nodes.GherkinFeatureNode
import io.nimbly.tzatziki.view.features.nodes.GherkinFileNode
import io.nimbly.tzatziki.view.features.nodes.GherkinScenarioNode
import io.nimbly.tzatziki.view.features.nodes.GherkinTagNode
import io.nimbly.tzatziki.view.features.nodes.ModuleNode
import io.nimbly.tzatziki.view.features.nodes.parent
import io.nimbly.tzatziki.view.features.structure.GherkinTreeTagStructure
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.getTreePath
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.tree.TreeVisitor.Action
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

// See com.intellij.ide.bookmark.ui.BookmarksView
class FeaturePanel(val project: Project) : SimpleToolWindowPanel(true), Disposable {

    val structure: GherkinTreeTagStructure
    val model: StructureTreeModel<GherkinTreeTagStructure>
    val tree: DnDAwareTree
    val treeSearcher: TreeSpeedSearch
    init {

        val tagService = project.tagService()
        structure = GherkinTreeTagStructure(this).apply {
            this.groupTag = tagService.groupTag == true
        }
        model = StructureTreeModel(structure, this)
        tree = DnDAwareTree(AsyncTreeModel(model, this))

        layout = BorderLayout()

        add(JBScrollPane(tree))
        treeSearcher = TreeSpeedSearch(tree)
        val treeExpander = DefaultTreeExpander(tree)

        val group = DefaultActionGroup().also {
            it.add(CommonActionsManager.getInstance().createExpandAllAction(treeExpander, this))
            it.add(CommonActionsManager.getInstance().createCollapseAllAction(treeExpander, this))
            it.add(LocateAction(this))
            it.add(GroupByModuleAction(this))
            it.add(GroupByTagAction(this))
            it.add(FilterTagAction(this))
            it.add(RunTestAction(this))
        }

        val toolbar = ActionManager.getInstance().createActionToolbar("CucumberPlusFeatureTree", group, false)
        toolbar.targetComponent = tree
        setToolbar(toolbar.component)
        tree.addMouseListener(MouseListening(tree, project))

        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

        forceRefresh()
    }

    private fun forceRefresh() {
        DumbService.getInstance(project).smartInvokeLater {
            PsiDocumentManager.getInstance(project).performWhenAllCommitted {

                // First tag list initialization
                val tagService = project.tagService()
                val filterActivated = tagService.filterByTags
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

        val tagService = project.tagService()
        tagService.filterByTags = filter

        if (filter)
            this.structure.filterByTags = tagService.tagExpression()
        else
            this.structure.filterByTags = null

        forceRefresh()
    }

    fun groupByTag(grouping: Boolean) {

        project.tagService().groupTag = grouping

        this.structure.groupTag = grouping

        forceRefresh()
    }

    fun refreshTags(tags: SortedMap<String, Tag>) {
        if (structure.groupTag || structure.filterByTags != null) {
            val stags = tags
                .map { it.key to it.value.gFiles.toList() }
                .toMap()
                .toSortedMap(TagComparator)
            structure.tags = stags
        }
        invalidateAsync()
    }

    fun refreshTags(tagsFilter: Expression?) {

        val filterActivated = project.tagService().filterByTags
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

    fun selectFromEditor() {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val editor = fileEditorManager.selectedTextEditor
            ?: return
        val file = editor.file as? GherkinFile
            ?: return
        val filepath = file.virtualFile.path.path

        val fileStack = mutableSetOf<Any>().apply {
            this.add(file)
            var m = file.module
            while (m != null) {
                this.add(m)
                m = m.parent()
            }
        }

        DumbService.getInstance(project).smartInvokeLater {
            PsiDocumentManager.getInstance(project).performWhenAllCommitted {

                TreeUtil.promiseExpand(tree) { tp ->

                    val userObject = (tp.lastPathComponent as? DefaultMutableTreeNode)?.userObject
                    if (userObject != null) {
                        if (userObject is ModuleNode && fileStack.contains(userObject.value))
                            Action.CONTINUE
                        else if (userObject is GherkinScenarioNode && userObject.value.containingFile == file)
                            Action.CONTINUE
                        else if (userObject is GherkinFeatureNode && userObject.value.containingFile == file)
                            Action.CONTINUE
                        else if (userObject is GherkinFileNode && userObject.file == file)
                            Action.CONTINUE
                        else if (userObject is GherkinTagNode && userObject.children.contains(GherkinFileNode(project, file, null)))
                            Action.CONTINUE
                        else
                            Action.SKIP_CHILDREN
                    }
                    else {
                        Action.CONTINUE
                    }
                }
                .onProcessed {

                    val offset = editor.caretModel.currentCaret.offset
                    val element = file.findElementAt(offset)

                    val candidates = mutableListOf<TreePath?>()

                    if (element != null) {
                        element.parentOfTypeIs<GherkinStepsHolder>(true)?.let {
                            candidates.add(GherkinScenarioNode(project, it, null).path())
                        }
                        element.parentOfTypeIs<GherkinFeature>(true)?.let {
                            candidates.add(GherkinFeatureNode(project, it, null).path())
                        }
                    }
                    candidates.add(GherkinFileNode(project, file, null).path())
                    val target = candidates.firstOrNull { it != null }

                    tree.selectionPath = target ?: it
                }
            }
        }
    }

    fun Any.path(): TreePath? = tree.model.getTreePath(this)

}

private val String.path get() = Path.of(this)

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

