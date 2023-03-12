package io.nimbly.tzatziki.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.*
import io.nimbly.tzatziki.util.*
import io.nimbly.tzatziki.view.features.DisposalService
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinTag
import java.util.*

@Service(Service.Level.PROJECT)
class TzTagService(val project: Project) : Disposable {

    private var tags: Map<String, Tag>? = null
    private val listeners = mutableListOf<TagEventListener>()

    init {
        PsiManager.getInstance(project).addPsiTreeChangeListener(
            PsiChangeListener(this),
            DisposalService.getInstance(project)
        )
    }

    fun getTags(): Map<String, Tag> {
        if (tags == null) {
            refreshTags(false)
        }
        return tags!!
    }

    fun addListener(listener: TagEventListener) {
        this.listeners.add(listener)
    }

    private fun updateListeners(tags: Map<String, Tag>) {
        this.tags = tags
        this.listeners.forEach {
            it.tagsUpdated(TagEvent(tags, this))
        }
    }

    internal fun refreshTags(updateListeners: Boolean = true) {

        // Get all tags
        val tags: Map<String, Tag> = findAllTags(project, project.getGherkinScope())

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

class TagEvent(val tags: Map<String, Tag>, source: Any) : EventObject(source)

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

class Tag(gherkinTag: GherkinTag) {

    val name: String
    private val _tags: MutableSet<GherkinTag>
    private val _files: MutableSet<GherkinFile>

    init {
        name = gherkinTag.name
        _tags = mutableSetOf()
        _files = mutableSetOf()
        addTag(gherkinTag)
    }

    val gtags: Set<GherkinTag> get() = _tags
    val gFiles: Set<GherkinFile> get() = _files

    internal fun addTag(tag: GherkinTag) {
        _tags.add(tag)
        _files.add(tag.containingFile as GherkinFile)
    }
}

private val CacheTagsKey: Key<CachedValue<List<GherkinTag>>> = Key.create("io.nimbly.tzatziki.util.tagsfinder")

private fun findAllTags(project: Project, scope: GlobalSearchScope): Map<String, Tag> {
    val allTags = mutableMapOf<String, Tag>()
    FilenameIndex
        .getAllFilesByExt(project, GherkinFileType.INSTANCE.defaultExtension, scope)
        .map { vfile -> vfile.getFile(project) }
        .filterIsInstance<GherkinFile>()
        .forEach { file ->
            val tags = CachedValuesManager.getCachedValue(file, CacheTagsKey) {

                val tags: List<GherkinTag> = PsiTreeUtil.collectElements(file) { element -> element is GherkinTag }
                    .map { it as GherkinTag }
                    .filter { it.name.isNotEmpty() }

                CachedValueProvider.Result.create(
                    tags,
                    PsiModificationTracker.MODIFICATION_COUNT, file
                )
            }

            tags.forEach { gtag: GherkinTag ->
                var tag = allTags[gtag.name]
                if (tag == null) {
                    tag = Tag(gtag)
                    allTags[gtag.name] = tag
                }
            }
        }
    return allTags.toSortedMap()
}

