import {CLIEngine, Linter} from "eslint";

class ESLintResponse {
  constructor(public request_seq: number, public command: string) {
  }

  body?: any;
  error?: string;
  isNoConfigFile?: boolean
}

export interface EslintPluginState extends PluginState {
    readonly eslintPackagePath: string;
    readonly standardPackagePath: string;
    readonly packageJsonPath?: string;
    readonly additionalRootDirectory?: string;
    readonly includeSourceText: boolean | null;
}

/**
 * See com.intellij.lang.javascript.linter.eslint.EslintUtil.FileKind
 */
enum FileKind {
    ts = "ts",
    html = "html",
    vue = "vue",
    jsAndOther = "js_and_other",
}

interface ESLintRequest {
    /**
     * Unique id of the message
     */
    readonly seq: number;

    /**
     * Message type (usually it is "request")
     */
    readonly type: string;

    /**
     * Id of the command
     */
    readonly command: string;

    /**
     * Additional arguments
     */
    readonly arguments: RequestArguments;
}

interface RequestArguments {
    /**
     * .eslintignore file path
     */
    readonly ignoreFilePath: string;
    /**
     * Absolute path for the file to check
     */
    readonly fileName: string;

    /**
     * Absolute config path
     */
    readonly configPath: string | null;
    readonly content: string;
    readonly extraOptions: string | null;
    readonly fileKind: FileKind
}

export class ESLintPlugin implements LanguagePlugin {
    private static readonly GetErrors: string = "GetErrors";
    private static readonly FixErrors: string = "FixErrors";
    private readonly includeSourceText: boolean | null;
    private readonly additionalRulesDirectory?: string;
    private readonly standardLinter: any;
    private readonly options: any;
    private readonly cliEngineCtor: any;

    constructor(state: EslintPluginState) {
        this.includeSourceText = state.includeSourceText;
        this.additionalRulesDirectory = state.additionalRootDirectory;
        let eslintPackagePath;
        if (state.standardPackagePath != null) {
            const standardPackagePath = state.standardPackagePath;
            this.standardLinter = requireInContext(standardPackagePath, state.packageJsonPath);
            // Standard doesn't provide API to check if file is ignored (https://github.com/standard/standard/issues/1448).
            // The only way is to use ESLint for that.
            eslintPackagePath = findESLintPackagePath(standardPackagePath, state.packageJsonPath);
        }
        else {
            eslintPackagePath = state.eslintPackagePath;
        }
        eslintPackagePath = normalizePath(eslintPackagePath);
        this.options = requireInContext(eslintPackagePath + "lib/options", state.packageJsonPath);
        this.cliEngineCtor = requireInContext(eslintPackagePath + "lib/api", state.packageJsonPath).CLIEngine;
    }

    onMessage(p: string, writer: MessageWriter): void {
        const request: ESLintRequest = JSON.parse(p);
        let response: ESLintResponse = new ESLintResponse(request.seq, request.command);
        try {
            if (request.command === ESLintPlugin.GetErrors) {
                response.body = this.filterSourceIfNeeded(this.getErrors(request.arguments));
            } else if (request.command === ESLintPlugin.FixErrors) {
                response.body = this.filterSourceIfNeeded(this.fixErrors(request.arguments))
            } else {
                response.error = `Unknown command: ${request.command}`
            }
        } catch (e) {
            response.isNoConfigFile = "no-config-found" === e.messageTemplate
              || (e.message && containsString(e.message.toString(), "No ESLint configuration found"));
            response.error = e.toString() + "\n\n" + e.stack;
        }
        writer.write(JSON.stringify(response));
    }

    private filterSourceIfNeeded(body: CLIEngine.LintReport): CLIEngine.LintReport {
        if (!this.includeSourceText) {
            body.results.forEach(value => {
                delete value.source
                value.messages.forEach(msg => delete msg.source)
            })
        }
        return body
    }

    private getErrors(getErrorsArguments: RequestArguments): CLIEngine.LintReport {
        return this.invokeESLint(getErrorsArguments)
    }

    private fixErrors(fixErrorsArguments: RequestArguments): CLIEngine.LintReport {
        return this.invokeESLint(fixErrorsArguments, {fix: true})
    }

    private invokeESLint(requestArguments: RequestArguments, additionalOptions: CLIEngine.Options = {}): CLIEngine.LintReport {
        const parsedCommandLineOptions = translateOptions(this.options.parse(requestArguments.extraOptions || ""));
        const options: CLIEngine.Options = {...parsedCommandLineOptions, ...additionalOptions};
        options.ignorePath = requestArguments.ignoreFilePath;
        if (requestArguments.configPath != null) {
            options.configFile = requestArguments.configPath;
        }
        if (this.additionalRulesDirectory != null && this.additionalRulesDirectory.length > 0) {
            if (options.rulePaths == null) {
                options.rulePaths = [this.additionalRulesDirectory]
            } else {
                options.rulePaths.push(this.additionalRulesDirectory);
            }
        }
        const cliEngine: CLIEngine = new this.cliEngineCtor(options);
        if (cliEngine.isPathIgnored(requestArguments.fileName)) {
            return createEmptyResult();
        }
        if (this.standardLinter != null) {
            const standardOptions : any = {filename: requestArguments.fileName};
            if (additionalOptions.fix) {
                standardOptions.fix = true;
            }
            return this.standardLinter.lintTextSync(requestArguments.content, standardOptions);
        }
        const config = cliEngine.getConfigForFile(requestArguments.fileName);
        if (!isFileKindAcceptedByConfig(config, requestArguments.fileKind)) {
            return createEmptyResult();
        }
        return cliEngine.executeOnText(requestArguments.content, requestArguments.fileName);
    }
}

