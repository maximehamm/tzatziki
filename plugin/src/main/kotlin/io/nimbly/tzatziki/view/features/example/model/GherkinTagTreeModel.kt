package io.nimbly.tzatziki.view.features.example.model

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.SlowOperations
import com.intellij.util.SmartList
import io.nimbly.tzatziki.view.features.example.nodetype.CategoriesHolder
import io.nimbly.tzatziki.view.features.example.nodetype.ModelDataRoot
import io.nimbly.tzatziki.view.features.example.util.GherkinUtil
import io.nimbly.tzatziki.view.features.example.util.TagNameUtil.tagNameFrom
import org.jetbrains.plugins.cucumber.psi.GherkinTag
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

abstract class GherkinTagTreeModel

protected constructor(private val project: Project) : TreeModel, Disposable {
    //    private final TagCategoryRegistry registry;
    //    private final JBehaveStoryService storyService;
    protected var data: ModelDataRoot? = null

    /**
     * Using this, an already built model data can be reused. For example when the layout changes in the tool window.
     */
    protected constructor(data: ModelDataRoot?, project: Project) : this(project) {
        this.data = data
    }

    override fun getRoot(): Any {
        return data!!
    }

    override fun dispose() {
        data!!.dispose()
        data = null
    }

    fun buildModel() {
        if (project.guessProjectDir() != null) {
            if (data == null) {
                data = ModelDataRoot(project)
            } else {
                //This is called when a layout switch happens in the tool window
                data!!.initData()
            }
            val gherkinFiles: MutableList<PsiFile> = SmartList()
            val storyFiles: List<PsiFile> = SmartList()
            //NOTE: Handling the whole logic in one stream() call chain may not return and process all Gherkin files in the project, hence the separation
            //NOTE2: Reading the Gherkin and Story files in separate read actions is in place to ensure that all files are read consistently.
            SlowOperations.allowSlowOperations<Boolean, RuntimeException> {
                DumbService.getInstance(
                    project
                ).runReadActionInSmartMode<Boolean> {
                    gherkinFiles.addAll(
                        GherkinUtil.collectGherkinFilesFromProject(project)
                    )
                }
            }

            persistGherkinTags(gherkinFiles)

            data!!.updateDisplayName()
            data!!.sort()
        }
    }

    private fun persistGherkinTags(gherkinFiles: List<PsiFile>) {
        for (file in gherkinFiles) {
            val gherkinTags = PsiTreeUtil.findChildrenOfType(
                file,
                GherkinTag::class.java
            )
            for (gherkinTag in gherkinTags) {
                addToContentRootAndCategory(tagNameFrom(gherkinTag), file)
            }
        }
    }

    private fun addToContentRootAndCategory(tagName: String, file: PsiFile) {
//        val categoryName: String = registry.categoryOf(tagName)
//        val contentRoot = getContentRoot(file)
//        if (categoryName != null) {
//            contentRoot!!.findCategory(categoryName)
//                .ifPresentOrElse(
//                    { category -> category.addTagOrFileToTag(tagName, file.virtualFile) }
//                ) {
//                    contentRoot!!.addCategory(
//                        Category(categoryName, project).add(
//                            Tag(
//                                tagName,
//                                file.virtualFile,
//                                project
//                            )
//                        )
//                    )
//                }
//        } else {
//            contentRoot.getOther().addTagOrFileToTag(tagName, file.virtualFile)
//        }
    }
    protected abstract fun getContentRoot(file: PsiFile): CategoriesHolder?
    override fun valueForPathChanged(path: TreePath, newValue: Any) {}
    override fun addTreeModelListener(l: TreeModelListener) {}
    override fun removeTreeModelListener(l: TreeModelListener) {}
}
