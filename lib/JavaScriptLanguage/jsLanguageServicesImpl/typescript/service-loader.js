"use strict";
exports.__esModule = true;
var logger_impl_1 = require("./logger-impl");
var toReplace = "ioSession.listen()";
var replacement = "//ioSession.listen()";
/**
 * Provide service for old & new integration (ts-complier-host-impl)
 */
function getService(state) {
    var serviceFolderPath = state.serverFolderPath;
    var serverFolderPath = serviceFolderPath;
    if (serverFolderPath == null) {
        throw new Error('Service file is empty');
    }
    var fs = require('fs');
    if (state.packageJson != null) {
        try {
            if ((process.versions).pnp != null) {
                var newRequirePath = (process.versions).pnp != null ?
                    state.packageJson :
                    state.packageJson.substr(0, state.packageJson.length - "/package.json".length);
                var newRequire = createRequire(newRequirePath);
                var path = serverFolderPath + "/lib/tsserverlibrary";
                logger_impl_1.serverLogger("Yarn require is used " + newRequirePath, true);
                var tsService = newRequire(path);
                if (tsService != null && tsService.version != null) {
                    var resolvePath = newRequire.resolve(path);
                    return {
                        ts: tsService,
                        serverFilePath: resolvePath
                    };
                }
            }
        }
        catch (e) {
            throw e;
        }
    }
    //simple path -> trying to load using regular "require" 
    try {
        var serviceFolderPath_1 = state.serverFolderPath;
        var lastChar = serviceFolderPath_1.charAt(serviceFolderPath_1.length - 1);
        if (lastChar != '/' && lastChar != '\\') {
            serviceFolderPath_1 = serviceFolderPath_1 + "/";
        }
        var resolvePath = serviceFolderPath_1 + "tsserverlibrary.js";
        var tsService = require(resolvePath);
        if (tsService != null) {
            //the main issue with the solution that we don't "real" start place
            //let's try to guess
            var fs_1 = require('fs');
            if (fs_1 != null && !fs_1.existsSync(serviceFolderPath_1 + "lib.d.ts")) {
                var nodeModulesCandidate = tsService.getDirectoryPath(tsService.getDirectoryPath(serviceFolderPath_1));
                //possibly "real" typescript is used 
                if (fs_1.existsSync(nodeModulesCandidate + "/typescript/lib/lib.d.ts")) {
                    resolvePath = nodeModulesCandidate + "/typescript/lib/tsserverlibrary.js";
                }
            }
            logger_impl_1.serverLogger("Require is used " + serviceFolderPath_1, true);
            return {
                ts: tsService,
                serverFilePath: resolvePath
            };
        }
    }
    catch (e) {
        //skip, try the next code 
    }
    var data = getFilePathIfExists(fs, serverFolderPath);
    if (!data) {
        throw new Error('Cannot find tsserverlibrary.js or tsserver.js file');
    }
    var filePath = data.path;
    var context = null;
    var vm = require('vm');
    context = createContext(context, vm);
    vm.runInNewContext(data.data, context);
    if (!context || !context.ts) {
        throw new Error('Cannot find tsserver implementation in the file ' + filePath);
    }
    return {
        ts: context.ts,
        serverFilePath: filePath
    };
}
exports.getService = getService;
function createRequire(contextPath) {
    var module = require('module');
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
        ', Node.js >= 12.2.0 is required');
}
function getFilePathIfExists(fs, serverFolderPath) {
    {
        var pathToServicesFile = serverFolderPath + "tsserverlibrary.js";
        if (fs.existsSync(pathToServicesFile)) {
            logger_impl_1.serverLogger("File content load for tsserverlibrary is used", true);
            return {
                data: fs.readFileSync(pathToServicesFile, 'utf-8'),
                path: pathToServicesFile
            };
        }
    }
    {
        var pathToServerFile = serverFolderPath + "tsserver.js";
        if (fs.existsSync(pathToServerFile)) {
            logger_impl_1.serverLogger("File content load for tsserver with override is used", true);
            var fileData = fs.readFileSync(pathToServerFile, 'utf-8');
            return {
                data: fileData.replace(toReplace, replacement),
                path: pathToServerFile
            };
        }
    }
    return null;
}
function createContext(context, vm) {
    context = vm.createContext();
    context.module = module;
    context.require = require;
    context.process = process;
    context.__dirname = __dirname;
    context.__filename = __filename;
    context.Buffer = Buffer;
    context.setTimeout = setTimeout;
    context.setInterval = setInterval;
    context.setInterval = setInterval;
    context.setImmediate = setImmediate;
    context.global = global;
    context.console = console;
    context.clearTimeout = clearTimeout;
    context.clearInterval = clearInterval;
    context.clearImmediate = clearImmediate;
    context.exports = exports;
    return context;
}
