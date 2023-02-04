var util = require('./intellij-util');

/**
 * @param {*} value
 * @return {string}
 */
function stringify(value) {
  var str = doStringify(value);
  if (util.isString(str)) {
    return str;
  }
  return 'Oops, something went wrong: IDE failed to stringify ' + typeof value;
}

/**
 * @param {*} value
 * @return {string}
 */
function doStringify(value) {
  if (util.isString(value)) {
    return value;
  }
  var normalizedValue = deepCopyAndNormalize(value);
  if (normalizedValue instanceof RegExp) {
    return normalizedValue.toString();
  }
  if (normalizedValue === undefined) {
    return 'undefined';
  }
  let bigintUniqueMarker;
  const result = JSON.stringify(normalizedValue, (key, value) => {
    if (typeof value === 'bigint') {
      // BigInt is not supported by JSON.stringify out-of-the-box
      if (bigintUniqueMarker == null) {
        bigintUniqueMarker = ('bigint-marker-n5ghs8iyzja-' + process.pid + '-' + process.uptime()).replace(/[-.]/g, '');
      }
      return bigintUniqueMarker + value.toString();
    }
    return value;
  }, 2);
  if (bigintUniqueMarker != null) {
    return result.replace(new RegExp('"' + bigintUniqueMarker + '(-?\\d+)"', 'g'), (_, numberStr) => numberStr + 'n');
  }
  return result;
}

function isObject(val) {
  return val === Object(val);
}

function deepCopyAndNormalize(value) {
  var cache = [];
  return (function doCopy(value) {
    if (value == null) {
      return value;
    }
    if (typeof value === 'number' || typeof value === 'boolean' || typeof value === 'string') {
      return value;
    }
    if (value instanceof RegExp) {
      return value;
    }

    if (cache.indexOf(value) !== -1) {
      return '[Circular reference found] Truncated by IDE';
    }
    cache.push(value);

    if (Array.isArray(value)) {
      return value.map(function (element) {
        return doCopy(element);
      });
    }

    if (isObject(value)) {
      var keys = Object.keys(value);
      keys.sort();
      var ret = {};
      keys.forEach(function (key) {
        ret[key] = doCopy(value[key]);
      });
      return ret;
    }

    return value;
  })(value);
}

exports.stringify = stringify;
