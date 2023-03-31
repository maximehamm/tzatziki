package io.nimbly.tzatziki.util

import java.nio.file.Path
import java.nio.file.Paths

fun String.toPath() : Path
    = Paths.get(this)

fun <T> Map<Path, T>.toTree() : Node<T>? {

    var root : Node<T>? = null

    val nodes = this.map { Node(it.value, it.key) }

    fun findParent(path: Path): Node<T>? {
        if (path.nameCount == 0)
            return null

        val parentPath = path.parent

        return nodes.find { it.path == parentPath }
            ?: return findParent(parentPath)
    }

    nodes.forEach {
        val parent = findParent(it.path)
        it.parent = parent
        if (parent == null)
            root = it
        parent?.children?.add(it)
    }

    return root
}

class Node<T>(val value: T, val path: Path) {

    var parent: Node<T>? = null

    val children = mutableListOf<Node<T>>()
    fun add(child: Node<T>) {
        children.add(child)
    }

    fun find(value: T): Node<T>? {
        if (this.value == value)
            return this
        children.forEach {  child ->
            val found = child.find(value)
            if (found != null)
                return found
        }
        return null
    }
}