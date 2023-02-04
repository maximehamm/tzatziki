"use strict";
var __assign = (this && this.__assign) || function () {
    __assign = Object.assign || function(t) {
        for (var s, i = 1, n = arguments.length; i < n; i++) {
            s = arguments[i];
            for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p))
                t[p] = s[p];
        }
        return t;
    };
    return __assign.apply(this, arguments);
};
exports.__esModule = true;
exports.ESLintPlugin = void 0;
var ESLintResponse = /** @class */ (function () {
    function ESLintResponse(request_seq, command) {
        this.request_seq = request_seq;
        this.command = command;
    }
    return ESLintResponse;
}());
/**
 * See com.intellij.lang.javascript.linter.eslint.EslintUtil.FileKind
 */
var FileKind;
(function (FileKind) {
    FileKind["ts"] = "ts";
    FileKind["html"] = "html";
    FileKind["vue"] = "vue";
    FileKind["jsAndOther"] = "js_and_other";
})(FileKind || (FileKind = {}));
var ESLintPlugin = /** @class */ (function () {
    function ESLintPlugin(state) {
        this.includeSourceText = state.includeSourceText;
        this.additionalRulesDirectory = state.additionalRootDirectory;
        var eslintPackagePath;
        if (state.standardPackagePath != null) {
            var standardPackagePath = state.standardPackagePath;
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
    ESLintPlugin.prototype.onMessage = function (p, writer) {
        var request = JSON.parse(p);
        var response = new ESLintResponse(request.seq, request.command);
        try {
            if (request.command === ESLintPlugin.GetErrors) {
                response.body = this.filterSourceIfNeeded(this.getErrors(request.arguments));
            }
            else if (request.command === ESLintPlugin.FixErrors) {
                response.body = this.filterSourceIfNeeded(this.fixErrors(request.arguments));
            }
            else {
                response.error = "Unknown command: " + request.command;
            }
        }
        catch (e) {
            response.isNoConfigFile = "no-config-found" === e.messageTemplate
                || (e.message && containsString(e.message.toString(), "No ESLint configuration found"));
            response.error = e.toString() + "\n\n" + e.stack;
        }
        writer.write(JSON.stringify(response));
    };
    ESLintPlugin.prototype.filterSourceIfNeeded = function (body) {
        if (!this.includeSourceText) {
            body.results.forEach(function (value) {
                delete value.source;
                value.messages.forEach(function (msg) { return delete msg.source; });
            });
        }
        return body;
    };
    ESLintPlugin.prototype.getErrors = function (getErrorsArguments) {
        return this.invokeESLint(getErrorsArguments);
    };
    ESLintPlugin.prototype.fixErrors = function (fixErrorsArguments) {
        return this.invokeESLint(fixErrorsArguments, { fix: true });
    };
    ESLintPlugin.prototype.invokeESLint = function (requestArguments, additionalOptions) {
        if (additionalOptions === void 0) { additionalOptions = {}; }
        var parsedCommandLineOptions = translateOptions(this.options.parse(requestArguments.extraOptions || ""));
        var options = __assign(__assign({}, parsedCommandLineOptions), additionalOptions);
        options.ignorePath = requestArguments.ignoreFilePath;
        if (requestArguments.configPath != null) {
            options.configFile = requestArguments.configPath;
        }
        if (this.additionalRulesDirectory != null && this.additionalRulesDirectory.length > 0) {
            if (options.rulePaths == null) {
                options.rulePaths = [this.additionalRulesDirectory];
            }
            else {
                options.rulePaths.push(this.additionalRulesDirectory);
            }
        }
        var cliEngine = new this.cliEngineCtor(options);
        if (cliEngine.isPathIgnored(requestArguments.fileName)) {
            return createEmptyResult();
        }
        if (this.standardLinter != null) {
            var standardOptions = { filename: requestArguments.fileName };
            if (additionalOptions.fix) {
                standardOptions.fix = true;
            }
            return this.standardLinter.lintTextSync(requestArguments.content, standardOptions);
        }
        var config = cliEngine.getConfigForFile(requestArguments.fileName);
        if (!isFileKindAcceptedByConfig(config, requestArguments.fileKind)) {
            return createEmptyResult();
        }
        return cliEngine.executeOnText(requestArguments.content, requestArguments.fileName);
    };
    ESLintPlugin.GetErrors = "GetErrors";
    ESLintPlugin.FixErrors = "FixErrors";
    return ESLintPlugin;
}());
exports.ESLintPlugin = ESLintPlugin;
function isFileKindAcceptedByConfig(config, fileKind) {
    var plugins = config.plugins;
    function hasPlugin(toCheck) {
        return Array.isArray(plugins) && plugins
            .filter(function (value) { return value == toCheck || value == "eslint-plugin-" + toCheck; }).length > 0;
    }
    function hasParser(parser) {
        return (config.parser != undefined && config.parser != null && containsString(normalizePath(config.parser), parser))
            || (config.parserOptions != undefined && config.parserOptions != null
                && containsString(normalizePath(config.parserOptions["parser"]), parser));
    }
    if (fileKind === FileKind.ts) {
        return (
        // typescript plugin was later renamed to @typescript-eslint
        hasPlugin("typescript")
            || hasPlugin("@typescript-eslint")
            || hasParser("babel-eslint")
            || hasParser("@babel/eslint-parser")
            || hasParser("typescript-eslint-parser")
            || hasParser("@typescript-eslint/parser"));
    }
    if (fileKind === FileKind.html) {
        return hasPlugin("html");
    }
    if (fileKind === FileKind.vue) {
        return (
        //eslint-plugin-html used to process .vue files prior to v5
        hasPlugin("html") ||
            //eslint-plugin-vue in plugins used to be enough to process .vue files prior to v3
            hasPlugin("vue") ||
            hasParser("vue-eslint-parser"));
    }
    return true;
}
function containsString(src, toFind) {
    return src != null && src.indexOf(toFind) >= 0;
}
function normalizePath(eslintPackagePath) {
    if (eslintPackagePath === undefined)
        return undefined;
    if (eslintPackagePath.charAt(eslintPackagePath.length - 1) !== '/' &&
        eslintPackagePath.charAt(eslintPackagePath.length - 1) !== '\\') {
        eslintPackagePath = eslintPackagePath + '/';
    }
    return toUnixPathSeparators(eslintPackagePath);
}
function toUnixPathSeparators(path) {
    return path.split("\\").join("/");
}
function findESLintPackagePath(standardPackagePath, contextPath) {
    var resolvedStandardPackagePath = requireResolveInContext(standardPackagePath, contextPath);
    var requirePath = require.resolve("eslint", { paths: [resolvedStandardPackagePath] });
    requirePath = toUnixPathSeparators(requirePath);
    var eslintPackageStr = "/eslint/";
    var ind = requirePath.lastIndexOf(eslintPackageStr);
    if (ind < 0) {
        throw Error("Cannot find eslint package for " + requirePath);
    }
    return requirePath.substring(0, ind + eslintPackageStr.length);
}
function requireInContext(modulePathToRequire, contextPath) {
    var contextRequire = getContextRequire(contextPath);
    return contextRequire(modulePathToRequire);
}
function requireResolveInContext(modulePathToRequire, contextPath) {
    var contextRequire = getContextRequire(contextPath);
    return contextRequire.resolve(modulePathToRequire);
}
function getContextRequire(contextPath) {
    if (contextPath != null) {
        var module_1 = require('module');
        if (typeof module_1.createRequire === 'function') {
            // https://nodejs.org/api/module.html#module_module_createrequire_filename
            // Implemented in Yarn PnP: https://next.yarnpkg.com/advanced/pnpapi/#requiremodule
            return module_1.createRequire(contextPath);
        }
        // noinspection JSDeprecatedSymbols
        if (typeof module_1.createRequireFromPath === 'function') {
            // Use createRequireFromPath (a deprecated version of createRequire) to support Node.js 10.x
            // noinspection JSDeprecatedSymbols
            return module_1.createRequireFromPath(contextPath);
        }
        throw Error('Function module.createRequire is unavailable in Node.js ' + process.version +
            ', Node.js >= 12.2.0 is required');
    }
    return require;
}
function createEmptyResult() {
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
function translateOptions(cliOptions) {
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
//# sourceMappingURL=eslint-plugin.js.map