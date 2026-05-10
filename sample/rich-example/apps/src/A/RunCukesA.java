package A;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

/**
 * Runs scenarios from apps/src/A/A.feature using A's step defs.
 *
 * Right-click → Run 'RunCukesA' from inside IntelliJ to validate the test case
 * end-to-end (the Java step defs must compile and Cucumber must resolve to A's
 * methods, not B's).
 */
@RunWith(Cucumber.class)
@CucumberOptions(
    features = "classpath:A",
    glue = "A",
    plugin = {"pretty"}
)
public class RunCukesA {
}
