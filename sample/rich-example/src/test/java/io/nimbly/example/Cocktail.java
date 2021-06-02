package io.nimbly.example;

import io.cucumber.java.ParameterType;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.Assert.assertEquals;

public class Cocktail {

    @Given("a global administrator named {string}")
    public void aGlobalAdministratorNamed(String arg0) {
    }

    @And("a blog named {string}")
    public void aBlogNamed(String arg0) {
    }

    @And("a customer named {string}")
    public void aCustomerNamed(String arg0) {
    }

    @And("a blog named {string} owned by {string}")
    public void aBlogNamedOwnedBy(String arg0, String arg1) {
    }

    @Given("I am logged in as Dr. Bill")
    public void iAmLoggedInAsDrBill() {
    }

    @When("I try to post to {string}")
    public void iTryToPostTo(String arg0) {
    }

    @Then("I should see {string}")
    public void iShouldSee(String expectedAnswer) {
        assertEquals(expectedAnswer, "Your article was published.");
    }

    @Given("I am logged in as Greg")
    public void iAmLoggedInAsGreg() {
    }


    @Then("The Cucumber table is formatted !")
    public void theCucumberTableIsAutomaticallyFormatted() {

    }

    @When("I enter any character                                      into {any} or {any} or {any}")
    public void iEnterAnyCharacterIntoNAFOrReadyOrDetails() {

    }

    @When("I enter a pipe anywhere")
    public void iEnterAPipeAnywhere() {

    }

    @Then("A new column is added                                         {any} {any} {any}")
    public void aNewColumnIsAddedNAFReadyMotif() {

    }

    @When("I select cells")
    public void iSelectCells() {

    }

    @Then("I can copy and paste them                                   {any} {any} {any} {any}")
    public void iCanCopyAndPasteThemNAFReadyDetailsSize() {

    }

    @Then("The table is automatically updated")
    public void theCucumberTableIsAutomaticallyUpdated() {

    }

    @Given("Romeo who wants to buy a drink")
    public void romeoWhoWantsToBuyADrink() {
    }

    @Deprecated
    @When("an order is declared for {any}")
    public void anOrderIsDeclaredForJuliette(String s) {
    }

    @Then("there is {int} cocktails in the order")
    public void thereIsCocktailsInTheOrder(int arg0) {
        assertEquals("Wrong number of order !", arg0, 1);
    }

    @Then("a message saying {any} is added")
    public void aMessageSayingIsAdded(String arg0) {
        assertEquals(arg0, "Hei!");
    }

    @Then("the ticket must say {any}")
    public void theTicketMustSay(String arg0) {
        assertEquals(arg0, "From Romeo to Jerry: Hei! ");
    }

    @When("a mocked menu is used")
    public void aMockedMenuIsUsed() {
    }

    @And("the mock binds #{int} to mojito")
    public void theMockBindsToMojito(int arg0) {
    }

    @And("a cocktail #{int} is added to the order")
    public void aCocktailIsAddedToTheOrder(int arg0) {
    }

    @And("the order contains a mojito")
    public void theOrderContainsAMojito() {
    }

    @And("the mock binds #{int} to ${int}")
    public void theMockBindsTo$(int arg0, int arg1) {
    }

    @And("Romeo pays his order")
    public void romeoPaysHisOrder() {
    }

    @Then("the payment component must be invoked {int} time for ${int}")
    public void thePaymentComponentMustBeInvokedTimeFor$(int arg0, int arg1) {
    }

    @ParameterType(".*")
    public String any(String value) {
        return value;
    }
}
