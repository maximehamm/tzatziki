package io.nimbly.tzatziki.pdf

import io.nimbly.tzatziki.util.pop
import java.lang.Exception

class Summary(
    private val idName: String = "t",
    private val initialIndent: String = "",
    private val indent: String = "    ")
{
    private var table = mutableListOf<TableEntry>()
    private var output = StringBuilder()
    private var idIndex = 0

    val currentId:String
        get() = idName + idIndex.toString()

    init {
        table.add(TableEntry("Table of contents Root", ""))
    }

    private fun root() = table[0]

    private fun nextId():String{
        idIndex++
        return currentId
    }

    fun addEntry(level: Int, label: String) {
        when {
            level > table.size ->
                throw Exception("Missing table of contents level : " +
                    "actual level <${table.size - 1}>, new entry level <$level>")
            level < 1 ->
                throw Exception("Min table of contents level is <1>, new entry level <$level>")
            level <= table.size ->
                for (i in level until table.size) table.pop()
        }
        val entry = TableEntry(label, nextId())
        table.last().child.add(entry)
        table.add(entry)
    }

    private fun ul(ulIndent: String)
        = "$ulIndent<ul class=\"toc\" >\n"

    private fun li(entry: TableEntry, liIndent: String)
        = liIndent + "<li href=\"#${entry.id}\"><a href=\"#${entry.id}\">${entry.label}</a></li>\n"

    private fun generate(entry: TableEntry, level: Int, entryIndent: String) {
        if (level != 0)
            output.append(li(entry, entryIndent))

        if (entry.child.size > 0) {
            output.append(ul(entryIndent))
            entry.child.forEach {
                generate(it, level + 1, entryIndent + indent)
            }
            output.append("$entryIndent</ul>\n")
        }
    }

    fun generate(): String {
        output = java.lang.StringBuilder()
        generate(root(), 0, initialIndent)
        return output.toString()
    }
}

private class TableEntry(val label: String, val id: String) {
    val child = mutableListOf<TableEntry>()
    override fun toString(): String {
        return "$id - $label"
    }
}