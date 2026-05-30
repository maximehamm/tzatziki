// TypeScript step definitions — paired with the @TsBackend scenarios in
// calculator.feature. Place a breakpoint on any of the body lines below to
// validate that Cucumber+ can install a tzatziki.cucumber.code breakpoint on a
// TypeScript step-def and that Gherkin <-> TS step navigation works.
import { Given, When, Then } from 'cucumber';

let tsValue: number = 0;

Given(/^a typed calculator with value (\d+)$/, function (start: string) {
    tsValue = parseInt(start, 10);
});

When(/^I multiply by (\d+)$/, function (n: string) {
    tsValue = tsValue * parseInt(n, 10);
});

When(/^I divide by (\d+)$/, function (n: string) {
    const divisor = parseInt(n, 10);
    if (divisor === 0) throw new Error('Cannot divide by zero');
    tsValue = Math.floor(tsValue / divisor);
});

Then(/^the typed result is (\d+)$/, function (expected: string) {
    const got = tsValue;
    if (got !== parseInt(expected, 10)) {
        throw new Error(`Expected ${expected} but got ${got}`);
    }
});
