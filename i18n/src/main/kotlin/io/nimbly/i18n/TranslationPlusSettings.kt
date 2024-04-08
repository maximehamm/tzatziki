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
    var keys: Map<EEngine, String> = mutableMapOf()

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

        keys.forEach { k ->
            val attributes = createCredentialAttributes(k.key)
            val credentials = Credentials(k.key.name, k.value)
            PasswordSafe.instance.set(attributes, credentials)
        }

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

        this.keys = TranslationEngineFactory.engines()
            .map { engine ->
                val attributes = createCredentialAttributes(engine.type)
                val credentials = PasswordSafe.instance[attributes]
                val key = credentials?.getPasswordAsString() ?: ""
                engine.type to key}
            .toMap()
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



