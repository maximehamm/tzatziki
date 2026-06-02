# behave step definitions — paired with ../rocket.feature.
# behave convention: step-def modules live under features/steps/.
#
# Unique "rocket" wording (no collision with the Java/JS samples) so Ctrl+Click
# on a Gherkin step lands here. Drop a breakpoint on a step body and run with
# behave to probe IDE step-def navigation / (future) Cucumber+ breakpoint sync.
from behave import given, when, then


@given('a rocket loaded with {fuel:d} tons of fuel')
def step_load_fuel(context, fuel):
    context.fuel = fuel


@when('I burn {burned:d} tons during ascent')
def step_burn(context, burned):
    context.fuel -= burned


@then('the remaining fuel is {expected:d} tons')
def step_check_fuel(context, expected):
    assert context.fuel == expected, 'Expected %d tons but got %d' % (expected, context.fuel)


@given('a rocket fitted with {count:d} boosters')
def step_fit_boosters(context, count):
    context.boosters = count


@when('I jettison {count:d} boosters')
def step_jettison(context, count):
    context.boosters -= count


@then('{count:d} boosters remain')
def step_check_boosters(context, count):
    assert context.boosters == count, 'Expected %d boosters but got %d' % (count, context.boosters)


@given('a rocket cruising at {altitude:d} km')
def step_cruise(context, altitude):
    context.altitude = altitude


@when('I climb by {delta:d} km')
def step_climb(context, delta):
    context.altitude += delta


@then('the altitude is {expected:d} km')
def step_check_altitude(context, expected):
    assert context.altitude == expected, 'Expected %d km but got %d' % (expected, context.altitude)

@then(u'the xxx is {expected} km')
def step_impl(context, expected):
    raise NotImplementedError(u'STEP: Then the xxx is <expected> km')

@then(u'Aaa')
def step_impl(context):
    raise NotImplementedError(u'STEP: And Aaa')

@then(u'Bbb')
def step_impl(context):
    raise NotImplementedError(u'STEP: And Bbb')