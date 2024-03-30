package io.nimbly.tzatziki.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.*
import io.cucumber.tagexpressions.Expression
import io.nimbly.tzatziki.util.*
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinTag
import java.util.*

@Service(Service.Level.PROJECT)
class TzFileService(val project: Project) : Disposable {

    private var tags: SortedMap<String, Tag>? = null
    private var tagsFilter: Expression? = null
    private var tagsFilterInitialized = false

    private val tagsListeners = mutableListOf<TagsEventListener>()
    private val tagsFilterListeners = mutableListOf<TagsFilterEventListener>()

    init {
        PsiManager.getInstance(project).addPsiTreeChangeListener(
            PsiChangeListener(this),
            DisposalService.getInstance(project)
        )
    }

    var sourcePathOnly: Boolean
        get() = state().sourcePathOnly == true
        set(b) {
            state().sourcePathOnly = b
        }

    var filterByTags: Boolean
        get() = state().filterByTags == true
        set(b) {
            state().filterByTags = b
        }

    var groupTag: Boolean
        get() = state().groupTag == true
        set(b) {
            state().groupTag = b
        }

    var selectedTags: List<String>
        get() = state().selectedTags
        set(b) {
            state().selectedTags = b
        }

    var selection: String?
        get() = state().selection
        set(v) {
            state().selection = v
        }

    fun tagExpression(): Expression? =
            state().tagExpression()

    fun getTags(): SortedMap<String, Tag> {
        if (tags == null) {
            refreshTags(false)
        }
        return tags!!
    }

    fun getTagsFilter(): Expression? {
        if (!tagsFilterInitialized) {
            val state = state()
            tagsFilter = state.tagExpression()
            tagsFilterInitialized = true
        }
        return tagsFilter
    }


    private fun state()
        = project.getService(TzPersistenceStateService::class.java)

    fun addTagsListener(listener: TagsEventListener) {
        this.tagsListeners.add(listener)
    }
    private fun updateTags(tags: SortedMap<String, Tag>, tagsUpdated: Boolean) {
        this.tags = tags
        this.tagsListeners.forEach {
            it.tagsUpdated(TagEvent(tags, tagsUpdated, this))
        }
    }

    fun addTagsFilterListener(listener: TagsFilterEventListener) {
        this.tagsFilterListeners.add(listener)
    }
    fun updateTagsFilter(tagsFilter: Expression?) {
        this.tagsFilter = tagsFilter
        this.tagsFilterListeners.forEach {
            it.tagsFilterUpdated(TagFilterEvent(tagsFilter, this))
        }
    }

    internal fun refreshTags(updateListeners: Boolean = true, force: Boolean = true) {

        // Get all tags
        val tags = findAllTags(project, project.getGherkinScope())

        // Check if tags are still the same
        val tagsUpdated = (tags == this.tags)

        // Update and inform listeners
        if (updateListeners)
            updateTags(tags, tagsUpdated)
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

    fun getGherkinScope(module: Module, recursive: Boolean = false): GlobalSearchScope {
        if (recursive) {
            var scope = this.getGherkinScope(module)
            module.subModules.forEach { m ->
                scope = scope.union(m.getGherkinScope(true))
            }
            return scope
        }
        
        val base = 
            if (!sourcePathOnly)
                module.moduleContentScope
            else
                GlobalSearchScope.moduleScope(module)
        
        return GlobalSearchScope.getScopeRestrictedByFileTypes(base, GherkinFileType.INSTANCE)
    }

    fun findAllGerkinsFiles(project: Project): Set<GherkinFile> {
        val scope = project.getGherkinScope()
        return findAllGerkinsFiles(scope, project)
    }

    fun findAllGerkinsFiles(module: Module, recursive: Boolean = false): Set<GherkinFile> {

        val scope = module.getGherkinScope(recursive)
        return findAllGerkinsFiles(scope, module.project)
    }

    private fun findAllGerkinsFiles(scope: GlobalSearchScope, project: Project): Set<GherkinFile> {

        val allFeatures = mutableSetOf<GherkinFile>()
        FilenameIndex
            .getAllFilesByExt(project, GherkinFileType.INSTANCE.defaultExtension, scope)
            .map { vfile -> vfile.getFile(project) }
            .filterIsInstance<GherkinFile>()
            .forEach { file ->
                allFeatures.add(file)
            }

        return allFeatures
    }

}

fun findAllGerkinsFiles(project: Project): Set<GherkinFile> {
    return project.tzFileService().findAllGerkinsFiles(project)
}

fun findAllGerkinsFiles(module: Module, recursive: Boolean = false): Set<GherkinFile> {
    return module.project.tzFileService().findAllGerkinsFiles(module, recursive)
}


fun Module.getGherkinScope(recursive: Boolean = false): GlobalSearchScope {
    return this.project.tzFileService().getGherkinScope(this, recursive)
}

fun Module.getGherkinScope(): GlobalSearchScope {
    return this.project.tzFileService().getGherkinScope(this)
}

fun Project.tzFileService(): TzFileService
    = this.getService(TzFileService::class.java)

interface TagsEventListener : EventListener {
    fun tagsUpdated(event: TagEvent) {}
}
class TagEvent(
    val tags: SortedMap<String, Tag>,
    val tagsUpdated: Boolean,
    source: Any) : EventObject(source)

interface TagsFilterEventListener : EventListener {
    fun tagsFilterUpdated(event: TagFilterEvent) {}
}
class TagFilterEvent(val tagsFilter: Expression?, source: Any) : EventObject(source)


private class PsiChangeListener(val service: TzFileService) : PsiTreeChangeListener {

