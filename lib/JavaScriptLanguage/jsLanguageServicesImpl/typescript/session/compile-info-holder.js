"use strict";
exports.__esModule = true;
var crypto = require('crypto');
var HolderContainer = /** @class */ (function () {
    function HolderContainer() {
        this.value = {};
    }
    HolderContainer.prototype.reset = function () {
        this.value = {};
    };
    HolderContainer.prototype.getName = function (projectUniqueName, projectConfigName) {
        return projectConfigName ? projectUniqueName : HolderContainer.InferredProjectName;
    };
    HolderContainer.prototype.getCompileInfoHolder = function (projectUniqueName, projectConfigName) {
        var name = this.getName(projectUniqueName, projectConfigName);
        return this.value[name];
    };
    HolderContainer.prototype.getOrCreateCompileInfoHolder = function (projectUniqueName, projectConfigName) {
        var name = this.getName(projectUniqueName, projectConfigName);
        var cachedValue = this.value[name];
        if (cachedValue)
            return cachedValue;
        cachedValue = new CompileInfoHolder();
        this.value[name] = cachedValue;
        return cachedValue;
    };
    HolderContainer.prototype.resetProject = function (projectUniqueName) {
        var cachedValue = this.value[projectUniqueName];
        if (cachedValue) {
            delete this.value[projectUniqueName];
        }
    };
    HolderContainer.InferredProjectName = "%InferredProjectNameCache%";
    return HolderContainer;
}());
exports.HolderContainer = HolderContainer;
;
/**
 * Emulating incremental compilation.
 * If file content wasn't changes we don't need recompile the file
 */
var CompileInfoHolder = /** @class */ (function () {
    function CompileInfoHolder() {
        this._lastCompilerResult = {};
    }
    CompileInfoHolder.prototype.checkUpdateAndAddToCache = function (file, ts_impl) {
        if (file) {
            var fileName = ts_impl.normalizePath(file.fileName);
            var newHash = calcHash(file.text);
            var oldHash = this._lastCompilerResult[fileName];
            if (oldHash != null && oldHash == newHash) {
                return false;
            }
            this._lastCompilerResult[fileName] = newHash;
            return true;
        }
        return false;
    };
    CompileInfoHolder.prototype.resetForFile = function (fileName) {
        if (this._lastCompilerResult[fileName]) {
            this._lastCompilerResult[fileName] = null;
        }
    };
    CompileInfoHolder.prototype.reset = function () {
        this._lastCompilerResult = {};
    };
    return CompileInfoHolder;
}());
exports.CompileInfoHolder = CompileInfoHolder;
function calcHash(content) {
    return crypto.createHash('md5').update(content).digest("hex");
}
exports.projectEmittedWithAllFiles = new HolderContainer();
