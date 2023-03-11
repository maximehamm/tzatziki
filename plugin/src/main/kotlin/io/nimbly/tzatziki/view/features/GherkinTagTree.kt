package io.nimbly.tzatziki.view.features

import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.psi.PsiManager
import javax.swing.JTree
import javax.swing.tree.TreeModel
import javax.swing.tree.TreeSelectionModel

/**
 * A custom JTree implementation for rendering the elements of the Gherkin tag tree.
 */
class GherkinTagTree(model: TreeModel) : JTree(model) {

    init {
        setCellRenderer(GherkinTagsNodeRenderer())
        getSelectionModel().selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        expandsSelectedPaths = true

    }

    /**
     * Node renderer for the Gherkin tag tree.
     *
     *
     * Configures the icons of nodes according to their node types.
     */
    internal class GherkinTagsNodeRenderer : NodeRenderer() {

        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) {
            super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus)

//            if (value is Project) {
//                icon =  AllIcons.General.ProjectTab
//                name = value.name
//
//            }
//            else if (value is GherkinFile) {
//                icon =  CucumberIcons.Cucumber
//                name = value.name
//            }
//            else {
//                print("Not yet implemented!")
//                //TODO
//            }
        }
    }
}
