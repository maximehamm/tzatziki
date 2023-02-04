"use strict";
exports.__esModule = true;
var util_1 = require("../../util");
var logger_impl_1 = require("../../logger-impl");
function extendProjectService21(TypeScriptProjectService, ts_impl, host) {
    overrideParseJsonConfigFileContent(ts_impl);
    overrideParseJsonSourceFileConfigFileContent(ts_impl);
    overrideGetBaseConfigFileName(ts_impl);
    util_1.extendEx(TypeScriptProjectService, "convertConfigFileContentToProjectOptions", function (oldFunction, args) {
        if (!oldFunction) {
            logger_impl_1.serverLogger("ERROR: method convertConfigFileContentToProjectOptions doesn't exist", true);
            return;
        }
        var options = oldFunction.apply(this, args);
        if (options) {
            if (options.projectOptions) {
                logger_impl_1.serverLogger("Updated compileOnSave");
                //By default ts service consider compileOnSave === undefined -> compileOnSave == false
                //we need override this behaviour
                if (options.projectOptions.compileOnSave == null) {
                    options.projectOptions.compileOnSave = true;
                }
                if (options.projectOptions.compilerOptions) {
                    options.projectOptions.compilerOptions.___processed_marker = true;
                }
            }
        }
        return options;
    });
}
exports.extendProjectService21 = extendProjectService21;
function overrideParseJsonConfigFileContent(ts_impl) {
    var parseJsonConfigFileContentOld = ts_impl.parseJsonConfigFileContent;
    if (!parseJsonConfigFileContentOld) {
        return false;
    }
    ts_impl.parseJsonConfigFileContent = function () {
        var jsonOption = arguments.length > 0 ? arguments[0] : null;
        if (jsonOption != null && !ts_impl.hasProperty(jsonOption, ts_impl.compileOnSaveCommandLineOption.name)) {
            logger_impl_1.serverLogger("No compileOnSave — return true");
            jsonOption.compileOnSave = true;
        }
        return parseJsonConfigFileContentOld.apply(this, arguments);
    };
    return true;
}
function overrideParseJsonSourceFileConfigFileContent(ts_impl) {
    var parseJsonSourceFileConfigFileContentOld = ts_impl.parseJsonSourceFileConfigFileContent;
    if (!parseJsonSourceFileConfigFileContentOld) {
        return false;
    }
    ts_impl.parseJsonSourceFileConfigFileContent = function () {
        var result = parseJsonSourceFileConfigFileContentOld.apply(this, arguments);
        if (result && result.compileOnSave == false) {
            if (result.raw) {
                if (result.raw.compileOnSave == null) {
                    result.compileOnSave = true;
                }
            }
        }
        return result;
    };
    return true;
}
function overrideGetBaseConfigFileName(ts_impl) {
    var getBaseConfigFileNameOld = ts_impl.server.getBaseConfigFileName;
    if (!getBaseConfigFileNameOld) {
        return false;
    }
    ts_impl.server.getBaseConfigFileName = function () {
        logger_impl_1.serverLogger("Called override", true);
        var result = getBaseConfigFileNameOld.apply(this, arguments);
        if (result)
            return result;
        if (arguments[0] && arguments[0].endsWith(".json")) {
            //no matter
            return ts_impl.getBaseFileName(arguments[0]);
        }
        return result;
    };
    return true;
}
