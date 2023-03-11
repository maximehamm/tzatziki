package io.nimbly.tzatziki.view.features.example.model

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import io.nimbly.tzatziki.view.features.example.nodetype.*

class ContentRootBasedGherkinTagTreeModel : GherkinTagTreeModel {

    constructor(project: Project) : super(project)
    constructor(data: ModelDataRoot?, project: Project) : super(data, project)

    override fun getContentRoot(file: PsiFile): CategoriesHolder? {
        return data?.findContentRootOrRootless(file)
    }

    // The methods below are responsible for building the actual tree model from the backing model data.
    override fun getChild(parent: Any?, index: Int): Any? {
        var child: Any? = null
        if (parent is ModelDataRoot && data!=null) {
            child = data!!.contentRootsByLayout[index]
        } else if (parent is ContentRoot) {
            child = NodeType.asContentRoot(parent).categories[index]
        } else if (parent is Category) {
            child = NodeType.asCategory(parent).tags[index]
        } else if (parent is Tag) {
            child = NodeType.asTag(parent).featureFiles[index]
        }
        return child
    }

    override fun getChildCount(parent: Any): Int {
        var count = 0
        if (parent is ModelDataRoot) {
            count = data!!.contentRootsByLayout.size
        } else if (parent is ContentRoot) {
            count = NodeType.asContentRoot(parent).categories.size
        } else if (parent is Category) {
            count = NodeType.asCategory(parent).tags.size
        } else if (parent is Tag) {
            count = NodeType.asTag(parent).featureFiles.size
        }
        return count
    }

    override fun isLeaf(node: Any): Boolean {
        var isLeaf = data!!.contentRootsByLayout.isEmpty()
        if (node is ContentRoot) {
            isLeaf = NodeType.asContentRoot(node).categories.isEmpty()
        } else if (node is Category) {
            isLeaf = !NodeType.asCategory(node).hasTag()
        } else if (node is Tag) {
            isLeaf = !NodeType.asTag(node).hasFeatureFile()
        } else if (node is FeatureFile) {
            isLeaf = true
        }
        return isLeaf
    }

    override fun getIndexOfChild(parent: Any, child: Any?): Int {
        var indexOfChild = 0
        if (parent != null && child != null) {
            if (parent is ModelDataRoot && child is ContentRoot?) {
                indexOfChild = data!!.contentRootsByLayout.indexOf(child) ?: 0
            } else if (parent is ContentRoot) {
                indexOfChild = NodeType.asContentRoot(parent).categories.indexOf(child)
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
