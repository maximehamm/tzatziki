/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
 *
 * This document is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This work is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package io.nimbly.example

import io.cucumber.java.ParameterType
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.Assert

class Cocktail2 {
    @Given("a global administrator named {string}")
    fun aGlobalAdministratorNamed(arg0: String?) {
    }

    @And("a blog named {string}")
    fun aBlogNamed(arg0: String?) {
    }

    @And("a customer named {string}")
    fun aCustomerNamed(arg0: String?) {
    }

    @And("a blog named {string} owned by {string}")
    fun aBlogNamedOwnedBy(arg0: String?, arg1: String?) {
    }

    @Given("I am logged in as Dr. Bill")
    fun iAmLoggedInAsDrBill() {
    }

    @When("I try to post to {string}")
    fun iTryToPostTo(arg0: String?) {
    }

    @Then("I should see {string}")
    fun iShouldSee(expectedAnswer: String?) {
        Assert.assertEquals(expectedAnswer, "Your article was published.")
    }

    @Given("I am logged in as Greg")
    fun iAmLoggedInAsGreg() {
    }

    @Then("The Cucumber table is formatted !")
    fun theCucumberTableIsAutomaticallyFormatted() {
    }

    @When("I enter any character                                      into {any} or {any} or {any}")
    fun iEnterAnyCharacterIntoNAFOrReadyOrDetails() {
    }

    @When("I enter a pipe anywhere")
    fun iEnterAPipeAnywhere() {
    }

    @Then("A new column is added                                         {any} {any} {any}")
    fun aNewColumnIsAddedNAFReadyMotif() {
    }

    @When("I select cells")
    fun iSelectCells() {
    }

    @Then("I can copy and paste them                                   {any} {any} {any} {any}")
    fun iCanCopyAndPasteThemNAFReadyDetailsSize() {
    }

    @Then("The table is automatically updated")
    fun theCucumberTableIsAutomaticallyUpdated() {
    }

    @Given("Romeo who wants to buy a drink")
    fun romeoWhoWantsToBuyADrink() {
    }

    @Deprecated("")
    @When("kotlin for {any}")
    fun anOrderIsDeclaredForJuliette(s: String?) {
    }

    @Then("there is {int} cocktails in the order")
    fun thereIsCocktailsInTheOrder(arg0: Int) {
        // sdfsf sdfsdf sdfsd sdf sdf
        // dfsdfsf
        // sdfsfsf
        Assert.assertEquals("Wrong number of order !", arg0.toLong(), 1)
    }

    @Then("a message saying {any} is added")
    fun aMessageSayingIsAdded(arg0: String?) {
        Assert.assertEquals(arg0, "Hei!")
    }

    @Then("the ticket must say {any}")
    fun theTicketMustSay(arg0: String?) {
        Assert.assertEquals(arg0, "From Romeo to Jerry: Hei! ")
    }

    @When("a mocked menu is used")
    fun aMockedMenuIsUsed() {
    }

    @And("the mock binds #{int} to mojito")
    fun theMockBindsToMojito(arg0: Int) {
    }

    @And("a cocktail #{int} is added to the order")
    fun aCocktailIsAddedToTheOrder(arg0: Int) {
    }

    @And("the order contains a mojito")
    fun theOrderContainsAMojito() {
    }

    @And("the mock binds #{int} to \${int}")
    fun `theMockBindsTo$`(arg0: Int, arg1: Int) {
    }

    @And("Romeo pays his order")
    fun romeoPaysHisOrder() {
    }

    @Then("the payment component must be invoked {int} time for \${int}")
    fun `thePaymentComponentMustBeInvokedTimeFor$`(arg0: Int, arg1: Int) {
    }

    @ParameterType(".*")
    fun any(value: String): String {
        return value
    }


}