package icons

import com.intellij.openapi.util.IconLoader

object ActionIcons {

    @JvmField val SHIFT_LEFT = IconLoader.getIcon("/io/nimbly/tzatziki/icons/column-shift-left-16x16.png", javaClass)
    @JvmField val SHIFT_RIGHT = IconLoader.getIcon("/io/nimbly/tzatziki/icons/column-shift-right-16x16.png", javaClass)
    @JvmField val SHIFT_UP = IconLoader.getIcon("/io/nimbly/tzatziki/icons/line-16x16-shift-up.png", javaClass)
    @JvmField val SHIFT_DOWN = IconLoader.getIcon("/io/nimbly/tzatziki/icons/line-16x16-shift-down.png", javaClass)

    @JvmField val INSERT_LINE = IconLoader.getIcon("/io/nimbly/tzatziki/icons/line-insert-16x16.png", javaClass)
    @JvmField val INSERT_COLUMN = IconLoader.getIcon("/io/nimbly/tzatziki/icons/column-insert-16x16.png", javaClass)
    @JvmField val DELETE_LINE = IconLoader.getIcon("/io/nimbly/tzatziki/icons/line-delete-16x16.png", javaClass)
    @JvmField val DELETE_COLUMN = IconLoader.getIcon("/io/nimbly/tzatziki/icons/culumn-delete-16x16.png", javaClass)

    @JvmField val EXPORT = IconLoader.getIcon("/io/nimbly/tzatziki/icons/export-16x16.png", javaClass)
}