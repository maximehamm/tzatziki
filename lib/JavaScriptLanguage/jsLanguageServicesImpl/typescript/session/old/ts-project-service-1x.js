"use strict";
exports.__esModule = true;
var logger_impl_1 = require("../../logger-impl");
var util_1 = require("../../util");
function extendProjectService1x(TypeScriptProjectService, ts_impl, host, projectEmittedWithAllFiles, projectErrors, isVersionTypeScript15, isVersionTypeScript16, isVersionTypeScript17, commonDefaultOptions) {
    util_1.extendEx(TypeScriptProjectService, "fileDeletedInFilesystem", function (oldFunction, args) {
        var info = args[0];
        var filePath = ts_impl.normalizePath(info.fileName);
        var emittedWithAllFiles = projectEmittedWithAllFiles.value;
        for (var el in emittedWithAllFiles) {
            if (emittedWithAllFiles.hasOwnProperty(el)) {
                var holder = emittedWithAllFiles[el];
                if (holder) {
                    holder.resetForFile(filePath);
                }
            }
        }
        oldFunction.apply(this, args);
    });
    util_1.extendEx(TypeScriptProjectService, "createInferredProject", function (oldFunction, args) {
        var project = oldFunction.apply(this, args);
        if (commonDefaultOptions.options != null && project && project.compilerService) {
            var commonSettings = project.compilerService.settings;
            var res = {};
            if (commonSettings) {
                for (var id in commonSettings) {
                    res[id] = commonSettings[id];
                }
            }
            for (var id in commonDefaultOptions.options) {
                res[id] = commonDefaultOptions[id];
            }
            project.compilerService.setCompilerOptions(res);
        }
        return project;
    });
    util_1.extendEx(TypeScriptProjectService, "watchedProjectConfigFileChanged", function (oldFunction, args) {
        var project = args[0];
        var projectFilename = project.projectFilename;
        cleanCachedData(projectFilename);
        if (isVersionTypeScript15) {
            return;
        }
        oldFunction.apply(this, args);
        logger_impl_1.serverLogger("Watcher — project changed " + (projectFilename ? projectFilename : "unnamed"), true);
    });
    util_1.extendEx(TypeScriptProjectService, "configFileToProjectOptions", function (oldFunction, args) {
        var configFilename = args[0];
        var normalizedConfigName = ts_impl.normalizePath(configFilename);
        if (normalizedConfigName) {
            if (logger_impl_1.isLogEnabled) {
                logger_impl_1.serverLogger("Parse config normalized path " + normalizedConfigName);
            }
            var value = projectErrors.value;
            value[normalizedConfigName] = null;
        }
        if (isVersionTypeScript15 || isVersionTypeScript16 || isVersionTypeScript17) {
            var result = oldFunction.apply(this, args);
            setProjectLevelError(result, configFilename);
            return result;
        }
        var configFileToProjectOptions = configFileToProjectOptionsExt(configFilename);
        configFileToProjectOptions = copyOptionsWithResolvedFilesWithoutExtensions(host, configFileToProjectOptions, this, ts_impl);
        if (logger_impl_1.isLogEnabled) {
            logger_impl_1.serverLogger("Parse config result options: " + JSON.stringify(configFileToProjectOptions));
        }
        setProjectLevelError(configFileToProjectOptions, normalizedConfigName);
        return configFileToProjectOptions;
    });
    /**
     * copy of super#configFileToProjectOptions()
     */
    function configFileToProjectOptionsExt(configFilename) {
        if (logger_impl_1.isLogEnabled) {
            logger_impl_1.serverLogger("Parse config " + configFilename);
        }
        configFilename = ts_impl.normalizePath(configFilename);
        // file references will be relative to dirPath (or absolute)
        var dirPath = ts_impl.getDirectoryPath(configFilename);
        var contents = host.readFile(configFilename);
        var rawConfig = ts_impl.parseConfigFileTextToJson(configFilename, contents);
        if (rawConfig.error) {
            if (logger_impl_1.isLogEnabled) {
                logger_impl_1.serverLogger("Parse config error " + JSON.stringify(rawConfig.error));
            }
            return { succeeded: false, errors: [rawConfig.error] };
        }
        else {
            var parsedJsonConfig = rawConfig.config;
            var parsedCommandLine = ts_impl.parseJsonConfigFileContent(parsedJsonConfig, host, dirPath, /*existingOptions*/ {}, configFilename);
            if (parsedCommandLine.errors && (parsedCommandLine.errors.length > 0)) {
                var result = { succeeded: false, errors: parsedCommandLine.errors };
                // if (parsedCommandLine.fileNames && parsedCommandLine.options) {
                //     const projectOptions = this.createProjectOptions(parsedCommandLine, parsedJsonConfig);
                //     result.projectOptions = projectOptions;
                // }
                return result;
            }
            else {
                var projectOptions = createProjectOptionsForCommandLine(parsedCommandLine, parsedJsonConfig);
                return { succeeded: true, projectOptions: projectOptions };
            }
        }
    }
    function createProjectOptionsForCommandLine(parsedCommandLine, parsedJsonConfig) {
        var projectOptions = {
            files: parsedCommandLine.fileNames,
            compilerOptions: parsedCommandLine.options,
        };
        if (parsedCommandLine.wildcardDirectories) {
            projectOptions.wildcardDirectories = parsedCommandLine.wildcardDirectories;
        }
        if (parsedJsonConfig && parsedJsonConfig.compileOnSave === false) {
            projectOptions.compileOnSave = false;
        }
        return projectOptions;
    }
    function cleanCachedData(projectFilename) {
        if (projectFilename) {
            projectEmittedWithAllFiles.reset();
            projectErrors.reset();
        }
    }
    function setProjectLevelError(configFileToProjectOptions, normalizedConfigName) {
        if (configFileToProjectOptions.errors) {
            var errors = configFileToProjectOptions.errors;
            if (errors.length > 0) {
                var errorsForService = errors.map(function (el) {
                    return {
                        end: undefined,
                        start: undefined,
                        text: ts_impl.flattenDiagnosticMessageText(el.messageText, "\n")
                    };
                });
                var value = projectErrors.value;
                value[normalizedConfigName] = errorsForService;
                //back compatibility 1.8
                configFileToProjectOptions.error = { errorMsg: errorsForService[0].text };
            }
        }
        else if (configFileToProjectOptions.error) {
            var error = configFileToProjectOptions.error;
            var errorMessage = error.errorMsg ? error.errorMsg : "Error parsing tsconfig";
            var value = projectErrors.value;
            value[normalizedConfigName] = [{
                    text: errorMessage,
                    end: undefined,
                    start: undefined,
                }];
        }
    }
}
exports.extendProjectService1x = extendProjectService1x;
function copyOptionsWithResolvedFilesWithoutExtensions(host, configFileToProjectOptions, projectService, ts_impl) {
    function getBaseFileName(path) {
        if (path === undefined) {
            return undefined;
        }
        var i = path.lastIndexOf(ts_impl.directorySeparator);
        return i < 0 ? path : path.substring(i + 1);
    }
    if (!configFileToProjectOptions || !configFileToProjectOptions.projectOptions) {
        return configFileToProjectOptions;
    }
    var projectOptions = configFileToProjectOptions.projectOptions;
    var files = projectOptions.files;
    if (!files) {
        return configFileToProjectOptions;
    }
    var compilerOptions = projectOptions.compilerOptions;
    var extensions = ts_impl.getSupportedExtensions(compilerOptions);
    var newFiles = [];
    var hasOverrides = false;
    l: for (var _i = 0, files_1 = files; _i < files_1.length; _i++) {
        var file = files_1[_i];
        var fileName = getBaseFileName(file);
        for (var _a = 0, extensions_1 = extensions; _a < extensions_1.length; _a++) {
            var extension = extensions_1[_a];
            if (fileName.lastIndexOf(extension) > 0) {
                newFiles.push(file);
                continue l;
            }
        }
        for (var _b = 0, extensions_2 = extensions; _b < extensions_2.length; _b++) {
            var extension = extensions_2[_b];
            if (host.fileExists(file + extension)) {
                hasOverrides = true;
                newFiles.push(file + extension);
                continue l;
            }
        }
        newFiles.push(file);
    }
    if (!hasOverrides) {
        return configFileToProjectOptions;
    }
    var newOptions = {
        succeeded: configFileToProjectOptions.succeeded,
        projectOptions: {
            compilerOptions: compilerOptions,
            files: newFiles,
            wildcardDirectories: projectOptions.wildcardDirectories,
            compileOnSave: projectOptions.compileOnSave
        }
    };
    if (configFileToProjectOptions.error) {
        newOptions.error = configFileToProjectOptions.error;
    }
    if (configFileToProjectOptions.errors) {
        newOptions.errors = configFileToProjectOptions.errors;
    }
    return newOptions;
}
exports.copyOptionsWithResolvedFilesWithoutExtensions = copyOptionsWithResolvedFilesWithoutExtensions;
