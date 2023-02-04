if (typeof process.stdin.setRawMode !== 'function') {
  process.stdin.setRawMode = function () {};
}

if (process.argv.indexOf('--watch') != -1 || process.argv.indexOf('--watchAll') != -1) {
  process.stdout.isTTY = true;
}

// DefaultReporter._wrapStdio (jest-cli/build/reporters/default_reporter.js) sets its own process.stdout.write
// that doesn't flushed until tests completed when tests are running in a single process.
process.stdout._intellijOriginalWrite =  process.stdout.write.bind(process.stdout);
