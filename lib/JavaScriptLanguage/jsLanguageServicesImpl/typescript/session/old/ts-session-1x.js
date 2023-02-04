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
var ts15impl_1 = require("./ts15impl");
var util_1 = require("../../util");
var logger_impl_1 = require("../../logger-impl");
var ts_project_service_1x_1 = require("./ts-project-service-1x");
function extendSessionClass1x(TypeScriptSession, TypeScriptProjectService, TypeScriptCommandNames, host, ts_impl, defaultOptionsHolder, projectEmittedWithAllFiles) {
    var projectErrors = new util_1.DiagnosticsContainer();
    var isVersionTypeScript15 = util_1.isTypeScript15(ts_impl);
    var isVersionTypeScript16 = util_1.isTypeScript16(ts_impl);
    var isVersionTypeScript17 = util_1.isTypeScript17(ts_impl);
    if (isVersionTypeScript15) {
        ts15impl_1.setGetFileNames(ts_impl.server.Project);
    }
    ts_project_service_1x_1.extendProjectService1x(TypeScriptProjectService, ts_impl, host, projectEmittedWithAllFiles, projectErrors, isVersionTypeScript15, isVersionTypeScript16, isVersionTypeScript17, defaultOptionsHolder);
    var Session1x = /** @class */ (function (_super) {
        __extends(Session1x, _super);
        function Session1x(byteLength, hrtime, logger) {
            var _this = this;
            if (isVersionTypeScript15) {
                _this = _super.call(this, host, logger, undefined, undefined) || this;
            }
            else {
                _this = _super.call(this, host, byteLength, hrtime, logger) || this;
            }
            return _this;
        }
        Session1x.prototype.reloadFileFromDisk = function (info) {
            info.svc.reloadFromFile(info.fileName);
        };
        Session1x.prototype.getScriptInfo = function (projectService, fileName) {
            return projectService.getScriptInfo(fileName);
        };
        Session1x.prototype.afterCompileProcess = function (project, requestedFile, wasOpened) {
        };
        Session1x.prototype.getProjectName = function (project) {
            return project.projectFilename;
        };
        Session1x.prototype.getProjectConfigPathEx = function (project) {
            return project.projectFilename;
        };
        Session1x.prototype.positionToLineOffset = function (project, fileName, position) {
            return project.compilerService.host.positionToLineOffset(fileName, position);
        };
        Session1x.prototype.containsFileEx = function (project, file, reqOpen) {
            return project.getSourceFileFromName(file, reqOpen) != null;
        };
        Session1x.prototype.lineOffsetToPosition = function (project, fileName, line, offset) {
            return project.compilerService.host.lineOffsetToPosition(fileName, line, offset);
        };
        Session1x.prototype.getLanguageService = function (project) {
            return project.compilerService.languageService;
        };
        Session1x.prototype.beforeFirstMessage = function () {
            _super.prototype.beforeFirstMessage.call(this);
        };
        Session1x.prototype.tsVersion = function () {
            return "2.0.0";
        };
        Session1x.prototype.onMessage = function (message) {
            if (isVersionTypeScript15) {
                if (!ts15impl_1.onMessage15(this, message)) {
                    _super.prototype.onMessage.call(this, message);
                }
                return;
            }
            _super.prototype.onMessage.call(this, message);
        };
        Session1x.prototype.executeCommand = function (request) {
            var startTime = Date.now();
            var command = request.command;
            try {
                if (TypeScriptCommandNames.Open == command) {
                    //use own implementation
                    var openArgs = request.arguments;
                    this.openClientFileEx(openArgs);
                    return util_1.doneRequest;
                }
                else if (TypeScriptCommandNames.ReloadProjects == command) {
                    projectEmittedWithAllFiles.reset();
                    if (isVersionTypeScript15)
                        return ts15impl_1.reload15(this, ts_impl);
                    var requestWithConfigArgs = request.arguments;
                    if (requestWithConfigArgs.projectFileName) {
                        var configFileName = ts_impl.normalizePath(requestWithConfigArgs.projectFileName);
                        var project = this.projectService.findConfiguredProjectByConfigFile(configFileName);
                        if (project != null) {
                            this.projectService.updateConfiguredProject(project);
                        }
                    }
                    this.refreshStructureEx();
                    return util_1.doneRequest;
                }
                else if (TypeScriptCommandNames.IDEChangeFiles == command) {
                    var updateFilesArgs = request.arguments;
                    return this.updateFilesEx(updateFilesArgs);
                }
                else if (TypeScriptCommandNames.IDECompile == command) {
                    var fileArgs = request.arguments;
                    return this.compileFileEx(fileArgs);
                }
                else if (TypeScriptCommandNames.Close == command) {
                    if (isVersionTypeScript15) {
                        ts15impl_1.close15(this, request);
                        return util_1.doneRequest;
                    }
                    _super.prototype.executeCommand.call(this, request);
                    return util_1.doneRequest;
                }
                else if ("syntacticDiagnosticsSync" === command) {
                    return { response: { infos: [] }, responseRequired: true };
                }
                else if ("suggestionDiagnosticsSync" === command) {
                    return { response: { infos: [] }, responseRequired: true };
                }
                else if ("semanticDiagnosticsSync" === command) {
                    var args = request.arguments;
                    return { response: { infos: this.getDiagnosticsEx([args.file], null, false) }, responseRequired: true };
                }
                else if (TypeScriptCommandNames.IDEGetErrors == command) {
                    var args = request.arguments;
                    return { response: { infos: this.getDiagnosticsEx(args.files) }, responseRequired: true };
                }
                else if (TypeScriptCommandNames.IDEGetMainFileErrors == command) {
                    var args = request.arguments;
                    return { response: { infos: this.getMainFileDiagnosticsForFileEx(args.file) }, responseRequired: true };
                }
                else if ("geterrForProject" == command) {
                    return this.processOldProjectErrors(request);
                }
                else if (TypeScriptCommandNames.IDECompletions == command) {
                    if (isVersionTypeScript15)
                        return { response: [], responseRequired: true };
                    return this.getCompletionEx(request);
                }
                else if (TypeScriptCommandNames.OpenExternalProject == command) {
                    //ignore
                    return util_1.doneRequest;
                }
                return _super.prototype.executeCommand.call(this, request);
            }
            finally {
                var processingTime = Date.now() - startTime;
                logger_impl_1.serverLogger("Message " + request.seq + " '" + command + "' server time, mills: " + processingTime, true);
            }
        };
        Session1x.prototype.closeClientFileEx = function (normalizedFileName) {
            this.projectService.closeClientFile(normalizedFileName);
        };
        Session1x.prototype.refreshStructureEx = function () {
            _super.prototype.refreshStructureEx.call(this);
            this.projectService.updateProjectStructure();
        };
        Session1x.prototype.openClientFileEx = function (openArgs) {
            var fileName = openArgs.file;
            var fileContent = openArgs.fileContent;
            var configFile = openArgs.projectFileName;
            var file = ts_impl.normalizePath(fileName);
            this.openFileEx(file, fileContent, configFile);
        };
        Session1x.prototype.openFileEx = function (fileName, fileContent, configFileName) {
            var projectService = this.projectService;
            if (configFileName) {
                this.projectService.openOrUpdateConfiguredProjectForFile(ts_impl.normalizePath(configFileName));
            }
            else {
                if (isVersionTypeScript15)
                    return ts15impl_1.openClientFileConfig15(projectService, fileName, fileContent, ts_impl);
                this.projectService.openOrUpdateConfiguredProjectForFile(fileName);
            }
            var info = this.projectService.openFile(fileName, /*openedByClient*/ true, fileContent);
            this.projectService.addOpenFile(info);
            return info;
        };
        Session1x.prototype.changeFileEx = function (fileName, content, tsconfig) {
            var file = ts_impl.normalizePath(fileName);
            var project = this.getForceProject(file);
            if (project) {
                var compilerService = project.compilerService;
                var scriptInfo = compilerService.host.getScriptInfo(file);
                if (scriptInfo != null) {
                    scriptInfo.svc.reload(content);
                    if (logger_impl_1.isLogEnabled) {
                        logger_impl_1.serverLogger("Update file reload content from text " + file);
                    }
                }
                else {
                    if (logger_impl_1.isLogEnabled) {
                        logger_impl_1.serverLogger("Update file scriptInfo is null " + file);
                    }
                    this.openClientFileEx({
                        file: fileName,
                        fileContent: content,
                        projectFileName: tsconfig
                    });
                }
            }
            else {
                if (logger_impl_1.isLogEnabled) {
                    logger_impl_1.serverLogger("Update file cannot find project for " + file);
                }
                this.openClientFileEx({
                    file: fileName,
                    fileContent: content,
                    projectFileName: tsconfig
                });
            }
        };
        Session1x.prototype.getProjectForCompileRequest = function (req, normalizedRequestedFile) {
            var project = null;
            if (req.file) {
                project = this.projectService.getProjectForFile(normalizedRequestedFile);
                if (project) {
                    return { project: project };
                }
                //by some reason scriptInfo can exist but defaultProject == null
                //lets try to detect project by opened files (if they are exist)
                var scriptInfo = this.projectService.filenameToScriptInfo[normalizedRequestedFile];
                if (scriptInfo) {
                    //lets try to file parent project
                    project = getProjectForPath(scriptInfo, normalizedRequestedFile, this.projectService);
                    if (project) {
                        return { project: project };
                    }
                }
                this.projectService.openOrUpdateConfiguredProjectForFile(normalizedRequestedFile);
                return { project: this.projectService.getProjectForFile(normalizedRequestedFile) };
            }
            else {
                if (isVersionTypeScript15)
                    return {
                        project: ts15impl_1.openProjectByConfig(this.projectService, normalizedRequestedFile, ts_impl),
                        wasOpened: false
                    };
                project = this.projectService.findConfiguredProjectByConfigFile(normalizedRequestedFile);
                if (!project) {
                    var configResult = this.projectService.openConfigFile(normalizedRequestedFile);
                    if (configResult && configResult.project) {
                        return { project: configResult.project };
                    }
                }
            }
            return { project: project };
        };
        Session1x.prototype.getProjectForFileEx = function (fileName, projectFile) {
            fileName = ts_impl.normalizePath(fileName);
            if (!projectFile) {
                return this.projectService.getProjectForFile(fileName);
            }
            projectFile = ts_impl.normalizePath(projectFile);
            return this.projectService.findConfiguredProjectByConfigFile(projectFile);
        };
        Session1x.prototype.getForceProject = function (normalizedFileName) {
            var projectForFileEx = this.getProjectForFileEx(normalizedFileName);
            if (projectForFileEx) {
                return projectForFileEx;
            }
            var configFile = this.projectService.findConfigFile(normalizedFileName);
            if (!configFile) {
                this.logMessage("Cannot find config file for this file " + normalizedFileName);
                return null;
            }
            return this.projectService.findConfiguredProjectByConfigFile(configFile);
        };
        Session1x.prototype.needRecompile = function (project) {
            return !project || !project.projectOptions || project.projectOptions.compileOnSave || project.projectOptions.compileOnSave === undefined;
        };
        Session1x.prototype.setNewLine = function (project, options) {
            (project.compilerService.host).getNewLine = function () {
                return ts_impl.getNewLineCharacter(options ? options : {});
            };
        };
        Session1x.prototype.getCompileOptionsEx = function (project) {
            return project.projectOptions ? project.projectOptions.compilerOptions : null;
        };
        Session1x.prototype.appendGlobalErrors = function (result, processedProjects, empty) {
            result = _super.prototype.appendGlobalErrors.call(this, result, processedProjects, empty);
            var values = projectErrors.value;
            for (var projectName in values) {
                if (values.hasOwnProperty(projectName)) {
                    var processedProjectErrors = values[projectName];
                    if (processedProjectErrors && processedProjectErrors.length > 0) {
                        if (empty || processedProjects[projectName]) {
                            var diagnosticBody = {
                                file: projectName,
                                diagnostics: processedProjectErrors
                            };
                            result = [diagnosticBody].concat(result);
                        }
                    }
                }
            }
            return result;
        };
        return Session1x;
    }(TypeScriptSession));
    function getProjectForPath(scriptInfo, path, projectService) {
        var searchPath = ts_impl.getDirectoryPath(path);
        if (searchPath) {
            var configFileName = projectService.findConfigFile(searchPath);
            if (configFileName) {
                var project = projectService.findConfiguredProjectByConfigFile(configFileName);
                if (project && project.getSourceFile(scriptInfo)) {
                    return project;
                }
            }
        }
        return null;
    }
    return Session1x;
}
exports.extendSessionClass1x = extendSessionClass1x;
function instantiateSession1x(SessionClassImpl, logger) {
    return new SessionClassImpl(Buffer.byteLength, process.hrtime, logger);
}
exports.instantiateSession1x = instantiateSession1x;
