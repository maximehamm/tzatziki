var toString = {}.toString;

/**
 * @param {*} value
 * @return {boolean}
 */
function isString(value) {
  return typeof value === 'string' || toString.call(value) === '[object String]';
}

/**
 * @param {Array.<string>} list
 * @param {number} fromInclusive
 * @param {number} toExclusive
 * @param {string} delimiterChar one character string
 * @returns {string}
 */
function joinList(list, fromInclusive, toExclusive, delimiterChar) {
  if (list.length === 0) {
    return '';
  }
  if (delimiterChar.length !== 1) {
    throw Error('Delimiter is expected to be a character, but "' + delimiterChar + '" received');
  }
  var addDelimiter = false
    , escapeChar = '\\'
    , escapeCharCode = escapeChar.charCodeAt(0)
    , delimiterCharCode = delimiterChar.charCodeAt(0)
    , result = ''
    , item
    , itemLength
    , ch
    , chCode;
  for (var itemId = fromInclusive; itemId < toExclusive; itemId++) {
    if (addDelimiter) {
      result += delimiterChar;
    }
    addDelimiter = true;
    item = list[itemId];
    itemLength = item.length;
    for (var i = 0; i < itemLength; i++) {
      ch = item.charAt(i);
      chCode = item.charCodeAt(i);
      if (chCode === delimiterCharCode || chCode === escapeCharCode) {
        result += escapeChar;
      }
      result += ch;
    }
  }
  return result;
}

exports.isString = isString;
exports.joinList = joinList;
