package B;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * #104 — step-scope test fixture (App B). Homonymous step defs to A — see
 * apps/src/A/StepDefsA.java javadoc for details.
 */
public class StepDefsB {

    private int count;
    private String supplier;

    @Given("Place a {int}-drink order")
    public void placeAnOrder(int count) {
        System.out.println("[B] Placed an order for " + count + " drinks");
        this.count = count;
    }

    @Given("the supplier is {string}")
    public void theSupplierIs(String name) {
        System.out.println("[B] supplier = " + name);
        this.supplier = name;
    }

    @When("completion-only-B-step")
    public void completionOnlyBStep() {
    }

    @Then("the order is registered for app B")
    public void theOrderIsRegisteredForAppB() {
        if (count <= 0) throw new AssertionError("[B] count not set");
        if (supplier == null) throw new AssertionError("[B] supplier not set");
        System.out.println("[B] order ok — " + count + " drinks from " + supplier);
    }
}
