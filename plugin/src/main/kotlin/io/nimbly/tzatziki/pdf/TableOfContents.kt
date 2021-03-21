package io.nimbly.tzatziki.pdf

import io.nimbly.tzatziki.util.pop
import java.lang.Exception

class TableOfContentEntry(val label: String, val id: String) {
    val child = mutableListOf<TableOfContentEntry>()

    override fun toString(): String {
        return "$id - $label"
    }
}

class TableOfContents(val idName: String = "t", val initialIndent: String = "", val indent: String = "    ") {
    private var tableOfContents = mutableListOf<TableOfContentEntry>()
    private var output = StringBuilder()
    private var idIndex = 0

    val currentId:String
        get(){
            return idName + idIndex.toString()
        }

    init {
        tableOfContents.add(TableOfContentEntry("Table of contents Root", ""))
    }

    fun root(): TableOfContentEntry {
        return tableOfContents[0]
    }

    fun nextId():String{
        idIndex++
        return currentId
    }

    fun addEntry(level: Int, label: String) {
        when {
            level > tableOfContents.size -> {
                throw Exception("Missing table of contents level : " +
                        "actual level <${tableOfContents.size - 1}>, new entry level <$level>")
            }
            level < 1 -> {
                throw Exception("Min table of contents level is <1>, new entry level <$level>")
            }
            level <= tableOfContents.size -> {
                for (i in level..tableOfContents.size - 1) tableOfContents.pop()
            }

        }
        val entry = TableOfContentEntry(label, nextId())
        tableOfContents[tableOfContents.size - 1].child.add(entry)
        tableOfContents.add(entry)
    }

    fun ul(ulIndent: String): String {
        return ulIndent + "<ul class=\"toc\" >\n"
    }

    fun li(entry: TableOfContentEntry, liIndent: String): String {
        return liIndent + "<li href=\"#${entry.id}\"><a href=\"#${entry.id}\">${entry.label}</a></li>\n"
    }

    private fun generate(entry: TableOfContentEntry, level: Int, entryIndent: String) {
        if (level != 0) {
            output.append(li(entry, entryIndent))
        }
        if (entry.child.size > 0) {
            output.append(ul(entryIndent))
            for (children in entry.child) {
                generate(children, level + 1, entryIndent + indent)
            }
            output.append(entryIndent + "</ul>\n")
        }
    }

    fun generate(): String {
        output = java.lang.StringBuilder()
        generate(root(), 0, initialIndent)
        return output.toString()
    }

}