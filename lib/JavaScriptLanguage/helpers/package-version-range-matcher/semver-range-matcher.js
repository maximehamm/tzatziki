var semver = require('semver');

function match(requests) {
  var responses = [];
  requests.forEach(function (request) {
    var response = {};
    response.packageName = request.packageName;
    response.versionRange = request.versionRange;
    response.version = request.version;
    if (!semver.valid(request.version)) {
      response.invalidVersion = true;
    }
    else if (!semver.validRange(request.versionRange)) {
      response.invalidVersionRange = true;
    }
    else {
      response.matched = semver.satisfies(request.version, request.versionRange);
    }
    responses.push(response);
  });
  return responses;
}

exports.match = match;
