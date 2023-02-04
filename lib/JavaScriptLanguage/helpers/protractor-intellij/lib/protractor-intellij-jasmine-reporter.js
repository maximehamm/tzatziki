var Tree = require('./protractor-intellij-tree')
  , processStdoutWrite = process.stdout.write.bind(process.stdout)
  , processStderrWrite = process.stderr.write.bind(process.stderr)
  , stringifier = require('./protractor-intellij-stringifier')
  , util = require('./protractor-intellij-util');

exports.tryAttachReporter = function (browserNode) {
  if (jasmine == null || typeof jasmine.getEnv !== 'function') {
    return false;
  }
  var env = jasmine.getEnv();
  if (env == null || typeof env.addReporter !== 'function') {
    return false;
  }
  env.addReporter(createSafeDelegatingReporter(new JasmineReporter(browserNode)));
  return true;
};

function createSafeDelegatingReporter(reporter) {
  var safeReporter = {};
  for (var key in reporter) {
    //noinspection JSUnfilteredForInLoop
    var method = reporter[key];
    if (typeof method === 'function') {
      //noinspection JSUnfilteredForInLoop
      safeReporter[key] = (function (method) {
        return function () {
          try {
            return method.apply(reporter, arguments);
          } catch (ex) {
            warn(ex.message + '\n' + ex.stack);
          }
        };
      })(method);
    }
  }
  return safeReporter;
}

function createdPatchedSpec(OriginalSpec, registry) {
  function PatchedSpec(attrs) {
    OriginalSpec.apply(this, arguments);
    if (attrs && attrs.id) {
      registry[attrs.id] = this;
    }
  }
  PatchedSpec.prototype = Object.create(OriginalSpec.prototype, {
    constructor: {
      value: PatchedSpec,
      enumerable: false,
      writable: true,
      configurable: true
    }
  });
  return PatchedSpec;
}

/**
 * @param {TestSuiteNode} browserNode test tree
 * @constructor
 */
function JasmineReporter(browserNode) {
  var specRegistry = {};
  if (typeof jasmine.Spec === 'function') {
    jasmine.Spec = createdPatchedSpec(jasmine.Spec, specRegistry);
  }
  this.isSpecDisabled = function (result) {
    var spec = specRegistry[result.id];
    return spec && spec.disabled;
  };
  this.getTotalNotDisabledSpecCount = function () {
    return Object.values(specRegistry).filter((spec) => {
      return !spec.disabled
    }).length;
  };
  this.browserNode = browserNode;
  this.currentSuiteNode = browserNode;
  this.nodeById = {};
}

JasmineReporter.prototype.jasmineStarted = function (options) {
  this.browserNode.tree.addTotalTestCount(this.getTotalNotDisabledSpecCount());
};

/**
 * @param {string} name
 * @param {TestSuiteNode} parentSuiteNode
 * @param {TestSuiteNode} stopNode
 */
function getLocationPath(name, parentSuiteNode, stopNode) {
  var names = [name], n = parentSuiteNode;
  while (n !== stopNode) {
    names.push(n.name);
    n = n.parent;
  }
  names.reverse();
  return util.joinList(names, 0, names.length, '.');
}

JasmineReporter.prototype.suiteStarted = function (result) {
  var locationPath = getLocationPath(result.description, this.currentSuiteNode, this.browserNode);
  var suiteNode = this.currentSuiteNode.addTestSuiteChild(result.description, 'suite', locationPath);
  suiteNode.start();
  this.currentSuiteNode = suiteNode;
};

JasmineReporter.prototype.suiteDone = function (result) {
  var suiteNode = this.currentSuiteNode;
  if (suiteNode == null) {
    return warn('No current suite to finish');
  }
  if (suiteNode.name !== result.description) {
    return warn('Suite name mismatch, actual: ' + suiteNode.name + ', expected: ' + result.description);
  }
  suiteNode.finish(false);
  this.currentSuiteNode = suiteNode.parent;
};

/**
 * @param {jasmine.Result} result
 */
JasmineReporter.prototype.specStarted = function (result) {
  if (this.isSpecDisabled(result)) {
    return;
  }
  var locationPath = getLocationPath(result.description, this.currentSuiteNode, this.browserNode);
  var specNode = this.currentSuiteNode.addTestChild(result.description, 'test', locationPath);
  specNode.startTimeMillis = new Date().getTime();
  specNode.start();
  if (this.nodeById[result.id] != null) {
    warn('jasmine error, specStarted with not unique result.id: ' + result.id)
  }
  this.nodeById[result.id] = specNode;
};

/**
 * @param {jasmine.Result} result
 */
JasmineReporter.prototype.specDone = function (result) {
  if (this.isSpecDisabled(result)) {
    return;
  }
  var specNode = this.nodeById[result.id];
  if (specNode == null) {
    return warn('Cannot find specNode by id ' + result.id);
  }
  var durationMillis;
  if (typeof specNode.startTimeMillis === 'number') {
    durationMillis = new Date().getTime() - specNode.startTimeMillis;
  }
  var passed = result.status === 'passed';
  var failureMsg, failureDetails, expectedStr, actualStr;
  if (result.failedExpectations.length > 0) {
    var failedExpectation = result.failedExpectations[0];
    failureMsg = failedExpectation.message || '';
    failureDetails = failedExpectation.stack;
    if (failedExpectation.expected !== failedExpectation.actual) {
      expectedStr = stringifier.stringify(failedExpectation.expected);
      actualStr = stringifier.stringify(failedExpectation.actual);
    }
  }
  var outcome;
  if (result.status === 'passed') {
    outcome = Tree.TestOutcome.SUCCESS;
  }
  else if (result.status === 'pending' || result.status === 'disabled') {
    outcome = Tree.TestOutcome.SKIPPED;
  }
  else {
    outcome = Tree.TestOutcome.FAILED;
    if (result.status !== 'failed') {
      failureMsg = (failureMsg || '') + '\nUnexpected spec status:' + result.status;
    }
  }
  specNode.setOutcome(outcome, durationMillis, failureMsg, failureDetails, expectedStr, actualStr, null, null);
  specNode.finish(false);
};

JasmineReporter.prototype.jasmineDone = function () {
};

function warn(message) {
  var str = 'WARN - IDE integration: ' + message + '\n';
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
