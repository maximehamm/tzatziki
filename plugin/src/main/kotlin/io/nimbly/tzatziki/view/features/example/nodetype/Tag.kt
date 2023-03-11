package io.nimbly.tzatziki.view.features.example.nodetype

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.SmartList
import io.nimbly.tzatziki.view.features.example.util.GherkinUtil.isGherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors

/**
 * Represents a Gherkin Tag in the tool window.
 *
 *
 * One or multiple Gherkin files (as [FeatureFile]s) may be bound to a tag, meaning the tag is present in all
 * bound Gherkin files. It may be present one or more times in one file.
 */
class Tag(displayName: String, initialFile: VirtualFile, project: Project) : AbstractNodeType(displayName, project) {

    val featureFiles: MutableList<FeatureFile> = SmartList()

    /**
     * The reason a VirtualFile is required is that a tag is displayed only when it has at least one Gherkin file
     * associated to it.
     */
    init {
        featureFiles.add(FeatureFile(initialFile, displayName, project))
    }

    fun hasFeatureFile(): Boolean {
        return featureFiles.isNotEmpty()
    }

    /**
     * Gets whether the argument virtual file is assigned to this tag.
     *
     * @param bddFile the Gherkin or Story file
     * @return true if the file is assigned, false otherwise
     */
    operator fun contains(bddFile: VirtualFile): Boolean {
        return featureFiles.stream().anyMatch { featureFile -> featureFile.file == bddFile }
    }

    /**
     * Adds the provided file to this tag if it isn't already added.
     *
     *
     * If, after adding the file, there are multiple files with its name, linked to this tag, then their display names
     * are updated to contain the Feature keywords or the relative paths from the project root in their display names.
     *
     * @param file the file to add
     */
    fun add(file: VirtualFile): Tag {
        if (!this.contains(file)) {
            featureFiles.add(FeatureFile(file, displayName, project))
            updateDisplayNames(file)
        }
        return this
    }

    /**
     * Updates the display names of files that have the same name as the argument file.
     *
     * @param file the file to update display names based on
     */
    fun updateDisplayNames(file: VirtualFile) {
        var featureFilesWithTheSameName: List<FeatureFile> = emptyList()
        if (featureFiles.size > 1 && getFeatureFilesWithTheNameOf(file).also {
                featureFilesWithTheSameName = it
            }.size > 1)
        {
            updateDisplayNamesOf(featureFilesWithTheSameName, file)
        }
    }

    /**
     * Removes the provided file from the underlying set of linked files.
     *
     *
     * When a Gherkin or Story file is removed, and with that name only one file remains under this tag, then the remaining
     * file's display name is restored to the file's name from the relative path.
     *
     *
     * If more than one file remains with that name they are examined and updated to contain the Feature keywords
     * or the relative paths from the project root in their display names.
     *
     * @param file the file to remove
     */
    fun remove(file: VirtualFile) {
        featureFiles.removeIf { featureFile -> featureFile.path == file.path }
        if (featureFiles.size == 1) {
            featureFiles[0]!!.resetDisplayName()
        } else if (featureFiles.size > 1) {
            val featureFilesWithTheSameName = getFeatureFilesWithTheNameOf(file)
            if (featureFilesWithTheSameName.size == 1) {
                featureFilesWithTheSameName[0]!!.resetDisplayName()
            } else if (featureFilesWithTheSameName.size > 1) {
                updateDisplayNamesOf(featureFilesWithTheSameName, file)
            }
        }
    }

    /**
     * Updates the display names of feature files after a change.
     *
     *
     * **Gherkin files**
     *
     *
     * If all files with the same name have different top level Feature keyword names, then those values are used
     * besides the filenames to identify which file is which, otherwise instead of using the Feature names, the
     * files' relative path from the project root are set as identifiers.
     *
     *
     * **JBehave Story files**
     *
     *
     * Since Story files don't have a unique keyword like the Feature in Gherkin, only the path-based distinction is applied.
     */
    private fun updateDisplayNamesOf(featureFilesWithTheSameName: List<FeatureFile>, file: VirtualFile) {
        if (isGherkinFile(file)) {
            val distinctFeatureNames = featureFilesWithTheSameName.stream()
                .map { featureFile -> PsiManager.getInstance(project).findFile(featureFile.file) }
                .filter { obj: PsiFile? -> Objects.nonNull(obj) }
                .map { psiFile: PsiFile? -> (psiFile as GherkinFile?)!!.features }
                .filter { features: Array<GherkinFeature> -> features.size > 0 }
                .map { features: Array<GherkinFeature> -> features[0].featureName } //regardless of the number of Feature keywords in the file, it always takes the first one if there is at least one
                .distinct()
                .collect(Collectors.toList())
            if (distinctFeatureNames.size == featureFilesWithTheSameName.size) {
                for (i in distinctFeatureNames.indices) {
                    featureFilesWithTheSameName[i]!!.setDisplayNameWithFeatureName(distinctFeatureNames[i])
                }
            } else {
                featureFilesWithTheSameName.forEach(Consumer { obj: FeatureFile? -> obj!!.setDisplayNameWithPath() })
            }
        } else { //JBehave Story files
            featureFilesWithTheSameName.forEach(Consumer { obj: FeatureFile? -> obj!!.setDisplayNameWithPath() })
        }
    }

    private fun getFeatureFilesWithTheNameOf(file: VirtualFile): List<FeatureFile> {
        return featureFiles.stream().filter { featureFile: FeatureFile? -> featureFile!!.hasFileName(file.name) }
            .collect(
                Collectors.toList()
            )
    }

    /**
     * Sorts the BDD files by their filenames if there is more than one BDD file in this tag.
     */
    override fun sort() {
        if (featureFiles.size > 1) {
            featureFiles.sortWith(Comparator.comparing {
                featureFile -> featureFile.name.lowercase(Locale.getDefault()) })
        }
    }

    override fun toString(): String {
        return displayName
    }


    override fun dispose() {
        featureFiles.clear()
    }
}
