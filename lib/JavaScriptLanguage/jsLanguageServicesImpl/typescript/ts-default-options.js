"use strict";
exports.__esModule = true;
var logger_impl_1 = require("./logger-impl");
var out_path_process_1 = require("./out-path-process");
var DefaultOptionsHolder = /** @class */ (function () {
    function DefaultOptionsHolder(defaultOptions, ts_impl, pluginState) {
        this.openConfigProject = true;
        this.defaultOptions = defaultOptions;
        this.pluginState = pluginState;
        if (defaultOptions && defaultOptions.project) {
            var configOptions = this.getConfigOptions(ts_impl, defaultOptions);
            this.options = configOptions.commonDefaultOptions;
            this.configFileName = configOptions.configFileName;
        }
        else {
            this.configFileName = pluginState.defaultConfig;
            this.openConfigProject = false;
            if (defaultOptions) {
                this.options = defaultOptions;
            }
            else if (this.configFileName) {
                var parsedInfo = this.getConfigOptionsForFile(ts_impl, this.configFileName);
                this.options = parsedInfo != null ? parsedInfo.commonDefaultOptions : null;
            }
        }
        if (pluginState.hasManualParams) {
            if (pluginState.mainFilePath) {
                this.mainFile = pluginState.mainFilePath;
            }
            if (pluginState.outPath) {
                this.pathProcessor = out_path_process_1.getPathProcessor(ts_impl, pluginState);
            }
        }
    }
    DefaultOptionsHolder.prototype.isUseSingleInferredProject = function () {
        if (this.pluginState) {
            if (this.pluginState.isUseSingleInferredProject != null) {
                return this.pluginState.isUseSingleInferredProject;
            }
        }
        return true;
    };
    DefaultOptionsHolder.prototype.isOpenConfigProject = function () {
        return this.openConfigProject;
    };
    DefaultOptionsHolder.prototype.showParentConfigWarning = function () {
        return this.options == null && !(this.pluginState.hasManualParams);
    };
    DefaultOptionsHolder.prototype.hasConfig = function () {
        return this.configFileName != null;
    };
    DefaultOptionsHolder.prototype.watchConfig = function (callback, ts_impl) {
        var _this = this;
        if (!this.hasConfig())
            return;
        this.updateConfigCallback = callback;
        ts_impl.sys.watchFile(this.configFileName, function (file, isRemoved) {
            _this.refresh(ts_impl);
        });
    };
    DefaultOptionsHolder.prototype.refresh = function (ts_impl) {
        if (!this.hasConfig())
            return;
        try {
            var configOptions = this.defaultOptions ?
                this.getConfigOptions(ts_impl, this.defaultOptions) :
                this.getConfigOptionsForFile(ts_impl, this.configFileName);
            if (configOptions == null)
                return;
            this.options = configOptions.commonDefaultOptions;
            var rawOptions = this.options;
            if (rawOptions.compileOnSave == null) {
                rawOptions.compileOnSave = true;
            }
            if (rawOptions.compilerOptions) {
                rawOptions.compilerOptions.___processed_marker = true;
            }
            this.configFileName = configOptions.configFileName;
            if (this.updateConfigCallback) {
                this.updateConfigCallback();
            }
        }
        catch (err) {
            if (logger_impl_1.isLogEnabled)
                throw err;
            logger_impl_1.serverLogger("Error refreshing tsconfig.json " + this.configFileName + ' ' + err.message + '\n' + err.stack, true);
        }
    };
    DefaultOptionsHolder.prototype.getConfigOptionsForFile = function (ts_impl, configName) {
        try {
            var sys = ts_impl.sys;
            //ok, lets parse typescript config
            var cachedConfigFileText = sys.readFile(configName);
            if (cachedConfigFileText) {
                var result = ts_impl.parseConfigFileTextToJson(configName, cachedConfigFileText);
                var configObject = result.config;
                if (configObject) {
                    var cwd = sys.getCurrentDirectory();
                    var configParseResult = ts_impl.parseJsonConfigFileContent(configObject, sys, ts_impl.getNormalizedAbsolutePath(ts_impl.getDirectoryPath(configName), cwd), {}, ts_impl.getNormalizedAbsolutePath(configName, cwd));
                    if (configParseResult) {
                        return { commonDefaultOptions: configParseResult.options, configFileName: configName };
                    }
                    else if (logger_impl_1.isLogEnabled) {
                        throw new Error("Cannot parse config " + configName);
                    }
                }
            }
        }
        catch (err) {
            logger_impl_1.serverLogger("Error parsing tsconfig.json " + configName + ' ' + err.message + '\n' + err.stack, true);
        }
        return null;
    };
    DefaultOptionsHolder.prototype.getConfigOptions = function (ts_impl, commonDefaultOptions) {
        try {
            var sys = ts_impl.sys;
            var fileOrDirectory = ts_impl.normalizePath(commonDefaultOptions.project);
            var configFileName = null;
            if (!fileOrDirectory || sys.directoryExists(fileOrDirectory)) {
                configFileName = ts_impl.combinePaths(fileOrDirectory, "tsconfig.json");
                if (!sys.fileExists(configFileName)) {
                    if (logger_impl_1.isLogEnabled) {
                        throw new Error("Config file doesn't exist " + configFileName);
                    }
                    return { commonDefaultOptions: commonDefaultOptions };
                }
            }
            else {
                configFileName = fileOrDirectory;
                if (!sys.fileExists(configFileName)) {
                    if (logger_impl_1.isLogEnabled) {
                        throw new Error("Config file doesn't exist " + configFileName);
                    }
                    return { commonDefaultOptions: commonDefaultOptions };
                }
            }
            if (!configFileName && logger_impl_1.isLogEnabled) {
                throw new Error("Config file doesn't exist " + fileOrDirectory);
            }
            var candidate = this.getConfigOptionsForFile(ts_impl, configFileName);
            if (candidate != null)
                return candidate;
        }
        catch (err) {
            if (logger_impl_1.isLogEnabled) {
                throw err;
            }
        }
        return { commonDefaultOptions: commonDefaultOptions };
    };
    return DefaultOptionsHolder;
}());
exports.DefaultOptionsHolder = DefaultOptionsHolder;
