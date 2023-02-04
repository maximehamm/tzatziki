"use strict";
var __extends = (this && this.__extends) || (function () {
    var extendStatics = function (d, b) {
        extendStatics = Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array && function (d, b) { d.__proto__ = b; }) ||
            function (d, b) { for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p]; };
        return extendStatics(d, b);
    };
    return function (d, b) {
        extendStatics(d, b);
        function __() { this.constructor = d; }
        d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
    };
})();
exports.__esModule = true;
var logger_impl_1 = require("./logger-impl");
var compile_info_holder_1 = require("./session/compile-info-holder");
function initCommandNames(TypeScriptCommandNames) {
    TypeScriptCommandNames.IDEChangeFiles = "ideChangeFiles";
    TypeScriptCommandNames.IDECompile = "ideCompile";
    TypeScriptCommandNames.IDEGetErrors = "ideGetErr";
    TypeScriptCommandNames.IDEGetAllErrors = "ideGetAllErr";
    TypeScriptCommandNames.IDECompletions = "ideCompletions";
    TypeScriptCommandNames.IDEComposite = "IDEComposite";
    TypeScriptCommandNames.IDEGetMainFileErrors = "ideGetMainFileErr";
    TypeScriptCommandNames.IDEGetProjectErrors = "ideGetProjectErr";
    TypeScriptCommandNames.IDEEmpty = "IDEEmpty";
    TypeScriptCommandNames.IDEGetProjectsInfo = "projectInfoIDE";
    if (TypeScriptCommandNames.ReloadProjects == undefined) {
        TypeScriptCommandNames.ReloadProjects = "reloadProjects";
    }
}
exports.initCommandNames = initCommandNames;
exports.DETAILED_COMPLETION_COUNT = 30;
exports.DETAILED_MAX_TIME = 150;
function isTypeScript15(ts_impl) {
    return checkVersion(ts_impl, "1.5");
}
exports.isTypeScript15 = isTypeScript15;
function isTypeScript16(ts_impl) {
    return checkVersion(ts_impl, "1.6");
}
exports.isTypeScript16 = isTypeScript16;
function isTypeScript17(ts_impl) {
    return checkVersion(ts_impl, "1.7");
}
exports.isTypeScript17 = isTypeScript17;
function isTypeScript20(ts_impl) {
    return checkVersion(ts_impl, "2.0");
}
exports.isTypeScript20 = isTypeScript20;
function checkVersion(ts_impl, versionText) {
    return ts_impl.version && (ts_impl.version == versionText || ts_impl.version.indexOf(versionText) == 0);
}
/**
 * Default tsserver implementation doesn't return response in most cases ("open", "close", etc.)
 * we want to override the behaviour and send empty-response holder
 */
