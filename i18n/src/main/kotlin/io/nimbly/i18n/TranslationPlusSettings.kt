package io.nimbly.i18n

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import io.nimbly.i18n.translation.engines.EEngine
import io.nimbly.i18n.translation.engines.Lang
import io.nimbly.i18n.translation.engines.TranslationEngineFactory

@State(name = "TranslationPlusSettings", storages = [Storage("translationPlus.xml")], category = SettingsCategory.CODE)
class TranslationPlusSettings : PersistentStateComponent<TranslationPlusSettings.State?> {

    var activeEngine: EEngine = EEngine.GOOGLE

    private var _keys: Map<EEngine, String> = mutableMapOf()

    /**
     * Engine API keys. The setter persists to PasswordSafe asynchronously
     * (off EDT and out of any read-action), because PasswordSafe.set is a
     * slow non-cancellable operation forbidden inside read actions in 2025.3+.
     *
     * Use [setKeysFromStorage] when populating from PasswordSafe to avoid
     * a redundant write-back.
     */
    var keys: Map<EEngine, String>
        get() = _keys
        set(value) {
            _keys = value
            val snapshot = value.toMap()
            ApplicationManager.getApplication().executeOnPooledThread {
                snapshot.forEach { (engine, key) ->
                    val attributes = createCredentialAttributes(engine)
                    PasswordSafe.instance.set(attributes, Credentials(engine.name, key))
                }
            }
        }

    private fun setKeysFromStorage(value: Map<EEngine, String>) {
        _keys = value
    }

    var input: String = Lang.AUTO.code
    var output: String = Lang.DEFAULT.code

    var useRefactoring: Boolean = true
    var useRefactoringPreview: Boolean = true
    var useRefactoringSearchInComment: Boolean = true

    /**
     * Saving state
     */
    override fun getState(): State {
        val state = State()

        state.activeEngine = activeEngine

        state.input = input
        state.output = output

        state.useRefactoring = useRefactoring
        state.useRefactoringPreview = useRefactoringPreview
        state.useRefactoringSearchInComment = useRefactoringSearchInComment

        // Note: keys are persisted to PasswordSafe via the `keys` setter (off EDT),
        // not from here. getState() must not trigger slow operations because it is
        // called from a read action by the configuration store.
        return state
    }

    /**
     * Loading state
     */
    override fun loadState(state: State) {

        this.activeEngine = state.activeEngine

        this.input = state.input
        this.output = state.output

        this.useRefactoring = state.useRefactoring
        this.useRefactoringPreview = state.useRefactoringPreview
        this.useRefactoringSearchInComment = state.useRefactoringSearchInComment

        // Populate keys WITHOUT triggering the setter — otherwise we'd write back to
        // PasswordSafe what we just read from it.
        setKeysFromStorage(
            TranslationEngineFactory.engines()
                .map { engine ->
                    val attributes = createCredentialAttributes(engine.type)
                    val credentials = PasswordSafe.instance[attributes]
                    val key = credentials?.getPasswordAsString() ?: ""
                    engine.type to key
                }
                .toMap()
        )
    }

    private fun createCredentialAttributes(engine: EEngine): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName("TranslationPlus", engine.name)
        )
    }


    class State {

        var activeEngine: EEngine = EEngine.GOOGLE

        var input: String = Lang.AUTO.code
        var output: String = Lang.DEFAULT.code

        var useRefactoring: Boolean = true
        var useRefactoringPreview: Boolean = true
        var useRefactoringSearchInComment: Boolean = true
    }

    companion object {
        fun getSettings(): TranslationPlusSettings {
            return ApplicationManager.getApplication().getService(TranslationPlusSettings::class.java)
        }
    }
}