function isFileKindAcceptedByConfig(config: Linter.Config, fileKind: FileKind): boolean {
    const plugins: string[] | null | undefined = (<any>config).plugins;

    function hasPlugin(toCheck: string): boolean {
        return Array.isArray(plugins) && plugins
            .filter(value => value == toCheck || value == "eslint-plugin-" + toCheck).length > 0;
    }

    function hasParser(parser: string): boolean {
        return (config.parser != undefined && config.parser != null && containsString(normalizePath(config.parser), parser))
          || (config.parserOptions != undefined && config.parserOptions != null
            && containsString(normalizePath(config.parserOptions["parser"]), parser))
    }

    if (fileKind === FileKind.ts) {
        return (
            // typescript plugin was later renamed to @typescript-eslint
            hasPlugin("typescript")
            || hasPlugin("@typescript-eslint")
            || hasParser("babel-eslint")
            || hasParser("@babel/eslint-parser")
            || hasParser("typescript-eslint-parser")
            || hasParser("@typescript-eslint/parser"))
    }
    if (fileKind === FileKind.html) {
        return hasPlugin("html")
    }
    if (fileKind === FileKind.vue) {
        return (
            //eslint-plugin-html used to process .vue files prior to v5
            hasPlugin("html") ||
            //eslint-plugin-vue in plugins used to be enough to process .vue files prior to v3
            hasPlugin("vue") ||
            hasParser("vue-eslint-parser")
        )
    }
    return true;
}

function containsString(src: string | null | undefined, toFind: string): boolean {
    return src != null && src.indexOf(toFind) >= 0
}

function normalizePath(eslintPackagePath: string | undefined): string | undefined {
    if (eslintPackagePath === undefined) return undefined
    if (eslintPackagePath.charAt(eslintPackagePath.length - 1) !== '/' &&
        eslintPackagePath.charAt(eslintPackagePath.length - 1) !== '\\') {
        eslintPackagePath = eslintPackagePath + '/';
    }
    return toUnixPathSeparators(eslintPackagePath);
}

function toUnixPathSeparators(path: string) {
    return path.split("\\").join("/");
}

function findESLintPackagePath(standardPackagePath: string, contextPath?: string): string {
    const resolvedStandardPackagePath = requireResolveInContext(standardPackagePath, contextPath);
    let requirePath = require.resolve("eslint", {paths: [resolvedStandardPackagePath]});
    requirePath = toUnixPathSeparators(requirePath);
    const eslintPackageStr = "/eslint/";
    const ind = requirePath.lastIndexOf(eslintPackageStr);
    if (ind < 0) {
        throw Error("Cannot find eslint package for " + requirePath);
    }
    return requirePath.substring(0, ind + eslintPackageStr.length);
}

function requireInContext(modulePathToRequire: string, contextPath?: string): any {
    const contextRequire = getContextRequire(contextPath);
    return contextRequire(modulePathToRequire);
}

function requireResolveInContext(modulePathToRequire: string, contextPath?: string): string {
    const contextRequire = getContextRequire(contextPath);
    return contextRequire.resolve(modulePathToRequire);
}

function getContextRequire(contextPath?: string): NodeRequire {
    if (contextPath != null) {
        const module = require('module')
        if (typeof module.createRequire === 'function') {
            // https://nodejs.org/api/module.html#module_module_createrequire_filename
            // Implemented in Yarn PnP: https://next.yarnpkg.com/advanced/pnpapi/#requiremodule
            return module.createRequire(contextPath);
        }
        // noinspection JSDeprecatedSymbols
        if (typeof module.createRequireFromPath === 'function') {
            // Use createRequireFromPath (a deprecated version of createRequire) to support Node.js 10.x
            // noinspection JSDeprecatedSymbols
            return module.createRequireFromPath(contextPath);
        }
        throw Error('Function module.createRequire is unavailable in Node.js ' + process.version +
            ', Node.js >= 12.2.0 is required')
    }
    return require;
}

function createEmptyResult(): CLIEngine.LintReport {
    return {
        results: [],
        warningCount: 0,
        fixableWarningCount: 0,
        fixableErrorCount: 0,
        errorCount: 0,
        usedDeprecatedRules: []
    };
}

// taken from private part of eslint(lib/cli.js), we need it here
/**
 * Translates the CLI options into the options expected by the CLIEngine.
 * @param {Object} cliOptions The CLI options to translate.
 * @returns {CLIEngine.Options} The options object for the CLIEngine.
 * @private
 */
function translateOptions(cliOptions: any): CLIEngine.Options {
    return {
        envs: cliOptions.env,
        extensions: cliOptions.ext,
        rules: cliOptions.rule,
        plugins: cliOptions.plugin,
        globals: cliOptions.global,
        ignore: cliOptions.ignore,
        ignorePath: cliOptions.ignorePath,
        ignorePattern: cliOptions.ignorePattern,
        configFile: cliOptions.config,
        rulePaths: cliOptions.rulesdir,
        useEslintrc: cliOptions.eslintrc,
        parser: cliOptions.parser,
        parserOptions: cliOptions.parserOptions,
        cache: cliOptions.cache,
        cacheFile: cliOptions.cacheFile,
        cacheLocation: cliOptions.cacheLocation,
        allowInlineConfig: cliOptions.inlineConfig,
        reportUnusedDisableDirectives: cliOptions.reportUnusedDisableDirectives,
        resolvePluginsRelativeTo: cliOptions.resolvePluginsRelativeTo
    };
}
