package io.nimbly.tzatziki.view.features.example.util

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinLanguage
import java.util.stream.Collectors

/**
 * Provider utility methods for Gherkin files.
 */
object GherkinUtil {
    /**
     * Collects all Gherkin files from the provided project.
     *
     * @return the list of Gherkin files, or empty list if no Gherkin file is found
     */
    fun collectGherkinFilesFromProject(project: Project): List<PsiFile> {
        return if (FileTypeManager.getInstance().findFileTypeByLanguage(GherkinLanguage.INSTANCE) != null) {
            FileTypeIndex.getFiles(GherkinFileType.INSTANCE, GlobalSearchScope.projectScope(project))
                .stream()
                .map { file: VirtualFile ->
                    PsiManager.getInstance(project).findFile(file)
                }
                .collect(Collectors.toList())
                .filterNotNull()
        } else emptyList()
    }

    /**
     * Returns whether the argument file is a Gherkin file.
     */
    fun isGherkinFile(file: PsiFile): Boolean {
        return GherkinFileType.INSTANCE == file.fileType
    }

    /**
     * Returns whether the argument file is a Gherkin file.
     */
    fun isGherkinFile(file: VirtualFile): Boolean {
        return GherkinFileType.INSTANCE == file.fileType
    }
}
