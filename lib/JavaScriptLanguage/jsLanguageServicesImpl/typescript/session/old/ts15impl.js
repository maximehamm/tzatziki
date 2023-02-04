"use strict";
exports.__esModule = true;
var logger_impl_1 = require("../../logger-impl");
function reload15(session, ts_impl) {
    logger_impl_1.serverLogger("Start reload");
    var service = session.projectService;
    for (var _i = 0, _a = service.configuredProjects; _i < _a.length; _i++) {
        var project = _a[_i];
        updateConfiguredProject15(service, project, ts_impl);
        service.watchedProjectConfigFileChanged(project);
    }
    for (var _b = 0, _c = service.inferredProjects; _b < _c.length; _b++) {
        var project = _c[_b];
        updateConfiguredProject15(service, project, ts_impl);
        service.watchedProjectConfigFileChanged(project);
    }
    service.updateProjectStructure();
}
exports.reload15 = reload15;
function close15(session, request) {
    var closeArgs = request.arguments;
    session.closeClientFile(closeArgs.file);
}
exports.close15 = close15;
function onMessage15(session, message) {
    try {
        var request = JSON.parse(message);
        var response = session.executeCommand(request);
        if (response && response.responseRequired) {
            session.output(response.response, request.command, request.seq);
        }
        return response;
    }
    catch (e) {
        logger_impl_1.serverLogger(e, true);
        throw e;
    }
}
exports.onMessage15 = onMessage15;
function openClientFileConfig15(service, fileName, fileContent, ts_impl) {
    var info = service.openClientFile(fileName);
    if (fileContent && info) {
        info.svc.reload(fileContent);
    }
    return info;
}
exports.openClientFileConfig15 = openClientFileConfig15;
function openProjectByConfig(service, fileName, ts_impl) {
    var searchPath = ts_impl.normalizePath(ts_impl.getDirectoryPath(fileName));
    var configFileName = service.findConfigFile(searchPath);
    if (configFileName) {
        configFileName = getAbsolutePath(configFileName, searchPath, ts_impl);
    }
    if (configFileName) {
        var project = findConfiguredProjectByConfigFile15(service, configFileName);
        if (project) {
            updateConfiguredProject15(service, project, ts_impl);
        }
        else {
            var configResult = service.openConfigFile(configFileName, fileName);
            if (!configResult.success) {
                logger_impl_1.serverLogger("Error opening config file " + configFileName + " " + configResult.errorMsg, true);
            }
            else {
                service.configuredProjects.push(configResult.project);
            }
        }
    }
    return findConfiguredProjectByConfigFile15(service, fileName);
}
exports.openProjectByConfig = openProjectByConfig;
function setGetFileNames(Project) {
    Project.prototype.getFileNames = function () {
        var sourceFiles = this.program.getSourceFiles();
        var getFileNameCallback = function (sourceFile) { return sourceFile.fileName; };
        return sourceFiles.map(getFileNameCallback);
    };
}
exports.setGetFileNames = setGetFileNames;
function getAbsolutePath(filename, directory, ts_impl) {
    var rootLength = ts_impl.getRootLength(filename);
    if (rootLength > 0) {
        return filename;
    }
    else {
        var splitFilename = filename.split('/');
        var splitDir = directory.split('/');
        var i = 0;
        var dirTail = 0;
        var sflen = splitFilename.length;
        while ((i < sflen) && (splitFilename[i].charAt(0) == '.')) {
            var dots = splitFilename[i];
            if (dots == '..') {
                dirTail++;
            }
            else if (dots != '.') {
                return undefined;
            }
            i++;
        }
        return splitDir.slice(0, splitDir.length - dirTail).concat(splitFilename.slice(i)).join('/');
    }
}
function findConfiguredProjectByConfigFile15(service, configFileName) {
    for (var i = 0, len = service.configuredProjects.length; i < len; i++) {
        if (service.configuredProjects[i].projectFilename == configFileName) {
            return service.configuredProjects[i];
        }
    }
    return undefined;
}
exports.findConfiguredProjectByConfigFile15 = findConfiguredProjectByConfigFile15;
function updateConfiguredProject15(service, project, ts_impl) {
    logger_impl_1.serverLogger("Update project", true);
    if (!service.host.fileExists(project.projectFilename)) {
        service.removeProject(project);
    }
    else {
        var rawConfig = ts_impl.readConfigFile(project.projectFilename);
        var projectOptions = rawConfig.config;
        logger_impl_1.serverLogger("New options " + JSON.stringify(projectOptions), true);
        project.setProjectOptions(projectOptions);
        project.finishGraph();
    }
}
exports.updateConfiguredProject15 = updateConfiguredProject15;
function copyListRemovingItem(item, list) {
    var copiedList = [];
    for (var i = 0, len = list.length; i < len; i++) {
        if (list[i] != item) {
            copiedList.push(list[i]);
        }
    }
    return copiedList;
}
