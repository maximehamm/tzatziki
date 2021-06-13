package io.nimbly.example;

import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

public class Cocktail {

    @Deprecated
    @When("java for {any}")
    public void anOrderIsDeclaredForJuliette(String s) {
    }

    @When("an order is declared for Juliette")
    public void anOrderIsDeclaredForJuliette() {
        int x =0;
        x++;
    }

    @Given("I have the following books in the store")
    public void iHaveTheFollowingBooksInTheStore() {
    }
}
