package io.nimbly.tzatziki.view.features.example.nodetype

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import io.nimbly.tzatziki.view.features.example.TagOccurrencesRegistry
import java.util.*

class FeatureFile(val file: VirtualFile, private val parentTag: String, project: Project) : AbstractNodeType(
    file.name, project
) {

    val path: String
        get() = file.path
    val name: String
        get() = file.name
    val fileType: FileType
        get() = file.fileType

    fun hasFileName(name: String?): Boolean {
        return name != null && name == file.name
    }

    fun resetDisplayName() {
        displayName = file.name
    }

    /**
     * Sets the display name to the combination of the file's name and argument Feature keyword text.
     *
     *
     * For instance, with a file name `smoke.feature` and a Feature name `Smoke testing`, it sets
     * the string `smoke.feature [Smoke testing]` as the display name.
     *
     * @param featureName the Feature keywords text
     */
    fun setDisplayNameWithFeatureName(featureName: String) {
        displayName = file.name + " [" + featureName + "]"
    }

    /**
     * Sets the display name to the combination of the file's name and its relative path to the project's root folder.
     *
     *
     * The following cases are viable:
     *
     *  * in case there is no relative path for some reason, then the display name is the file's name, e.g. `"smoke.feature"`
     *  * if the relative path is empty (the file is located in the project root folder), then the display name is set as e.g. `"smoke.feature [/]"`
     *  * if the file is located somewhere deeper in the project, then the display name is set as e.g. `"smoke.feature [module-name/src/main/resources/features]"`
     *
     */
    fun setDisplayNameWithPath() {
        val relativePath = VfsUtilCore.getRelativePath(file.parent, project.guessProjectDir()!!)
        displayName =
            if (relativePath != null) file.name + " [" + (if (relativePath.isEmpty()) "/" else relativePath) + "]" else file.name
    }

    override fun toString(): String {
        return displayName + " (" + count() + ")"
    }

    private fun count(): Int {
        return TagOccurrencesRegistry.getInstance(project).getCountFor(file.path, parentTag)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as FeatureFile
        return file.path == that.file.path
    }

    override fun hashCode(): Int {
        return Objects.hash(file)
    }
}
