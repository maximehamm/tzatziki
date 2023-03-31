package io.nimbly.tzatziki.view.features

import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.services.Tag
import io.nimbly.tzatziki.services.TagComparator
import io.nimbly.tzatziki.services.tagService
import io.nimbly.tzatziki.util.file
import io.nimbly.tzatziki.util.parent
import io.nimbly.tzatziki.util.parentOfTypeIs
import io.nimbly.tzatziki.view.features.actions.ExportPdfAction
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
import com.intellij.openapi.diagnostic.Logger
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
import com.intellij.ui.tree.TreeVisitor.Action
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

// See com.intellij.ide.bookmark.ui.BookmarksView
class FeaturePanel(val project: Project) : SimpleToolWindowPanel(true), Disposable {

    val LOG = Logger.getInstance(FeaturePanel::class.java);

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
            it.addSeparator(" ")
            it.add(GroupByModuleAction(this))
            it.add(GroupByTagAction(this))
            it.add(FilterTagAction(this))
            it.addSeparator(" ")
            it.add(RunTestAction(this))
            it.add(ExportPdfAction(this))
        }

        val toolbar = ActionManager.getInstance().createActionToolbar("CucumberPlusFeatureTree", group, false)
        toolbar.targetComponent = tree
        setToolbar(toolbar.component)
        tree.addMouseListener(MouseListening(tree, project))

        tree.selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION

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
        if (editor == null) {
            LOG.warn("No editor found")
            return
        }
        val file = editor.file as? GherkinFile
        if (file == null) {
            LOG.warn("No Gherkin file found")
            return
        }

        val fileStack = mutableSetOf<Any>().apply {
            this.add(file)
            var m = file.module
            while (m != null) {
                this.add(ModuleId(m.name))
                m = m.parent
            }
        }
        fileStack.forEachIndexed { index, any ->
            LOG.info("Stack #$index = " + any + ", class is: ${any.javaClass}")
        }

        // Find target
        val offset = editor.caretModel.currentCaret.offset
        val element = file.findElementAt(offset)
        val candidates = mutableListOf<PsiElement?>()
        if (element != null) {
            candidates.add(element.parentOfTypeIs<GherkinStepsHolder>(true))
            candidates.add(element.parentOfTypeIs<GherkinFeature>(true))
        }
        candidates.add(file)
        LOG.info("${candidates.size} candidates found")

        // Expand nodes & select them
        DumbService.getInstance(project).smartInvokeLater {
            PsiDocumentManager.getInstance(project).performWhenAllCommitted {
                UIUtil.invokeAndWaitIfNeeded<Any> {

                    // Expand and collect
                    val allPaths = mutableListOf<TreePath>()
                    var treePath: TreePath? = null
                    TreeUtil.promiseExpand(tree) { tp: TreePath ->
                        LOG.info("Parsing path: ${tp.path}")
                        val lastPathComponent = tp.lastPathComponent
                        if (lastPathComponent == null) {
                            LOG.info("lastPathComponent is null")
                        }
                        else {
                            LOG.info("lastPathComponent class is: ${lastPathComponent.javaClass}")
                        }
                        val userObject = (lastPathComponent as? DefaultMutableTreeNode)?.userObject
                        if (userObject == null) {
                            LOG.info("userObject is null")
                        }
                        else {
                            LOG.info("userObject is: $userObject, classc is ${userObject.javaClass}")
                        }

                        if (userObject != null) {
                            if (userObject is ModuleNode && fileStack.contains(ModuleId(userObject.value.name))) {
                                LOG.info("userObject continue 1")
                                Action.CONTINUE
                            } else if (userObject is GherkinScenarioNode && userObject.value.containingFile == file) {
                                if (candidates.contains(userObject.value)) treePath = tp
                                LOG.info("userObject continue 2")
                                Action.CONTINUE
                            } else if (userObject is GherkinFeatureNode && userObject.value.containingFile == file) {
                                if (candidates.contains(userObject.value)) treePath = tp
                                LOG.info("userObject continue 3")
                                Action.CONTINUE
                            } else if (userObject is GherkinFileNode && userObject.file == file) {
                                treePath = tp
                                LOG.info("userObject continue 4")
                                Action.CONTINUE
                            } else if (userObject is GherkinTagNode && userObject.children.contains(GherkinFileNode(project, file, null))) {
                                if (treePath != null) {
                                    allPaths.add(treePath!!)
                                    treePath = null
                                }
                                LOG.info("userObject continue 5")
                                Action.CONTINUE
                            } else {
                                LOG.info("userObject skip 1")
                                Action.SKIP_CHILDREN
                            }
                        }
                        else {
                            LOG.info("userObject continue 6")
                            Action.CONTINUE
                        }
                    }
                    .onProcessed {

                        if (treePath != null)
                            allPaths.add(treePath!!)

                        LOG.info("${allPaths.size} path to be selected")
                        tree.selectionPaths = allPaths.toTypedArray()
                    }
                    .onError { err ->
                        LOG.error("Some error occured", err)
                    }
                }
            }
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

