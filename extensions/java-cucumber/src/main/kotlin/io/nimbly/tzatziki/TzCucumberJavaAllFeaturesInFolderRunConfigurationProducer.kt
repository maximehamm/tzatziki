package io.nimbly.tzatziki

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.cucumber.java.run.CucumberJavaAllFeaturesInFolderRunConfigurationProducer
import org.jetbrains.plugins.cucumber.java.run.CucumberJavaRunConfiguration

/**
 * Ensure cucumber producer will be the prefered one (order=first)
 */
@Deprecated("Test ro be removed !")
class TzCucumberJavaAllFeaturesInFolderRunConfigurationProducer : CucumberJavaAllFeaturesInFolderRunConfigurationProducer() {


    override fun isPreferredConfiguration(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        return shouldReplace(self, other)
    }

    override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        // Warning : this method should return within some millis...
        // If not, the run action presentation will ignore custom config name
        // build by getConfigurationName method
        // @See BaseRunConfigurationAction:update()
        return true
    }

//    override fun setupConfigurationFromContext(
//        configuration: CucumberJavaRunConfiguration,
//        context: ConfigurationContext,
//        sourceElement: Ref<PsiElement>
//    ): Boolean {
//
//        val b = super.setupConfigurationFromContext(configuration, context, sourceElement)
//
//        val glueProvider = getGlueProvider(sourceElement.get())
//        glueProvider?.calculateGlue {
//            println(it)
//        }
//
//        return b
//    }

}
