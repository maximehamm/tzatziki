package B;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

/**
 * Runs scenarios from apps/src/B/B.feature using B's step defs.
 */
@RunWith(Cucumber.class)
@CucumberOptions(
    features = "classpath:B",
    glue = "B",
    plugin = {"pretty"}
)
public class RunCukesB {
}
