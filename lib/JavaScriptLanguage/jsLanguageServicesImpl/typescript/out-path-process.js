"use strict";
/**
 * Deprecated part of typescript compiler integration
 * Expands macros for 'output path' field
 */
exports.__esModule = true;
var fileName = '$FileName$';
var fileDir = '$FileDir$';
var fileDirName = '$FileDirName$';
//relative to module content root
var fileDirRelativeToProjectRoot = '$FileDirRelativeToProjectRoot$';
var fileDirRelativeToSourcePath = '$FileDirRelativeToSourcepath$';
var fileRelativeDir = '$FileRelativeDir$';
var moduleFileDir = '$ModuleFileDir$';
var moduleSourcePath = '$ModuleFileDir$';
var sourcePath = '$Sourcepath$';
function getPathProcessor(ts_impl, params) {
    var projectPath = ts_impl.normalizePath(params.projectPath);
    var outPath = ts_impl.normalizePath(params.outPath);
    var outPathHasMacro = outPath.indexOf('$') >= 0;
    var separator = '/';
    function isAbsolute(path) {
        return path.indexOf('/') === 0 || path.indexOf(':') > 0;
    }
    function getName(oldFileName) {
        return oldFileName.substring(oldFileName.lastIndexOf(separator) + 1);
    }
    var MacroProcessor = /** @class */ (function () {
        function MacroProcessor() {
        }
        MacroProcessor.prototype.getExpandedPath = function (oldFileName, contentRoot, sourceRoot, onError) {
            try {
                oldFileName = ts_impl.normalizePath(oldFileName);
                //oldFileName is absolute normalized path
                var newFileName = getName(oldFileName);
                var partWithoutName = outPath;
                if (outPathHasMacro) {
                    partWithoutName = this.expandMacro(oldFileName, newFileName, contentRoot, sourceRoot);
                }
                var path = "";
                if (partWithoutName) {
                    path += partWithoutName + '/';
                }
                path += newFileName;
                if (!isAbsolute(path)) {
                    path = projectPath + '/' + path;
                }
                console.log('suggested path is ' + path);
                return path;
            }
            catch (e) {
                console.log('Suggester error ' + e);
                onError(e);
            }
            return null;
        };
        ;
        MacroProcessor.prototype.expandMacro = function (fullFileName, onlyFileName, contentRoot, sourceRoot) {
            var expandValue = outPath;
            function hasMacro(m) {
                return expandValue.indexOf(m) >= 0;
            }
            function expand(m, value) {
                expandValue = expandValue.replace(m, value);
            }
            if (hasMacro(fileName)) {
                expand(fileName, onlyFileName);
            }
            if (hasMacro(fileDir)) {
                expand(fileDir, ts_impl.getDirectoryPath(fullFileName));
            }
            if (hasMacro(fileDirName)) {
                expand(fileDirName, getName(ts_impl.getDirectoryPath(fullFileName)));
            }
            if (hasMacro(fileRelativeDir)) {
                expand(fileRelativeDir, ts_impl.getDirectoryPath(fullFileName.replace(projectPath + separator, "")));
            }
            if (hasMacro(fileDirRelativeToProjectRoot)) {
                expand(fileDirRelativeToProjectRoot, ts_impl.getDirectoryPath(fullFileName.replace(ts_impl.normalizePath(contentRoot) + separator, "")));
            }
            if (hasMacro(fileDirRelativeToSourcePath)) {
                expand(fileDirRelativeToSourcePath, ts_impl.getDirectoryPath(fullFileName.replace(ts_impl.normalizePath(sourceRoot) + separator, "")));
            }
            if (hasMacro(moduleFileDir)) {
                expand(moduleFileDir, ts_impl.normalizePath(contentRoot));
            }
            if (hasMacro(moduleSourcePath)) {
                expand(moduleSourcePath, ts_impl.normalizePath(sourceRoot));
            }
            if (hasMacro(sourcePath)) {
                expand(sourcePath, ts_impl.normalizePath(sourceRoot));
            }
            return expandValue;
        };
        return MacroProcessor;
    }());
    return new MacroProcessor();
}
exports.getPathProcessor = getPathProcessor;
