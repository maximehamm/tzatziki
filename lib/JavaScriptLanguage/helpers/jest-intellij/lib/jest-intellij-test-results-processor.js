const Tree = require('../../base-test-reporter/intellij-tree');
const jestIntellijUtil = require('./jest-intellij-util');
const tree = new Tree(null, process.stdout.write.bind(process.stdout));

tree.startNotify();

module.exports = jestIntellijUtil.safeFn(function (result) {
  result.testResults.forEach(function (testResults) {
    processResults(testResults);
  });
  tree.testingFinished();
  return result;
});

function processResults(testResultsPerTestFile) {
  const testFileNode = jestIntellijUtil.addTestFileNode(tree, testResultsPerTestFile.testFilePath);
  testFileNode.register();
  testFileNode.start();
  jestIntellijUtil.reportTestFileResults(testFileNode, testResultsPerTestFile, true);
}
