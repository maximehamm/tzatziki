var UTF8 = 'utf8';
var initializedPlugin = null;
function parseParams() {
    var result = {
        sessionId: null,
        restArgs: null,
        pluginName: null
    };
    var args = process.argv.slice(2);
    var counter = 0;
    var paramNameToPropertyName = {};
    paramNameToPropertyName["-id="] = 'sessionId';
    paramNameToPropertyName["-pluginName="] = 'pluginName';
    args.forEach(function (value, index, arr) {
        function isName(name) {
            return value.indexOf(name) === 0;
        }
        function getValue() {
            return value.split('=')[1];
        }
        Object.keys(paramNameToPropertyName).forEach(function (val) {
            if (isName(val)) {
                result[paramNameToPropertyName[val]] = getValue();
                counter++;
            }
        });
    });
    result.restArgs = args.slice(counter);
    return result;
}
function getPluginFactory(state, pluginName) {
    var pluginPath = pluginName;
    if (state.pluginPath) {
        pluginPath = state.pluginPath;
    }
    var factoryProvider = require(pluginPath);
    return factoryProvider.factory;
}
;
function initAndStartListening(params) {
    var readline = require("readline");
    var rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout,
        terminal: false
    });
    process.stdin.setEncoding(UTF8);
    var pending = [];
    var canWrite = true;
    function writeMessage(s) {
        if (!canWrite) {
            pending.push(s);
        }
        else {
            canWrite = false;
            process.stdout.write(new Buffer(s, UTF8), setCanWriteFlagAndWriteMessageIfNecessary);
        }
    }
    function setCanWriteFlagAndWriteMessageIfNecessary() {
        canWrite = true;
        if (pending.length) {
            writeMessage(pending.shift());
        }
    }
    var messageWriter = {
        write: function (answer) {
            writeMessage(answer + '\n');
        }
    };
    var expectedState = true;
    rl.on("line", function (input) {
        var message = input.trim();
        if (expectedState) {
            var state = JSON.parse(message);
            if (state && state.pluginName) {
                var pluginName = state.pluginName;
                if (initializedPlugin == null) {
                    var pluginFactory = getPluginFactory(state, pluginName);
                    if (pluginFactory != null) {
                        var result = {};
                        try {
                            var _a = pluginFactory.create(state), languagePlugin = _a.languagePlugin, readyMessage = _a.readyMessage;
                            initializedPlugin = languagePlugin;
                            result.success = true;
                            result.message = readyMessage;
                            sendJson(JSON.stringify(result));
                        }
                        catch (e) {
                            //initialization error
                            //ok, lets kill the process
                            result.success = false;
                            var err = e.message || e.messageText;
                            result.error = "Initialization error (" + pluginName + "). " + err;
                            result.stack = e.stack;
                            sendJson(JSON.stringify(result));
                        }
                    }
                }
                expectedState = false;
            }
        }
        else {
            if (initializedPlugin != null) {
                try {
                    initializedPlugin.onMessage(message, messageWriter);
                }
                catch (e) {
                    console.error(e.message + " " + e.stack);
                }
            }
        }
    });
    rl.on("close", function () {
        exitProcess();
    });
    sendCommand("ready");
    setInterval(function () {
        console.error('Process ' + params.sessionId + ' heartbeat: "alive" ');
    }, 60000);
    function sendCommand(command) {
        process.stdout.write(params.sessionId + ' ' + command + '\n', UTF8);
    }
    function sendJson(json) {
        process.stdout.write(json + '\n', UTF8);
    }
}
function exitProcess() {
    process.exit(0);
}
initAndStartListening(parseParams());
