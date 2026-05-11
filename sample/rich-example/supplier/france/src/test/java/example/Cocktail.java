package example;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.java.ro.Dar;

import java.util.Date;

public class Cocktail {

    Integer order;

    @Given("I have the following books in the store")
    public void iHaveTheFollowingBooksInTheStore(DataTable table) {
        
    }

    @SuppressWarnings("test")
    @Deprecated
    @Given("Romeo who wants to buy a drink")
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

    @Given("the following users and scores:")
    public void theFollowingUsersAndScores(DataTable table) {
    }

    @Given("Bidule trucs")
    public void biduleTrucs() {
    }


    @Given("l'utilisateur {string} a {int} ans")
    public void lUtilisateurAAns(String prenom, Integer  age) {
        
    }

    @Then("son score devrait être {}")
    public void sonScoreDevraitEtre(String arg0) {
        truc();
    }

    private void truc() {
    }
}
