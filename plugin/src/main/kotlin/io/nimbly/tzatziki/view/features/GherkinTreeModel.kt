package io.nimbly.tzatziki.view.features

import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import io.nimbly.tzatziki.util.findAllGerkinsFiles
import io.nimbly.tzatziki.view.features.node.FeatureNode
import io.nimbly.tzatziki.view.features.node.GherkinFileNode
import io.nimbly.tzatziki.view.features.node.ProjectNode
import io.nimbly.tzatziki.view.features.node.ScenarioNode
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

class GherkinTreeModel(private val project: Project) : TreeModel, Disposable {

    var childrenOfProjet: List<GherkinFileNode>? = null

    override fun getRoot(): Any {
        return ProjectNode(project)
    }

    override fun getChild(parent: Any, index: Int): Any? {
        if (parent is ProjectNode) {
            return childrenOfProjet(parent.element).getOrNull(index)
        } else if (parent is GherkinFileNode) {
            return childrenOfGherkinFile(parent.element).getOrNull(index)
        } else if (parent is FeatureNode) {
            return childrenOfFeature(parent.element).getOrNull(index)
        }
        return null
    }

    override fun getChildCount(parent: Any): Int {
        if (parent is ProjectNode) {
            return childrenOfProjet(parent.element).size
        } else if (parent is GherkinFileNode) {
            return childrenOfGherkinFile(parent.element).size
        } else if (parent is FeatureNode) {
            return childrenOfFeature(parent.element).size
        }
        return 0
    }

    override fun isLeaf(node: Any): Boolean {
        if (node is ProjectNode) {
            return false
        } else if (node is GherkinFileNode) {
            return false
        } else if (node is FeatureNode) {
            return false
        }
        return true
    }

    override fun getIndexOfChild(parent: Any, child: Any): Int {
        if (parent is ProjectNode) {
            return childrenOfProjet(parent.element).size
        } else if (parent is GherkinFileNode) {
            return childrenOfGherkinFile(parent.element).size
        } else if (parent is FeatureNode) {
            return childrenOfFeature(parent.element).size
        }
        return -1
    }


    override fun valueForPathChanged(path: TreePath, newValue: Any) {
        //TODO
    }

    override fun addTreeModelListener(l: TreeModelListener) {
        //TODO
    }

    override fun removeTreeModelListener(l: TreeModelListener) {
        //TODO
    }

    override fun dispose() {
        //TODO
    }


    private fun childrenOfProjet(project: Project): List<GherkinFileNode> {
        if (childrenOfProjet == null) {
            childrenOfProjet =
                findAllGerkinsFiles(project)
                    .map { GherkinFileNode(project, it) }
                    .sortedBy { it.toString()}
        }
        return childrenOfProjet!!
    }

    private fun childrenOfGherkinFile(gherkinFile: GherkinFile): List<FeatureNode> {
        return gherkinFile.features
            .map { FeatureNode(gherkinFile.project, it) }
            .sortedBy { it.toString()}
    }

    private fun childrenOfFeature(feature: GherkinFeature): List<ScenarioNode> {
        return feature.scenarios
            .map { ScenarioNode(feature.project, it) }
            .sortedBy { it.toString()}
    }

    fun findNodes(child: PsiElement?): List<NodeDescriptor<Any>> {

        if (child is GherkinStepsHolder) {
            val nodes = childrenOfProjet
                ?.map { it }


        }
        return emptyList()
    }

//    fun updateModelForFile(file: PsiFile) {
//        TODO("Not yet implemented")
//    }
}