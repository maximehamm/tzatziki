"use strict";
exports.__esModule = true;
var compile_info_holder_1 = require("./session/compile-info-holder");
var util_1 = require("./util");
var ts_session_1x_1 = require("./session/old/ts-session-1x");
var ts_session_21_1 = require("./session/old/ts-session-21");
var ts_common_session_1x_21_1 = require("./session/old/ts-common-session-1x_21");
var ts_session_latest_1 = require("./ts-session-latest");
function instantiateSession(ts_impl, logger, defaultOptionsHolder, sessionClass) {
    var host = ts_impl.sys;
    var session;
    if (use1x(ts_impl.version)) {
        return ts_session_1x_1.instantiateSession1x(sessionClass, logger);
    }
    else {
        return ts_session_21_1.instantiateSession21(ts_impl, logger, sessionClass, defaultOptionsHolder, host);
    }
}
exports.instantiateSession = instantiateSession;
function createSessionClass(ts_impl, defaultOptionsHolder) {
    var defaultSessionClass = ts_common_session_1x_21_1.createCommon_1x_21_SessionClass(ts_impl, defaultOptionsHolder);
    var TypeScriptProjectService = ts_impl.server.ProjectService;
    var TypeScriptCommandNames = ts_impl.server.CommandNames;
    util_1.initCommandNames(TypeScriptCommandNames);
    var version = ts_impl.version;
    var versionNumbers = util_1.parseNumbersInVersion(version);
    var host = ts_impl.sys;
    if (util_1.isVersionMoreOrEqual(versionNumbers, 2, 4, 0)) {
        return ts_session_latest_1.createSessionLatestClass(TypeScriptProjectService, TypeScriptCommandNames, host, ts_impl, defaultOptionsHolder);
    }
    else if (use1x(version)) {
        var sessionClass = ts_session_1x_1.extendSessionClass1x(defaultSessionClass, TypeScriptProjectService, TypeScriptCommandNames, host, ts_impl, defaultOptionsHolder, compile_info_holder_1.projectEmittedWithAllFiles);
        return sessionClass;
    }
    else {
        var sessionClass = ts_session_21_1.extendSessionClass21(defaultSessionClass, TypeScriptProjectService, TypeScriptCommandNames, host, ts_impl, defaultOptionsHolder, compile_info_holder_1.projectEmittedWithAllFiles);
        return sessionClass;
    }
}
exports.createSessionClass = createSessionClass;
function use1x(version) {
    var isTS1X = version.indexOf("1.") == 0;
    var isTS2X = version.indexOf("2.") == 0;
    var isTS20 = isTS2X && version.indexOf("2.0") == 0;
    return isTS1X || isTS20 && isOld20();
    function isOld20() {
        for (var i = 0; i < 6; i++) {
            var expectedVersion = "2.0." + i;
            if (expectedVersion == version ||
                version.indexOf(expectedVersion + ".") == 0) {
                return true;
            }
        }
        return false;
    }
}
