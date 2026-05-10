package A;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * #104 — step-scope test fixture (App A).
 *
 * Three kinds of step defs to exercise the scope filter:
 *  1. {@code Place a {int}-drink order} — HOMONYM with apps/src/B/StepDefsB.java
 *  2. {@code the customer is "{string}"} — UNIQUE to A
 *  3. {@code completion-only-A-step} — UNIQUE to A, used to check completion
 *
 * Both classes live in the SAME IntelliJ module ("apps") and the SAME source
 * root ("apps/src"), so Cucumber's loadStepsFor sees both. Only Cucumber+'s
 * scope filter (issue #104) isolates them via {@code .cucumber-scope}.
 */
public class StepDefsA {

    private int count;
    private String customer;

    @Given("Place a {int}-drink order")
    public void placeAnOrder(int count) {
        System.out.println("[A] Placed an order for " + count + " drinks");
        this.count = count;
    }

    public int getCount() {
        for (int i = 0; i < 100; i++) {

        }
        System.out.println("[A] Placed an order for " + count + " drinks");
        return count;
    }

    @Given("the customer is {string}")
    public void theCustomerIs(String name) {
        System.out.println("[A] customer = " + name);
        this.customer = name;
    }

    @When("completion-only-A-step")
    public void completionOnlyAStep() {
        // never used in features; only for completion proposal coverage
    }

    @Then("the order is registered for app A")
    public void theOrderIsRegisteredForAppA() {
        if (count <= 0) throw new AssertionError("[A] count not set");
        if (customer == null) throw new AssertionError("[A] customer not set");
        System.out.println("[A] order ok — " + count + " drinks for " + customer);
    }
}