exports.doneRequest = {
    responseRequired: true,
    response: "done"
};
var DiagnosticsContainer = /** @class */ (function () {
    function DiagnosticsContainer() {
        this.value = {};
    }
    DiagnosticsContainer.prototype.reset = function () {
        this.value = {};
    };
    return DiagnosticsContainer;
}());
exports.DiagnosticsContainer = DiagnosticsContainer;
function updateInferredProjectSettings(ts_impl, defaultOptionsHolder, projectService) {
    var serviceDefaults = projectService.getCompilerOptionsForInferredProjects();
    setDefaultOptions(serviceDefaults, defaultOptionsHolder, projectService);
    defaultOptionsHolder.watchConfig(function () {
        setDefaultOptions(serviceDefaults, defaultOptionsHolder, projectService);
    }, ts_impl);
    if (defaultOptionsHolder.configFileName && defaultOptionsHolder.isOpenConfigProject()) {
        logger_impl_1.serverLogger("Opening external project for config " + defaultOptionsHolder.configFileName, true);
        projectService.openExternalProject({
            projectFileName: defaultOptionsHolder.configFileName,
            rootFiles: [{ fileName: defaultOptionsHolder.configFileName }],
            options: {}
        });
    }
}
exports.updateInferredProjectSettings = updateInferredProjectSettings;
function setDefaultOptions(serviceDefaults, defaultOptionsHolder, projectService) {
    if (serviceDefaults === void 0) { serviceDefaults = {}; }
    var result = {};
    copyPropertiesInto(serviceDefaults, result);
    var compilerOptions = defaultOptionsHolder.options;
    if (compilerOptions && compilerOptions.compileOnSave == null) {
        result.compileOnSave = true;
    }
    copyPropertiesInto(compilerOptions, result);
    projectService.setCompilerOptionsForInferredProjects(result);
}
exports.setDefaultOptions = setDefaultOptions;
function copyPropertiesInto(fromObject, toObject) {
    for (var obj in fromObject) {
        if (fromObject.hasOwnProperty(obj)) {
            toObject[obj] = fromObject[obj];
        }
    }
}
exports.copyPropertiesInto = copyPropertiesInto;
function extendEx(ObjectToExtend, name, func) {
    var proto = ObjectToExtend.prototype;
    var oldFunction = proto[name];
    proto[name] = function () {
        return func.apply(this, [oldFunction, arguments]);
    };
}
exports.extendEx = extendEx;
function parseNumbersInVersion(version) {
    var result = [];
    var versions = version.split(".");
    for (var _i = 0, versions_1 = versions; _i < versions_1.length; _i++) {
        version = versions_1[_i];
        if (version == null || version === "") {
            break;
        }
        var currentNumber = Number(version);
        if (currentNumber == null || isNaN(currentNumber)) {
            break;
        }
        result = result.concat(currentNumber);
    }
    return result;
}
exports.parseNumbersInVersion = parseNumbersInVersion;
function isVersionMoreOrEqual(version) {
    var expected = [];
    for (var _i = 1; _i < arguments.length; _i++) {
        expected[_i - 1] = arguments[_i];
    }
    for (var i = 0; i < expected.length; i++) {
        var expectedNumber = expected[i];
        var currentNumber = version.length > i ? version[i] : 0;
        if (currentNumber < expectedNumber)
            return false;
        if (currentNumber > expectedNumber)
            return true;
    }
    return version.length >= expected.length;
}
exports.isVersionMoreOrEqual = isVersionMoreOrEqual;
function isFunctionKind(kind) {
    return kind == "method" ||
        kind == "local function" ||
        kind == "function" ||
        kind == "call" ||
        kind == "construct";
}
exports.isFunctionKind = isFunctionKind;
function getDefaultSessionClass(ts_impl, host, defaultOptionsHolder) {
    var DefaultSessionExtension = /** @class */ (function (_super) {
        __extends(DefaultSessionExtension, _super);
        function DefaultSessionExtension() {
            var _this = _super !== null && _super.apply(this, arguments) || this;
            _this._hasFirstMessage = false;
            return _this;
        }
        DefaultSessionExtension.prototype.onMessage = function (message) {
            if (!this._hasFirstMessage) {
                logger_impl_1.serverLogger("TypeScript service version: " + ts_impl.version, true);
                this.beforeFirstMessage();
                if (defaultOptionsHolder.options) {
                    if (defaultOptionsHolder.configFileName) {
                        logger_impl_1.serverLogger("Loaded default config: " + defaultOptionsHolder.configFileName);
                    }
                    logger_impl_1.serverLogger("Default service options: " + JSON.stringify(defaultOptionsHolder.options), true);
                }
                else {
                    logger_impl_1.serverLogger("Use Single Inferred Project: " + defaultOptionsHolder.isUseSingleInferredProject(), true);
                }
                this._hasFirstMessage = true;
            }
            _super.prototype.onMessage.call(this, message);
        };
        DefaultSessionExtension.prototype.beforeFirstMessage = function () {
        };
        DefaultSessionExtension.prototype.getFileWrite = function (projectFilename, outFiles, realWriteFile, contentRoot, sourceRoot) {
            return function (fileName, data, writeByteOrderMark, onError, sourceFiles) {
                var normalizedName = fileName;
                try {
                    normalizedName = normalizePathIfNeed(ts_impl.normalizePath(fileName), projectFilename);
                    if (defaultOptionsHolder.pathProcessor) {
                        normalizedName = fixNameWithProcessor(normalizedName, onError, contentRoot, sourceRoot);
                    }
                    ensureDirectoriesExist(ts_impl.getDirectoryPath(normalizedName));
                }
                catch (e) {
                    if (logger_impl_1.isLogEnabled)
                        throw e;
                    logger_impl_1.serverLogger("Error while process write file " + e.stack, true);
                }
                if (logger_impl_1.isLogEnabled) {
                    logger_impl_1.serverLogger("Compile write file: " + fileName, true);
                    logger_impl_1.serverLogger("Compile write file (normalized): " + normalizedName);
                }
                outFiles.push(normalizedName);
                return realWriteFile(normalizedName, data, writeByteOrderMark, onError, sourceFiles);
            };
        };
        DefaultSessionExtension.prototype.getCompileInfo = function (req) {
            return {};
        };
        DefaultSessionExtension.prototype.compileFileEx = function (req) {
            if (!req.file && !req.projectFileName) {
                logger_impl_1.serverLogger("Compile: Empty compile request", true);
                return exports.doneRequest;
            }
            var compileInfo = this.getCompileInfo(req);
            try {
                var isIncludeErrors = req.includeErrors;
                if (!compileInfo.project || !compileInfo.isCompilingRequired) {
                    if (!compileInfo.isCompilingRequired) {
                        logger_impl_1.serverLogger("Compile: skip compileOnSave = false", true);
                    }
                    if (!compileInfo.project) {
                        if (logger_impl_1.isLogEnabled)
                            throw new Error("cannot find project");
                        logger_impl_1.serverLogger("Compile: can't find project: shouldn't be happened", true);
                    }
                    return exports.doneRequest;
                }
                var compileInfoHolder_1 = null;
                if (compileInfo.projectName) {
                    compileInfoHolder_1 = compile_info_holder_1.projectEmittedWithAllFiles.getOrCreateCompileInfoHolder(compileInfo.projectName, compileInfo.projectPath);
                }
                var rawSourceFiles = compileInfo.getSourceFiles();
                var includeErrorsAndProcessedFiles_1 = isIncludeErrors;
                var processedFiles_1 = [];
                var toUpdateFiles_1 = [];
                var diagnostics_1 = includeErrorsAndProcessedFiles_1 ? [] : undefined;
                rawSourceFiles.forEach(function (val) {
                    if (includeErrorsAndProcessedFiles_1) {
                        processedFiles_1.push(val.fileName);
                    }
                    if (!compileInfoHolder_1 || compileInfoHolder_1.checkUpdateAndAddToCache(val, ts_impl)) {
                        toUpdateFiles_1.push(val);
                    }
                });
                var useOutFile = compileInfo.projectUsesOutFile;
                if (toUpdateFiles_1.length > 0) {
                    if (toUpdateFiles_1.length == rawSourceFiles.length || useOutFile) {
                        var emitDiagnostics = compileInfo.emit();
                        if (includeErrorsAndProcessedFiles_1 && emitDiagnostics && emitDiagnostics.length > 0) {
                            diagnostics_1 = diagnostics_1.concat(emitDiagnostics);
                        }
                    }
                    else {
                        toUpdateFiles_1.forEach(function (el) {
                            var emitDiagnostics = compileInfo.emit(el);
                            if (includeErrorsAndProcessedFiles_1 && emitDiagnostics && emitDiagnostics.length > 0) {
                                diagnostics_1 = diagnostics_1.concat(emitDiagnostics);
                            }
                        });
                    }
                }
                else {
                    logger_impl_1.serverLogger("Compile: No files to update. Source files count: " + rawSourceFiles.length);
                }
                if (includeErrorsAndProcessedFiles_1) {
                    diagnostics_1 = diagnostics_1.concat(compileInfo.getDiagnostics());
                }
                return { response: { generatedFiles: compileInfo.getOutFiles(), infos: diagnostics_1, processedFiles: processedFiles_1 }, responseRequired: true };
            }
            finally {
                if (compileInfo.postProcess) {
                    compileInfo.postProcess();
                }
            }
        };
        return DefaultSessionExtension;
    }(ts_impl.server.Session));
    function normalizePathIfNeed(file, projectFilename) {
        if (0 === ts_impl.getRootLength(file)) {
            var contextDir = void 0;
            if (projectFilename) {
                contextDir = ts_impl.getDirectoryPath(projectFilename);
            }
            if (!contextDir) {
                contextDir = host.getCurrentDirectory();
            }
            return ts_impl.getNormalizedAbsolutePath(file, contextDir);
        }
        return file;
    }
    function ensureDirectoriesExist(directoryPath) {
        if (directoryPath.length > ts_impl.getRootLength(directoryPath) && !host.directoryExists(directoryPath)) {
            var parentDirectory = ts_impl.getDirectoryPath(directoryPath);
            ensureDirectoriesExist(parentDirectory);
            host.createDirectory(directoryPath);
        }
    }
    function fixNameWithProcessor(filename, onError, contentRoot, sourceRoot) {
        if (defaultOptionsHolder.pathProcessor) {
            filename = defaultOptionsHolder.pathProcessor.getExpandedPath(filename, contentRoot, sourceRoot, onError);
        }
        return filename;
    }
    return DefaultSessionExtension;
}
exports.getDefaultSessionClass = getDefaultSessionClass;
