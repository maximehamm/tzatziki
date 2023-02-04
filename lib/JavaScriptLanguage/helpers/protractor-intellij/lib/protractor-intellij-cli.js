var path = require('path')
  , CONFIG_FILE_KEY = 'intellijOriginalConfigFile';

function parseArguments() {
  var argv = process.argv
    , options = {};
  for (var i = 2; i < argv.length; i++) {
    var arg = argv[i];
    var ind = arg.indexOf('=');
    if (ind !== -1 && arg.indexOf('--') === 0) {
      var key = arg.substring(2, ind);
      options[key] = arg.substring(ind + 1);
    }
  }
  return options;
}

var options = parseArguments();

function getConfigFile() {
  var configFile = options[CONFIG_FILE_KEY];
  if (!configFile) {
    throw Error(CONFIG_FILE_KEY + " option not specified");
  }
  return configFile;
}

function isMasterProcess() {
  var mainFilename = require.main.filename;
  if (typeof mainFilename === 'string') {
    var basename = path.basename(mainFilename);
    var dirname = path.basename(path.dirname(mainFilename));
    return dirname === 'bin' && basename === 'protractor';
  }
  return false;
}

exports.getConfigFile = getConfigFile;
exports.isMasterProcess = isMasterProcess;