    override fun beforeChildAddition(event: PsiTreeChangeEvent) = Unit
    override fun beforeChildRemoval(event: PsiTreeChangeEvent) = Unit
    override fun beforeChildReplacement(event: PsiTreeChangeEvent) = Unit
    override fun beforeChildMovement(event: PsiTreeChangeEvent) = Unit
    override fun beforeChildrenChange(event: PsiTreeChangeEvent) = Unit
    override fun beforePropertyChange(event: PsiTreeChangeEvent) = Unit

    override fun childMoved(event: PsiTreeChangeEvent) {
        if (event.child is GherkinFile) {
            refresh(true)
        }
        event(event)
    }
    override fun childRemoved(event: PsiTreeChangeEvent) {
        if (event.child is GherkinFile) {
            refresh(true)
        }
        event(event)
    }
    override fun childAdded(event: PsiTreeChangeEvent) {
        if (event.child is GherkinFile) {
            refresh(true)
        }
        event(event)
    }

    override fun propertyChanged(event: PsiTreeChangeEvent) {
        if (event.parent is PsiDirectory && event.propertyName == "fileName") {
            // Renaming file
            val file = event.element
            if (file !is GherkinFile)
                return
            refresh(true)
        }
        if (event.propertyName == "roots") {
            // Renaming module 
            refresh(true)
        }

        event(event)
    }

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

    fun refresh(structure: Boolean = false) {
        DumbService.getInstance(service.project).smartInvokeLater {
            PsiDocumentManager.getInstance(service.project).performLaterWhenAllCommitted() {
                service.refreshTags(true, structure)
            }
        }
    }
}

class Tag(gherkinTag: GherkinTag): Comparable<Tag> {

    val name: String
    internal val _tags: MutableSet<GherkinTag>
    internal val _files: MutableSet<GherkinFile>

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

    override fun compareTo(other: Tag): Int {
       return this.name.compareTo(other.name)
    }
}

private val CacheTagsKey: Key<CachedValue<List<GherkinTag>>> = Key.create("io.nimbly.tzatziki.util.tagsfinder")

private fun findAllTags(project: Project, scope: GlobalSearchScope): SortedMap<String, Tag> {
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
                val name = gtag.name.substringAfter("@")
                var tag = allTags[name]
                if (tag == null) {
                    tag = Tag(gtag)
                    allTags[name] = tag
                }
                tag._tags.add(gtag)
                tag._files.add(gtag.containingFile as GherkinFile)
            }
        }
    return allTags.toSortedMap(TagComparator)
}

object TagComparator : Comparator<String> {
    override fun compare(o1: String?, o2: String?): Int {
        if (o1 == null || o2 == null)
            return 0

        val uo1 = o1.uppercase()
        val uo2 = o2.uppercase()

        val c = uo1.compareTo(uo2)
        if (c != 0)
            return c

        return o1.compareTo(o2)
    }
}