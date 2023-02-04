var path = require('path')
  , cli = require('./protractor-intellij-cli')
  , originalConfigPath = cli.getConfigFile()
  , Tree = require('./protractor-intellij-tree');

var originalConfig = require(originalConfigPath).config;

var plugins = originalConfig.plugins || [];
plugins.push({
  path: require.resolve('./protractor-intellij-plugin.js')
});
originalConfig.plugins = plugins;

var originalConfigDir = path.dirname(originalConfigPath);

Object.defineProperty(originalConfig, 'configDir', {
  set: function () {
  },
  get: function () {
    return originalConfigDir;
  }
});

if (cli.isMasterProcess()) {
  var tree = new Tree(null, process.stdout.write.bind(process.stdout));
  tree.updateRootNode(path.basename(originalConfigPath), null, 'file://' + originalConfigPath);
  tree.startNotify();
}

exports.config = originalConfig;
