import {ESLintPlugin, EslintPluginState} from "./eslint-plugin";

class ESLintPluginFactory implements LanguagePluginFactory {
    create(state: EslintPluginState): { languagePlugin: LanguagePlugin; readyMessage?: any } {
        return {languagePlugin: new ESLintPlugin(state)};
    }
}

let factory = new ESLintPluginFactory();

export {factory};