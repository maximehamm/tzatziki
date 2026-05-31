# behave step definitions — paired with ../calculator.feature.
# behave convention: step-def modules live under features/steps/.
#
# Drop a breakpoint on a step body below and run the feature with behave to
# probe IDE step-def navigation / (future) Cucumber+ breakpoint sync.
from behave import given, when, then


@given('a calculator with value {start:d}')
def step_set_value(context, start):
    context.value = start


@when('I add {n:d}')
def step_add(context, n):
    context.value += n


@when('I multiply by {n:d}')
def step_multiply(context, n):
    context.value *= n


@when('I divide by {n:d}')
def step_divide(context, n):
    if n == 0:
        raise ValueError('Cannot divide by zero')
    context.value //= n


@then('the result is {expected:d}')
def step_check(context, expected):
    got = context.value
    assert got == expected, 'Expected %d but got %d' % (expected, got)
