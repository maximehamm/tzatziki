package example;

import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

public class Cocktail {


    @SuppressWarnings("test")
    @Given("Romeo who wants to buy a drink")
    @When("xx an order is declared for Juliette")
    public void anOrderIsDeclaredForJuliette() {
        order = 0;
    }

    @Deprecated
    @When("java for {mydate}")
    @When("nothing")
    public void anOrderIsDeclaredForJuliette(String s) {
    }

    @ParameterType(".*")
    public Date mydate(String date)  {
        return null;
    }
}
