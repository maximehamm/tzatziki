"use strict";
exports.__esModule = true;
var fs = require("fs");
//copy ts-server implementation
function createPollingWatchedFileSet(ts, sys, interval, chunkSize) {
    if (interval === void 0) { interval = 2500; }
    if (chunkSize === void 0) { chunkSize = 30; }
    var watchedFiles = [];
    var nextFileToCheck = 0;
    if (!ts.FileWatcherEventKind) {
        //old signature
        //export type FileWatcherCallback = (fileName: string, removed?: boolean) => void;
        ts.FileWatcherEventKind = {};
        ts.FileWatcherEventKind.Created = false;
        ts.FileWatcherEventKind.Changed = false;
        ts.FileWatcherEventKind.Deleted = true;
    }
    return { getModifiedTime: getModifiedTime, poll: poll, startWatchTimer: startWatchTimer, addFile: addFile, removeFile: removeFile };
    function getModifiedTime(fileName) {
        return fs.statSync(fileName).mtime;
    }
    function poll(checkedIndex) {
        var watchedFile = watchedFiles[checkedIndex];
        if (!watchedFile) {
            return;
        }
        fs.stat(watchedFile.fileName, function (err, stats) {
            if (err) {
                if (err.code === "ENOENT") {
                    if (watchedFile.mtime.getTime() !== 0) {
                        watchedFile.mtime = new Date(0);
                        watchedFile.callback(watchedFile.fileName, ts.FileWatcherEventKind.Deleted);
                    }
                }
                else {
                    watchedFile.callback(watchedFile.fileName, ts.FileWatcherEventKind.Changed);
                }
            }
            else {
                var oldTime = watchedFile.mtime.getTime();
                var newTime = stats.mtime.getTime();
                if (oldTime !== newTime) {
                    watchedFile.mtime = stats.mtime;
                    var eventKind = oldTime === 0
                        ? ts.FileWatcherEventKind.Created
                        : newTime === 0
                            ? ts.FileWatcherEventKind.Deleted
                            : ts.FileWatcherEventKind.Changed;
                    watchedFile.callback(watchedFile.fileName, eventKind);
                }
            }
        });
    }
    // this implementation uses polling and
    // stat due to inconsistencies of fs.watch
    // and efficiency of stat on modern filesystems
    function startWatchTimer() {
        setInterval(function () {
            var count = 0;
            var nextToCheck = nextFileToCheck;
            var firstCheck = -1;
            while ((count < chunkSize) && (nextToCheck !== firstCheck)) {
                poll(nextToCheck);
                if (firstCheck < 0) {
                    firstCheck = nextToCheck;
                }
                nextToCheck++;
                if (nextToCheck === watchedFiles.length) {
                    nextToCheck = 0;
                }
                count++;
            }
            nextFileToCheck = nextToCheck;
        }, interval);
    }
    function addFile(fileName, callback) {
        var file = {
            fileName: fileName,
            callback: callback,
            mtime: sys.fileExists(fileName)
                ? getModifiedTime(fileName)
                : new Date(0) // Any subsequent modification will occur after this time
        };
        watchedFiles.push(file);
        if (watchedFiles.length === 1) {
            startWatchTimer();
        }
        return file;
    }
    function removeFile(file) {
        unorderedRemoveItem(watchedFiles, file);
    }
}
exports.createPollingWatchedFileSet = createPollingWatchedFileSet;
function unorderedRemoveItem(array, item) {
    unorderedRemoveFirstItemWhere(array, function (element) { return element === item; });
}
function unorderedRemoveFirstItemWhere(array, predicate) {
    for (var i = 0; i < array.length; i++) {
        if (predicate(array[i])) {
            unorderedRemoveItemAt(array, i);
            break;
        }
    }
}
function unorderedRemoveItemAt(array, index) {
    // Fill in the "hole" left at `index`.
    array[index] = array[array.length - 1];
    array.pop();
}
