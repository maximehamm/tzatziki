package io.nimbly.tzatziki.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import io.nimbly.tzatziki.util.findAllTags
import io.nimbly.tzatziki.util.getGherkinScope
import io.nimbly.tzatziki.view.features.DisposalService
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinTag
import java.util.*

@Service(Service.Level.PROJECT)
class TzTagService(val project: Project) : Disposable {

    private var tags: List<String>? = null
    private val listeners = mutableListOf<TagEventListener>()

    init {
        PsiManager.getInstance(project).addPsiTreeChangeListener(
            PsiChangeListener(this),
            DisposalService.getInstance(project)
        )
    }

    fun getTags(): List<String> {
        if (tags == null) {
            refreshTags(false)
        }
        return tags!!
    }

    fun addListener(listener: TagEventListener) {
        this.listeners.add(listener)
    }

    private fun updateListeners(tags: List<String>) {
        this.tags = tags
        this.listeners.forEach {
            it.tagsUpdated(TagEvent(tags, this))
        }
    }

    internal fun refreshTags(updateListeners: Boolean = true) {

        // Get all tags
        val tags: List<String> = findAllTags(project, project.getGherkinScope())
            .groupBy { it.name }
            .keys
            .map { "@$it" }
            .sortedBy { it.toUpperCase() }

        // Check if tags are still the same
        if (tags == this.tags)
            return

        // Update and inform listeners
        if (updateListeners)
            updateListeners(tags)
        else
            this.tags = tags
    }

    companion object {
        fun getInstance(project: Project): DisposalService {
            return project.getService(DisposalService::class.java)
        }
    }

    override fun dispose() {
        this.tags = null
    }
}

interface TagEventListener : EventListener {
    fun tagsUpdated(event: TagEvent) {}
}

class TagEvent(val tags: List<String>, source: Any) : EventObject(source)

private class PsiChangeListener(val service: TzTagService) : PsiTreeChangeListener {

    override fun beforeChildAddition(event: PsiTreeChangeEvent) = Unit
    override fun beforeChildRemoval(event: PsiTreeChangeEvent) = Unit
    override fun beforeChildReplacement(event: PsiTreeChangeEvent) = Unit
    override fun beforeChildMovement(event: PsiTreeChangeEvent) = Unit
    override fun beforeChildrenChange(event: PsiTreeChangeEvent) = Unit
    override fun beforePropertyChange(event: PsiTreeChangeEvent) = Unit

    override fun childAdded(event: PsiTreeChangeEvent) = event(event)
    override fun childMoved(event: PsiTreeChangeEvent) = event(event)
    override fun childRemoved(event: PsiTreeChangeEvent) = event(event)
    override fun propertyChanged(event: PsiTreeChangeEvent) = event(event)

    override fun childReplaced(event: PsiTreeChangeEvent) {
        val tag = event.parent as? GherkinTag
        if (tag != null)
            refresh()
    }

    override fun childrenChanged(event: PsiTreeChangeEvent) {
        val parent = event.parent
            ?: return
        val file = parent.containingFile
        if (file !is GherkinFile)
            return
        refresh()
    }

    private fun event(event: PsiTreeChangeEvent) {
        //NA
    }

    fun refresh() {
        DumbService.getInstance(service.project).smartInvokeLater {
            PsiDocumentManager.getInstance(service.project).performLaterWhenAllCommitted() {
                service.refreshTags()
            }
        }
    }

}
