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
var ts_project_service_21_1 = require("./session/old/ts-project-service-21");
var util_1 = require("./util");
var logger_impl_1 = require("./logger-impl");
var compile_info_holder_1 = require("./session/compile-info-holder");
function createSessionLatestClass(TypeScriptProjectService, TypeScriptCommandNames, host, ts_impl, defaultOptionsHolder) {
    ts_project_service_21_1.extendProjectService21(TypeScriptProjectService, ts_impl, host);
    var DefaultSessionClass = util_1.getDefaultSessionClass(ts_impl, host, defaultOptionsHolder);
    var SessionLatest = /** @class */ (function (_super) {
        __extends(SessionLatest, _super);
        function SessionLatest() {
            return _super !== null && _super.apply(this, arguments) || this;
        }
        SessionLatest.prototype.executeCommand = function (request) {
            var command = request.command;
            if (TypeScriptCommandNames.IDEEmpty == command)
                return util_1.doneRequest;
            if (TypeScriptCommandNames.IDECompile == command) {
                return this.compileFileEx(request.arguments);
            }
            if (TypeScriptCommandNames.ReloadProjects == command) {
                compile_info_holder_1.projectEmittedWithAllFiles.reset();
                return _super.prototype.executeCommand.call(this, request);
            }
            if (TypeScriptCommandNames.IDEGetProjectsInfo == command) {
                return this.getProjectsInfoIDE();
            }
            if (TypeScriptCommandNames.Reload == command) {
                var reloadArguments = request.arguments;
                this.reloadFile(reloadArguments);
                return util_1.doneRequest;
            }
            if (TypeScriptCommandNames.OpenExternalProject == command && (!ts_impl.server || !(ts_impl.server.getBaseConfigFileName))) {
                return this.handleOpenExternalProjectForOldApi(request);
            }
            if (command == "suggestionDiagnosticsSync" && !TypeScriptCommandNames.SuggestionDiagnosticsSync) {
                return { response: { infos: [] }, responseRequired: true };
            }
            return _super.prototype.executeCommand.call(this, request);
        };
        SessionLatest.prototype.handleOpenExternalProjectForOldApi = function (request) {
            var oldBase = ts_impl.getBaseFileName;
            if (!oldBase) { //nothing
                return util_1.doneRequest;
            }
            ts_impl.getBaseFileName = function () {
                var result = oldBase.apply(this, arguments);
                if (result.endsWith(".json")) {
                    //hack. We have to change behaviour getBaseFileName(normalized) for config candidates
                    return "tsconfig.json";
                }
                return result;
            };
            try {
                var result = _super.prototype.executeCommand.call(this, request);
                return result;
            }
            catch (_a) {
                //restore
                ts_impl.getBaseFileName = oldBase;
            }
            return util_1.doneRequest;
        };
        SessionLatest.prototype.beforeFirstMessage = function () {
            if (defaultOptionsHolder.options != null) {
                util_1.updateInferredProjectSettings(ts_impl, defaultOptionsHolder, this.projectService);
            }
            _super.prototype.beforeFirstMessage.call(this);
        };
        SessionLatest.prototype.logError = function (err, cmd) {
            _super.prototype.logError.call(this, err, cmd);
            if (logger_impl_1.isLogEnabled) {
                logger_impl_1.serverLogger("Internal error:" + err + "\n " + err.stack);
            }
        };
        SessionLatest.prototype.getProjectsInfoIDE = function () {
            var _this = this;
            logger_impl_1.serverLogger("Getting project information");
            var infos = [];
            var configuredProjects = this.projectService.configuredProjects;
            if (Array.isArray(configuredProjects)) {
                for (var _i = 0, configuredProjects_1 = configuredProjects; _i < configuredProjects_1.length; _i++) {
                    var configuredProject = configuredProjects_1[_i];
                    logger_impl_1.serverLogger("Process " + configuredProject.getProjectName());
                    this.addProjectInfo(configuredProject, infos);
                }
            }
            else {
                configuredProjects.forEach(function (configuredProject, key) {
                    logger_impl_1.serverLogger("Process " + configuredProject.getProjectName());
                    _this.addProjectInfo(configuredProject, infos);
                });
            }
            for (var _a = 0, _b = this.projectService.inferredProjects; _a < _b.length; _a++) {
                var inferredProject = _b[_a];
                logger_impl_1.serverLogger("Process " + inferredProject.getProjectName());
                this.addProjectInfo(inferredProject, infos);
            }
            for (var _c = 0, _d = this.projectService.externalProjects; _c < _d.length; _c++) {
                var externalProject = _d[_c];
                logger_impl_1.serverLogger("Process " + externalProject.getProjectName());
                this.addProjectInfo(externalProject, infos);
            }
            return { responseRequired: true, response: infos };
        };
        SessionLatest.prototype.addProjectInfo = function (project, infos) {
            var name = project.getProjectName();
            var fileNames = project.getFileNames(false);
            logger_impl_1.serverLogger("Project " + project.getProjectName() + " files count: " + fileNames.length);
            var regularFileInfos = fileNames.map(function (el) {
                try {
                    var info = project.getScriptInfo(el);
                    return {
                        fileName: el,
                        isOpen: info.isScriptOpen(),
                        isExternal: false
                    };
                }
                catch (e) {
                    return {
                        fileName: el,
                        isOpen: false,
                        isExternal: false
                    };
                }
            });
            var externalFileInfos = project.getExternalFiles().map(function (el) {
                try {
                    var info = project.getScriptInfo(el);
                    return {
                        fileName: el,
                        isOpen: info.isScriptOpen(),
                        isExternal: true
                    };
                }
                catch (e) {
                    return {
                        fileName: el,
                        isOpen: false,
                        isExternal: true
                    };
                }
            });
            infos.push({
                projectName: name,
                fileInfos: regularFileInfos.concat(externalFileInfos)
            });
        };
        SessionLatest.prototype.getCompileInfo = function (req) {
            var _this = this;
            var findInfo = this.getProjectForCompile(req);
            if (!findInfo || !findInfo.project)
                return {};
            var file = req.file;
            var project = findInfo.project;
            var uniqueName = project.getProjectName();
            var configFilePath = null;
            var projectKind = project.projectKind;
            var compile = req.force || this.getCompileOnSave(project);
            if (!compile)
                return { project: project, isCompilingRequired: false };
            if (projectKind == ts_impl.server.ProjectKind.Configured) {
                configFilePath = project.getConfigFilePath();
            }
            else if (projectKind == ts_impl.server.ProjectKind.External) {
                configFilePath = uniqueName;
            }
            var languageService = project.getLanguageService(true);
            if (file && !req.force) {
                var scriptInfo = this.projectService.getScriptInfo(file);
                try {
                    if (scriptInfo) {
                        if (!scriptInfo.isScriptOpen()) {
                            logger_impl_1.serverLogger("Compile: Reload file content " + file);
                            scriptInfo.reloadFromFile();
                            this.projectService.reloadProjects();
                        }
                    }
                    else {
                        logger_impl_1.serverLogger("Compile: Cannot find  script info for: " + file);
                    }
                }
                catch (e) {
                    if (logger_impl_1.isLogEnabled)
                        throw e;
                    logger_impl_1.serverLogger("Compile: Cannot reload content: " + e.message + ", stack " + e.stack, true);
                }
            }
            var oldNewLine = host.newLine;
            var compilerOptions = this.getCompilerOptionsEx(project);
            var newLineOwner = host;
            if (ts_impl.getNewLineCharacter) {
                var newValue = ts_impl.getNewLineCharacter(compilerOptions ? compilerOptions : {});
                if (project && project.directoryStructureHost) {
                    project.directoryStructureHost.newLine = newValue;
                }
                newLineOwner.newLine = newValue;
            }
            else {
                logger_impl_1.serverLogger("Compile: ERROR API was changed cannot find ts.getNewLineCharacter", true);
            }
            var program = languageService.getProgram();
            var useOutFile = !!compilerOptions.outFile || !!compilerOptions.out;
            var outFiles = [];
            var fileWriteCallback = this.getFileWrite(configFilePath, outFiles, host.writeFile, req.contentRootForMacro, req.sourceRootForMacro);
            return {
                project: project,
                isCompilingRequired: true,
                projectPath: configFilePath,
                projectName: uniqueName,
                projectUsesOutFile: useOutFile,
                postProcess: function () {
                    newLineOwner.newLine = oldNewLine;
                    if (findInfo.wasOpened) {
                        logger_impl_1.serverLogger("Close client file " + file);
                        _this.projectService.closeClientFile(file);
                    }
                },
                getDiagnostics: function () {
                    if (file) {
                        return _this.getDiagnosticsForFile(file, configFilePath);
                    }
                    var fileNames = project.getFileNames(true);
                    var result = [];
                    fileNames.forEach(function (fileName) {
                        result = result.concat(_this.getDiagnosticsForFile(fileName, configFilePath));
                    });
                    return result;
                },
                emit: function (el) {
                    program.emit(el, fileWriteCallback);
                    return [];
                },
                getOutFiles: function () {
                    return outFiles;
                },
                getSourceFiles: function () {
                    return program.getSourceFiles();
                },
            };
        };
        SessionLatest.prototype.getCompilerOptionsEx = function (project) {
            //ts2.6rc getCompilationSettings
            var compilerOptions = project.getCompilerOptions ? project.getCompilerOptions() : project.getCompilationSettings();
            return compilerOptions;
        };
        SessionLatest.prototype.getDiagnosticsForFile = function (file, configFilePath) {
            if (logger_impl_1.isLogEnabled) {
                logger_impl_1.serverLogger("Get diagnostics for " + file);
            }
            var fileArgs = {
                file: file,
                projectFileName: configFilePath == null ? undefined : configFilePath
            };
            var results = [];
            {
                var responseSemantic = this.executeCommand({
                    command: TypeScriptCommandNames.SemanticDiagnosticsSync,
                    type: "request",
                    seq: 0,
                    arguments: fileArgs
                });
                var resultSemantic = responseSemantic.response;
                if (resultSemantic && resultSemantic.length > 0)
                    results = results.concat({
                        file: file,
                        diagnostics: resultSemantic
                    });
            }
            {
                var responseSyntax = this.executeCommand({
                    command: TypeScriptCommandNames.SyntacticDiagnosticsSync,
                    type: "request",
                    seq: 0,
                    arguments: fileArgs
                });
                var resultSyntax = responseSyntax.response;
                if (resultSyntax && resultSyntax.length > 0)
                    results = results.concat({
                        file: file,
                        diagnostics: resultSyntax
                    });
            }
            return results;
        };
        SessionLatest.prototype.getCompileOnSave = function (project) {
            var projectKind = project.projectKind;
            if (projectKind == ts_impl.server.ProjectKind.Configured ||
                projectKind == ts_impl.server.ProjectKind.External) {
                return project.compileOnSaveEnabled;
            }
            if (this.getCompilerOptionsEx(project).compileOnSave === false) {
                return false;
            }
            return true;
        };
        SessionLatest.prototype.getProjectForCompile = function (req) {
            var projectFileName = req.projectFileName;
            if (projectFileName != null) {
                var normalizedPath = ts_impl.normalizePath(projectFileName);
                var project = this.getProjectForConfigPath(normalizedPath);
                if (project)
                    return { project: project };
                this.projectService.openExternalProject({
                    projectFileName: normalizedPath,
                    rootFiles: [{ fileName: normalizedPath }],
                    options: {}
                });
                var externalProject = this.getProjectForConfigPath(normalizedPath);
                if (externalProject != null) {
                    logger_impl_1.serverLogger("External Project(2) was created for compiling", true);
                    return { project: externalProject };
                }
                else {
                    logger_impl_1.serverLogger("Error while creating External Project(2) for compiling", true);
                }
            }
            else {
                var normalizedPath = ts_impl.normalizePath(req.file);
                try {
                    var project = this.getProjectForFilePath(normalizedPath);
                    if (project) {
                        return { project: project };
                    }
                }
                catch (e) {
                    //no project
                }
                var openClientFile = this.projectService.openClientFileWithNormalizedPath(normalizedPath);
                logger_impl_1.serverLogger("Open client file: " + openClientFile);
                try {
                    var project = this.getProjectForFilePath(normalizedPath);
                    if (project) {
                        return { project: project, wasOpened: true };
                    }
                }
                catch (e) {
                    //no project
                }
            }
            return null;
        };
        SessionLatest.prototype.getProjectForConfigPath = function (normalizedPath) {
            return this.projectService.findProject(normalizedPath);
        };
        SessionLatest.prototype.getProjectForFilePath = function (normalizedPath) {
            return this.projectService.getDefaultProjectForFile(normalizedPath, true);
        };
        SessionLatest.prototype.reloadFile = function (reloadArguments) {
            try {
                var file = reloadArguments.file;
                if (!file)
                    return;
                var scriptInfo = this.projectService.getScriptInfo(file);
                if (!scriptInfo)
                    return;
                scriptInfo.reloadFromFile();
            }
            catch (e) {
                if (logger_impl_1.isLogEnabled)
                    throw e;
                this.logError(e, "reload");
            }
        };
        return SessionLatest;
    }(DefaultSessionClass));
    return SessionLatest;
}
exports.createSessionLatestClass = createSessionLatestClass;
