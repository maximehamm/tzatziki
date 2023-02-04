const Tree = require('../../base-test-reporter/intellij-tree')
  , stringifier = require('../../base-test-reporter/intellij-stringifier')
  , util = require('../../base-test-reporter/intellij-util')
  , jestIntellijUtil = require('./jest-intellij-util')
  , processStdoutWrite = process.stdout.write.bind(process.stdout)
  , processStderrWrite = process.stderr.write.bind(process.stderr)
  , nowTime = Date.now.bind(Date);

// Do not require anything outside of unmockedModulePathPatterns (e.g. from node_modules).
// Otherwise required modules and transitively required modules will be mocked if running with "automock:true".

function normalizeFailedExpectation(failedExpectation, testFilePath) {
  const normalizedMessageAndStackObj = jestIntellijUtil.normalizeFailureMessageAndStack(failedExpectation.message || '',
                                                                                        failedExpectation.stack || '');
  let message = normalizedMessageAndStackObj.message;
  let stack = normalizedMessageAndStackObj.stack;
  if (stack.length === 0) {
    const nlInd = message.indexOf('\n');
    if (nlInd > 0 && nlInd + 1 < message.length && message.charAt(nlInd + 1) === '\n') {
      stack = message.substring(nlInd + 2);
      message = message.substring(0, nlInd);
    }
  }
  let expected, actual;
  if (failedExpectation.error) {
    if (failedExpectation.error.matcherResult) {
      expected = failedExpectation.error.matcherResult.expected;
      actual = failedExpectation.error.matcherResult.actual;
    }
    else {
      expected = failedExpectation.error.expected;
      actual = failedExpectation.error.actual;
    }
  }
  else {
    expected = failedExpectation.expected;
    actual = failedExpectation.actual;
  }
  return {message: message, stack: stack, expected: expected, actual: actual};
}

function getTestFilePath() {
  if (jasmine.testPath) {
    return jasmine.testPath;
  }
  if (Symbol && typeof Symbol.for === 'function') {
    const globalStateKey = Symbol.for('$$jest-matchers-object');
    if (globalStateKey) {
      const globalState = global[globalStateKey];
      if (globalState) {
        const state = globalState.state;
        if (state) {
          return state.testPath;
        }
      }
    }
  }
}

function getUniqueTestFileRunId() {
  const id = process.pid + '_';
  if (typeof process.hrtime === 'function') {
    return id + process.hrtime().join('_');
  }
  return id + process.uptime();
}

(function () {
  const setupTestFrameworkScriptFile = jestIntellijUtil.getOriginalSetupTestFrameworkScriptFile();
  if (setupTestFrameworkScriptFile) {
    try {
      require(setupTestFrameworkScriptFile);
    }
    catch (e) {
      warn("Failure from " + setupTestFrameworkScriptFile + ': ' + e.message + '\n' + e.stack);
    }
  }

  const testFilePath = getTestFilePath();
  if (util.isString(testFilePath)) {
    const tree = new Tree(getUniqueTestFileRunId(), processStdoutWrite, testFilePath);
    tree.startNotify();
    jasmine.getEnv().addReporter(new JasmineReporter(tree, testFilePath));
  }
  else {
    console.error('intellij: cannot find testFilePath');
  }
})();

function createdPatchedSpec(OriginalSpec, registry) {
  class JBPatchedSpec extends OriginalSpec {
    constructor(attrs) {
      super(attrs);
      if (attrs && attrs.id) {
        registry[attrs.id] = this;
      }
    }
  }
  return JBPatchedSpec;
}

/**
 * @constructor
 */
function JasmineReporter(tree, testFilePath) {
  const specRegistry = {};
  jasmine.Spec = createdPatchedSpec(jasmine.Spec, specRegistry);
  this.isSpecDisabled = function (result) {
    let spec = specRegistry[result.id];
    return spec && spec.disabled;
  };
  this.getTotalNotDisabledSpecCount = function () {
    if (!Object.values) return null;
    return Object.values(specRegistry).filter((spec) => {
      return !spec.disabled
    }).length;
  };
  this.tree = tree;
  this.testFilePath = testFilePath;
  this.currentSuiteNode = tree.root;
  this.nodeById = {};
}

JasmineReporter.prototype.jasmineStarted = safeFn(function (options) {
  this.tree.addTotalTestCount(this.getTotalNotDisabledSpecCount());
});

