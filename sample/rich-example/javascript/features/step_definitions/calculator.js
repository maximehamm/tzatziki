// JavaScript step definitions — paired with the @JsBackend scenarios in
// calculator.feature. Place a breakpoint on any of the body lines below.
const { Given, When, Then } = require('@cucumber/cucumber');

let jsValue = 0;

Given(/^a calculator with value (\d+)$/, function (start) {
    jsValue = parseInt(start, 10);
});

When(/^I add (\d+)$/, function (n) {
    jsValue = jsValue + parseInt(n, 10);
});

Then(/^the result is (\d+)$/, function (expected) {
    const got = jsValue;
    if (got !== parseInt(expected, 10)) {
        throw new Error('Expected ' + expected + ' but got ' + got);
    }
});
