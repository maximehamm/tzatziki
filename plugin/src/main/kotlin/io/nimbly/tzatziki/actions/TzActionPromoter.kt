package io.nimbly.tzatziki.actions

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import io.nimbly.tzatziki.psi.cellAt

class TzActionPromoter : ActionPromoter {

    override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction>? {

        val shiftAction = actions.find { it is TableShiftAction }
        if (shiftAction != null) {

            val file = context.getData(CommonDataKeys.PSI_FILE)
            val offset = CommonDataKeys.CARET.getData(context)?.offset

            if (file!=null && offset!=null && file.cellAt(offset) != null) {
                return listOf(shiftAction)
            }
        }

        return null
    }
}