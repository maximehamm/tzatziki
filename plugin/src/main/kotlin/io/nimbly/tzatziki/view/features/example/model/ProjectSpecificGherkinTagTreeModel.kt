package io.nimbly.tzatziki.view.features.example.model

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import io.nimbly.tzatziki.view.features.example.nodetype.*

class ProjectSpecificGherkinTagTreeModel : GherkinTagTreeModel {

    constructor(project: Project) : super(project)
    constructor(data: ModelDataRoot?, project: Project) : super(data, project)

    override fun getContentRoot(file: PsiFile): CategoriesHolder?{
        return data
    }

    // The methods below are responsible for building the actual tree model from the backing model data.
    override fun getChild(parent: Any, index: Int): Any {
        var child: Any? = null
        if (parent is ModelDataRoot) {
            child = data!!.categories?.get(index)
        } else if (parent is Category) {
            child = NodeType.asCategory(parent).tags[index]
        } else if (parent is Tag) {
            child = NodeType.asTag(parent).featureFiles[index]
        }
        return child!!
    }

    override fun getChildCount(parent: Any): Int {
        var count = 0
        if (parent is ModelDataRoot) {
            count = data!!.categories?.size ?: 0
        } else if (parent is Category) {
            count = NodeType.asCategory(parent).tags.size
        } else if (parent is Tag) {
            count = NodeType.asTag(parent).featureFiles.size
        }
        return count
    }

    override fun isLeaf(node: Any): Boolean {
        var isLeaf = data!!.categories?.isEmpty()
        if (node is Category) {
            isLeaf = !NodeType.asCategory(node).hasTag()
        } else if (node is Tag) {
            isLeaf = !NodeType.asTag(node).hasFeatureFile()
        } else if (node is FeatureFile) {
            isLeaf = true
        }
        return isLeaf == true
    }

    override fun getIndexOfChild(parent: Any, child: Any?): Int {
        var indexOfChild = 0
        if (child != null) {
            if (parent is ModelDataRoot) {
                indexOfChild = data!!.categories?.indexOf(child) ?: 0
            } else if (parent is Category) {
                indexOfChild = NodeType.asCategory(parent).tags.indexOf(child)
            } else if (parent is Tag) {
                indexOfChild = NodeType.asTag(parent).featureFiles.indexOf(child)
            }
        } else {
            indexOfChild = -1
        }
        return indexOfChild
    }
}
