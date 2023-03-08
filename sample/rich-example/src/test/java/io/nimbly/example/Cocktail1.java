package io.nimbly.example;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

public class Cocktail1 {

    Integer order;

    @Given("I have the following books in the store")
    public void iHaveTheFollowingBooksInTheStore() {
    }

    @SuppressWarnings("test")
    @Given("Romeo who wants to buy a drink")
    @When("xx an order is declared for Juliette")
    public void anOrderIsDeclaredForJuliette() {
        order = 0;
    }

    @Deprecated
    @When("java for {any}")
    @When("nothing")
    public void anOrderIsDeclaredForJuliette(String s) {
    }

}



