JasmineReporter.prototype.suiteStarted = safeFn(function (result) {
  const locationPath = jestIntellijUtil.getLocationPath(this.currentSuiteNode, result.description,
                                                        this.tree.root, this.testFilePath);
  const suiteNode = this.currentSuiteNode.addTestSuiteChild(result.description, 'suite', locationPath);
  this.currentSuiteNode = suiteNode;
});

JasmineReporter.prototype.suiteDone = safeFn(function (result) {
  const suiteNode = this.currentSuiteNode;
  if (suiteNode == null) {
    return warn('No current suite to finish');
  }
  if (suiteNode.name !== result.description) {
    return warn('Suite name mismatch, actual: ' + suiteNode.name + ', expected: ' + result.description);
  }
  if (suiteNode.state.name !== 'created') {
    suiteNode.finish(false);
  }
  this.currentSuiteNode = suiteNode.parent;
});

function startAncestorSuites(suite, root) {
  if (suite.state.name === 'created' && suite !== root) {
    startAncestorSuites(suite.parent, root);
    suite.start();
  }
}

/**
 * @param {jasmine.Result} result
 */
JasmineReporter.prototype.specStarted = safeFn(function (result) {
  if (this.isSpecDisabled(result)) {
    return;
  }
  startAncestorSuites(this.currentSuiteNode, this.tree.root);
  const locationPath = jestIntellijUtil.getLocationPath(this.currentSuiteNode, result.description,
                                                        this.tree.root, this.testFilePath);
  const specNode = this.currentSuiteNode.addTestChild(result.description, 'test', locationPath);
  try {
    specNode.startTime = {millis: nowTime()};
  }
  catch (e) {
    specNode.startTime = {error: e};
  }
  specNode.start();
  if (this.nodeById[result.id] != null) {
    warn('jasmine error, specStarted with not unique result.id: ' + result.id)
  }
  this.nodeById[result.id] = specNode;
});

function passedTime(startTime) {
  if (typeof startTime.millis === 'number') {
    try {
      return nowTime() - startTime.millis;
    }
    catch (e) {
      warn('Failed to call Date.now() on specDone: ' + e.message);
    }
  }
  else {
    warn('Failed to call Date.now() on specStarted: ' + startTime.error);
  }
}

/**
 * @param {jasmine.Result} result
 */
JasmineReporter.prototype.specDone = safeFn(function (result) {
  const specNode = this.nodeById[result.id];
  if (specNode == null) {
    if (this.isSpecDisabled(result)) {
      return;
    }
    return warn('Cannot find specNode by id ' + result.id);
  }
  const durationMillis = passedTime(specNode.startTime);
  let failureMessage, failureDetails, expectedStr, actualStr;
  if (result.failedExpectations.length > 0) {
    const failedExpectation = result.failedExpectations[0];
    const normalized = normalizeFailedExpectation(failedExpectation, this.testFilePath);
    failureMessage = normalized.message;
    failureDetails = normalized.stack;
    if (normalized.expected !== normalized.actual) {
      expectedStr = stringifier.stringify(normalized.expected);
      actualStr = stringifier.stringify(normalized.actual);
    }
  }
  var outcome;
  if (result.status === 'passed') {
    outcome = Tree.TestOutcome.SUCCESS;
  }
  else if (result.status === 'pending' || result.status === 'disabled') {
    outcome = Tree.TestOutcome.SKIPPED;
  }
  else if (result.status === 'todo') {
    failureMessage = failureMessage != null ? failureMessage : `Todo '${specNode.name}'`;
    outcome = Tree.TestOutcome.SKIPPED;
  }
  else {
    failureMessage = failureMessage != null ? failureMessage : 'Unspecified error';
    outcome = Tree.TestOutcome.FAILED;
  }
  specNode.setOutcome(outcome, durationMillis, failureMessage, failureDetails, expectedStr, actualStr, null, null);
  if (util.isString(expectedStr)) {
    specNode.setPrintExpectedAndActualValues(!jestIntellijUtil.containsExpectedAndActualValues(failureMessage));
  }
  specNode.finish(false);
});

JasmineReporter.prototype.jasmineDone = safeFn(function () {
  this.tree.root.finish(false);
});

function safeFn(fn) {
  return function () {
    try {
      return fn.apply(this, arguments);
    } catch (ex) {
      warn(ex.message + '\n' + ex.stack);
    }
  };
}

function warn(message) {
  const str = 'WARN - IDE integration: ' + message + '\n';
  try {
    processStderrWrite(str);
  }
  catch (ex) {
    try {
      processStdoutWrite(str);
    }
    catch (ex) {
      // do nothing
    }
  }
}
