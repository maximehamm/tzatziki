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
var util_1 = require("../../util");
var logger_impl_1 = require("../../logger-impl");
var ts_project_service_21_1 = require("./ts-project-service-21");
function extendSessionClass21(TypeScriptSession, TypeScriptProjectService, TypeScriptCommandNames, host, ts_impl, defaultOptionsHolder, projectEmittedWithAllFiles) {
    ts_project_service_21_1.extendProjectService21(TypeScriptProjectService, ts_impl, host);
    var version = ts_impl.version;
    var tsVersion = util_1.parseNumbersInVersion(version);
    var Session21 = /** @class */ (function (_super) {
        __extends(Session21, _super);
        function Session21() {
            return _super !== null && _super.apply(this, arguments) || this;
        }
        Session21.prototype.reloadFileFromDisk = function (info) {
            info.reloadFromFile();
        };
        Session21.prototype.getScriptInfo = function (projectService, fileName) {
            return projectService && projectService.getScriptInfoForNormalizedPath(fileName);
        };
        Session21.prototype.appendGlobalErrors = function (result, processedProjects, empty) {
            var _this = this;
            try {
                result = _super.prototype.appendGlobalErrors.call(this, result, processedProjects, empty);
                if (!processedProjects)
                    return result;
                var _loop_1 = function (projectName) {
                    if (processedProjects.hasOwnProperty(projectName)) {
                        var processedProject_1 = processedProjects[projectName];
                        var anyProject = processedProject_1;
                        var errors = anyProject.getProjectErrors ?
                            anyProject.getProjectErrors() :
                            //ts2.5 method
                            anyProject.getGlobalProjectErrors();
                        if (errors && errors.length > 0) {
                            var items = errors.map(function (el) { return _this.formatDiagnostic(projectName, processedProject_1, el); });
                            result = result.concat({
                                file: projectName,
                                diagnostics: items
                            });
                        }
                    }
                };
                for (var projectName in processedProjects) {
                    _loop_1(projectName);
                }
                return result;
            }
            catch (err) {
                logger_impl_1.serverLogger("Error appending global project errors " + err.message, true);
            }
            return result;
        };
        Session21.prototype.beforeFirstMessage = function () {
            if (defaultOptionsHolder.options != null) {
                util_1.updateInferredProjectSettings(ts_impl, defaultOptionsHolder, this.projectService);
            }
            _super.prototype.beforeFirstMessage.call(this);
        };
        Session21.prototype.setNewLine = function (project, options) {
            //todo lsHost is a private field
            var host = project.lsHost;
            if (!host) {
                logger_impl_1.serverLogger("API was changed Project#lsHost is not found", true);
                return;
            }
            if (host && ts_impl.getNewLineCharacter) {
                host.getNewLine = function () {
                    return ts_impl.getNewLineCharacter(options ? options : {});
                };
            }
        };
        Session21.prototype.getCompileOptionsEx = function (project) {
            return project.getCompilerOptions();
        };
        Session21.prototype.needRecompile = function (project) {
            if (!project)
                return true;
            var compilerOptions = project.getCompilerOptions();
            if (compilerOptions && compilerOptions.___processed_marker) {
                return project.compileOnSaveEnabled;
            }
            return true;
        };
        Session21.prototype.afterCompileProcess = function (project, requestedFile, wasOpened) {
            if (project) {
                var projectService = this.getProjectService();
                var externalProjects = projectService.externalProjects;
                for (var _i = 0, externalProjects_1 = externalProjects; _i < externalProjects_1.length; _i++) {
                    var project_1 = externalProjects_1[_i];
                    //close old projects
                    var projectName = this.getProjectName(project_1);
                    logger_impl_1.serverLogger("Close external project " + projectName, true);
                    projectService.closeExternalProject(projectName);
                }
                if (wasOpened) {
                    logger_impl_1.serverLogger("Close the opened file", true);
                    this.closeClientFileEx(requestedFile);
                }
            }
        };
        Session21.prototype.isExternalProject = function (project) {
            var projectKind = project.projectKind;
            return projectKind && projectKind == ts_impl.server.ProjectKind.External;
        };
        Session21.prototype.getProjectForCompileRequest = function (req, normalizedRequestedFile) {
            if (req.file) {
                var projectService = this.getProjectService();
                var project = void 0;
                try {
                    var project_2 = this.getProjectForFileEx(normalizedRequestedFile);
                }
                catch (e) {
                    //no project
                }
                if (project) {
                    return { project: project };
                }
                project = this.getFromExistingProject(normalizedRequestedFile);
                if (project) {
                    return { project: project };
                }
                var openClientFile = projectService.openClientFileWithNormalizedPath(normalizedRequestedFile);
                project = this.getFromExistingProject(normalizedRequestedFile);
                var configFileNameForExternalProject = openClientFile.configFileName;
                if (project && openClientFile && configFileNameForExternalProject) {
                    projectService.openExternalProject({
                        projectFileName: configFileNameForExternalProject,
                        rootFiles: [{ fileName: configFileNameForExternalProject }],
                        options: {}
                    });
                    //reduce memory usage: 'old' project will be closed only after 'new' project was created
                    //so we don't release source files
                    projectService.closeClientFile(normalizedRequestedFile);
                    var externalProject = projectService.findProject(configFileNameForExternalProject);
                    if (externalProject != null) {
                        logger_impl_1.serverLogger("External Project was created for compiling", true);
                        return { project: externalProject, wasOpened: false };
                    }
                    else {
                        logger_impl_1.serverLogger("Error while creating External Project for compiling", true);
                    }
                }
                else {
                    logger_impl_1.serverLogger("File was opened for compiling", true);
                    return { project: project, wasOpened: true };
                }
            }
            else if (req.projectFileName) {
                var projectService = this.getProjectService();
                var configProject = projectService.findProject(normalizedRequestedFile);
                if (configProject) {
                    return { project: configProject, wasOpened: false };
                }
                logger_impl_1.serverLogger("External project was created for compiling project", true);
                projectService.openExternalProject({
                    projectFileName: normalizedRequestedFile,
                    rootFiles: [{ fileName: normalizedRequestedFile }],
                    options: {}
                });
                var externalProject = projectService.findProject(normalizedRequestedFile);
                if (externalProject != null) {
                    logger_impl_1.serverLogger("External Project(2) was created for compiling", true);
                    return { project: externalProject };
                }
                else {
                    logger_impl_1.serverLogger("Error while creating External Project(2) for compiling", true);
                }
            }
            return { project: null };
        };
        Session21.prototype.positionToLineOffset = function (project, fileName, position) {
            //todo review performance
            var scriptInfo = this.getProjectService().getScriptInfo(fileName);
            if (!scriptInfo) {
                logger_impl_1.serverLogger("ERROR! Cannot find script info for file " + fileName, true);
                return undefined;
            }
            return scriptInfo.positionToLineOffset(position);
        };
        Session21.prototype.containsFileEx = function (project, file, reqOpen) {
            return (project).containsFile(file, reqOpen);
        };
        Session21.prototype.getProjectName = function (project) {
            return project.getProjectName();
        };
        Session21.prototype.getProjectConfigPathEx = function (project) {
            if (this.isExternalProject(project)) {
                return this.getProjectName(project);
            }
            //ts2.2
            if (project.getConfigFilePath) {
                return project.getConfigFilePath();
            }
            var configFileName = project.configFileName;
            return configFileName;
        };
        Session21.prototype.executeCommand = function (request) {
            var startTime = this.getTime();
            var command = request.command;
            try {
                if (TypeScriptCommandNames.Open == command || TypeScriptCommandNames.Close == command) {
                    _super.prototype.executeCommand.call(this, request);
                    //open | close command doesn't send answer so we have to override
                    return util_1.doneRequest;
                }
                else if (TypeScriptCommandNames.ReloadProjects == command) {
                    projectEmittedWithAllFiles.reset();
                    _super.prototype.executeCommand.call(this, request);
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
                else if ("completions" == command) {
                    return this.getCompletionEx(request);
                }
                else if (command == "suggestionDiagnosticsSync") {
                    return { response: { infos: [] }, responseRequired: true };
                }
                else if (TypeScriptCommandNames.IDEGetErrors == command) {
                    var args = request.arguments;
                    return { response: { infos: this.getDiagnosticsEx(args.files) }, responseRequired: true };
                }
                else if (TypeScriptCommandNames.IDEGetMainFileErrors == command) {
                    var args = request.arguments;
                    return { response: { infos: this.getMainFileDiagnosticsForFileEx(args.file) }, responseRequired: true };
                }
                else if (TypeScriptCommandNames.IDEGetProjectErrors == command) {
                    var args = request.arguments;
                    var projectDiagnosticsForFileEx = this.getProjectDiagnosticsForFileEx(args.file);
                    return { response: { infos: projectDiagnosticsForFileEx }, responseRequired: true };
                }
                else if ("geterrForProject" == command) {
                    var version221AndHigher = util_1.isVersionMoreOrEqual(tsVersion, 2, 2, 1);
                    if (!version221AndHigher) {
                        return this.processOldProjectErrors(request);
                    }
                    return _super.prototype.executeCommand.call(this, request);
                }
                else if (TypeScriptCommandNames.OpenExternalProject == command) {
                    //ignore
                    return util_1.doneRequest;
                }
                if (TypeScriptCommandNames.IDEGetProjectsInfo == command) {
                    return this.getProjectsInfoIDE();
                }
                return _super.prototype.executeCommand.call(this, request);
            }
            finally {
                var processingTime = Date.now() - startTime;
                logger_impl_1.serverLogger("Message " + request.seq + " '" + command + "' server time, mills: " + processingTime, true);
            }
        };
        Session21.prototype.getProjectForFileEx = function (fileName, projectFile) {
            var _this = this;
            if (!projectFile) {
                fileName = ts_impl.normalizePath(fileName);
                var scriptInfo = this.getScriptInfo(this.getProjectService(), fileName);
                if (scriptInfo) {
                    var projects = scriptInfo.containingProjects;
                    if (projects && projects.length > 1) {
                        var candidates = [];
                        for (var _i = 0, projects_1 = projects; _i < projects_1.length; _i++) {
                            var currentProject = projects_1[_i];
                            var projectConfigPathEx = this.getProjectConfigPathEx(currentProject);
                            if (projectConfigPathEx) {
                                candidates.push(currentProject);
                            }
                        }
                        if (candidates.length == 1) {
                            return candidates[0];
                        }
                        //ok, we have several tsconfig.json that include the file
                        //we should use the nearest config
                        if (candidates.length > 0) {
                            var candidatesProjectDirToProject_1 = {};
                            candidates.forEach(function (el) {
                                return candidatesProjectDirToProject_1[ts_impl.getDirectoryPath(_this.getProjectConfigPathEx(el))] = el;
                            });
                            var directory = ts_impl.getDirectoryPath(fileName);
                            while (directory && directory.length != ts_impl.getRootLength(directory)) {
                                var nearestProject = candidatesProjectDirToProject_1[directory];
                                if (nearestProject) {
                                    return nearestProject;
                                }
                                var newDirectory = ts_impl.getDirectoryPath(directory);
                                directory = newDirectory != directory ? newDirectory : null;
                            }
                        }
                    }
                }
                return this.getProjectService().getDefaultProjectForFile(fileName, true);
            }
            return this.getProjectService().findProject(projectFile);
        };
        Session21.prototype.tsVersion = function () {
            return "2.0.5";
        };
        Session21.prototype.closeClientFileEx = function (normalizedFileName) {
            var scriptInfoForNormalizedPath = this.getProjectService().getScriptInfoForNormalizedPath(normalizedFileName);
            if (!scriptInfoForNormalizedPath || !scriptInfoForNormalizedPath.isOpen) {
                return;
            }
            this.projectService.closeClientFile(normalizedFileName);
        };
        Session21.prototype.refreshStructureEx = function () {
            _super.prototype.refreshStructureEx.call(this);
            this.getProjectService().refreshInferredProjects();
        };
        Session21.prototype.changeFileEx = function (fileName, content, tsconfig) {
            fileName = ts_impl.normalizePath(fileName);
            var info = this.getProjectService().getScriptInfoForNormalizedPath(fileName);
            if (info && info.isOpen) {
                this.getProjectService().getOrCreateScriptInfo(fileName, true, content);
            }
            else {
                this.getProjectService().openClientFileWithNormalizedPath(fileName, content);
            }
        };
        Session21.prototype.getLanguageService = function (project, sync) {
            if (sync === void 0) { sync = true; }
            return project.getLanguageService(sync);
        };
        Session21.prototype.lineOffsetToPosition = function (project, fileName, line, offset) {
            //todo review performance
            var scriptInfo = this.getProjectService().getScriptInfo(fileName);
            if (!scriptInfo) {
                logger_impl_1.serverLogger("ERROR! Cannot find script info for file " + fileName, true);
                return undefined;
            }
            return scriptInfo.lineOffsetToPosition(line, offset);
        };
        /**
         * todo change d.ts files & replace any by ts.server.ProjectService
         */
        Session21.prototype.getProjectService = function () {
            return this.projectService;
        };
        Session21.prototype.getFromExistingProject = function (normalizedRequestedFile) {
            var projectService = this.getProjectService();
            {
                //prefer configured project
                var configuredProjects = projectService.configuredProjects;
                for (var _i = 0, configuredProjects_1 = configuredProjects; _i < configuredProjects_1.length; _i++) {
                    var project = configuredProjects_1[_i];
                    if (this.containsFileEx(project, normalizedRequestedFile, false)) {
                        return project;
                    }
                }
            }
            {
                var inferredProjects = projectService.inferredProjects;
                for (var _a = 0, inferredProjects_1 = inferredProjects; _a < inferredProjects_1.length; _a++) {
                    var project = inferredProjects_1[_a];
                    if (this.containsFileEx(project, normalizedRequestedFile, false)) {
                        return project;
                    }
                }
            }
            return null;
        };
        Session21.prototype.getProjectsInfoIDE = function () {
            var infos = [];
            for (var _i = 0, _a = this.projectService.configuredProjects; _i < _a.length; _i++) {
                var configuredProject = _a[_i];
                this.addProjectInfo(configuredProject, infos);
            }
            for (var _b = 0, _c = this.projectService.inferredProjects; _b < _c.length; _b++) {
                var inferredProject = _c[_b];
                this.addProjectInfo(inferredProject, infos);
            }
            for (var _d = 0, _e = this.projectService.externalProjects; _d < _e.length; _d++) {
                var externalProject = _e[_d];
                this.addProjectInfo(externalProject, infos);
            }
            return { responseRequired: true, response: infos };
        };
        Session21.prototype.addProjectInfo = function (project, infos) {
            var name = project.getProjectName();
            var regularFileInfos = project.getFileNames(false).map(function (el) {
                var info = project.getScriptInfo(el);
                return {
                    fileName: el,
                    isOpen: info.isScriptOpen(),
                    isExternal: false
                };
            });
            var externalFileInfos = [];
            if (project.getExternalFiles) {
                externalFileInfos = project.getExternalFiles().map(function (el) {
                    var info = project.getScriptInfo(el);
                    return {
                        fileName: el,
                        isOpen: info.isScriptOpen(),
                        isExternal: true
                    };
                });
            }
            infos.push({
                projectName: name,
                fileInfos: regularFileInfos.concat(externalFileInfos)
            });
        };
        return Session21;
    }(TypeScriptSession));
    //todo remove after replacing typing
    var IDESessionImpl = Session21;
    return IDESessionImpl;
}
exports.extendSessionClass21 = extendSessionClass21;
function instantiateSession21(ts_impl, logger, SessionClassImpl, defaultOptionsHolder, host) {
    var _a, _b;
    var cancellationToken;
    try {
        var factory = require("./cancellationToken");
        cancellationToken = factory(host.args);
    }
    catch (e) {
        cancellationToken = {
            isCancellationRequested: function () {
                return false;
            },
            setRequest: function (_requestId) {
                return void 0;
            },
            resetRequest: function (_requestId) {
                return void 0;
            }
        };
    }
    var nullTypingsInstaller = {
        enqueueInstallTypingsRequest: function () {
        },
        attach: function (projectService) {
        },
        onProjectClosed: function (p) {
        },
        globalTypingsCacheLocation: undefined
    };
    var useSingleInferredProject = defaultOptionsHolder.isUseSingleInferredProject();
    var version = ts_impl.version;
    var tsVersion = util_1.parseNumbersInVersion(version);
    var session;
    if (util_1.isVersionMoreOrEqual(tsVersion, 2, 3, 1)) {
        var pluginProbeLocations = (_a = (defaultOptionsHolder.pluginState.pluginProbeLocations || [])).concat.apply(_a, parseStringArrayArg(defaultOptionsHolder, "--pluginProbeLocations"));
        var globalPlugins = (_b = (defaultOptionsHolder.pluginState.globalPlugins || [])).concat.apply(_b, parseStringArrayArg(defaultOptionsHolder, "--globalPlugins"));
        var options = {
            host: host,
            cancellationToken: cancellationToken,
            useSingleInferredProject: useSingleInferredProject,
            typingsInstaller: nullTypingsInstaller,
            byteLength: Buffer.byteLength,
            hrtime: process.hrtime,
            pluginProbeLocations: pluginProbeLocations,
            logger: logger,
            globalPlugins: globalPlugins,
            canUseEvents: true
        };
        session = new SessionClassImpl(options);
    }
    else {
        session = new SessionClassImpl(host, cancellationToken, useSingleInferredProject, nullTypingsInstaller, Buffer.byteLength, process.hrtime, logger, true);
    }
    return session;
    function parseStringArrayArg(optionsHolder, argName) {
        var cmdArgs = optionsHolder.pluginState.commandLineArguments;
        if (!cmdArgs)
            return [];
        var index = cmdArgs.indexOf(argName);
        var arg = index >= 0 && index < cmdArgs.length - 1
            ? cmdArgs[index + 1]
            : undefined;
        if (arg === undefined) {
            return [];
        }
        return arg.split(",").filter(function (name) { return name !== ""; });
    }
}
exports.instantiateSession21 = instantiateSession21;
