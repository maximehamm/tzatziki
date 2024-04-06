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
import io.nimbly.i18n.translation.engines.TranslationEngineFactory


@State(name = "TranslationPlusSettings", storages = [Storage("translationPlus.xml")], category = SettingsCategory.CODE)
class TranslationPlusSettings : PersistentStateComponent<TranslationPlusSettings.State?> {

    var activeEngine: EEngine = EEngine.GOOGLE_FREE
    var keys: Map<EEngine, String> = mutableMapOf()

    override fun getState(): State {
        val state = State()

        state.activeEngine = activeEngine

        keys.map { k ->
            val attributes = createCredentialAttributes(k.value)
            val credentials = Credentials(k.key.name, k.value)
            PasswordSafe.instance.set(attributes, credentials)
        }

        return state
    }

    override fun loadState(state: State) {
        this.activeEngine = state.activeEngine

        state.keys = TranslationEngineFactory.engines()
            .map { engine ->
                val attributes = createCredentialAttributes(engine.type.name)
                val credentials = PasswordSafe.instance[attributes]
                val key = credentials?.getPasswordAsString() ?: ""
                engine.type to key}
            .toMap()

//        this.keys = state.keys
    }

    private fun createCredentialAttributes(key: String): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName("TranslationPlus", key)
        )
    }


    class State {
        var activeEngine: EEngine = EEngine.GOOGLE_FREE
        var keys: Map<EEngine, String> = mutableMapOf()
    }

    companion object {
        fun getSettings(): TranslationPlusSettings {
            return ApplicationManager.getApplication().getService(TranslationPlusSettings::class.java)
        }
    }
}



