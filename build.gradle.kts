@file:Suppress("PropertyName")

import java.net.URI

allprojects {
    group = "io.nimbly.tzatziki"
    version = "17.0.11"
}

val notes by extra {"""
       <b>Please kindly report any problem... and Rate &amp; Review this plugin !</b><br/>
       <br/>
       Change notes :
       <ul> 
         <li><b>17.0</b> Rewriting breakpoint supports. Now you can set breakpoints from Gherkin ! (Java, Koltin, Scala only) <br/>      
         <li><b>16.6</b> New button to display also files not part of sources/resources path <br/>      
         <li><b>16.5</b> Filter step completion according to tags filtering setup <br/>      
         <li><b>16.3</b> Remove translation stuff : please use 'Translation+' plugin instead !<br/>       
         <li><b>16.2</b> Remove use of deprecated IntelliJ IDEA JDK apis<br/>       
         <li><b>16.1</b> Translate selection using Google Translate (files of any kind, gherkin, java, etc.)<br/>
         <li><b>15.4</b> IntelliJ IDEA 2023.3.2 compatibility</li>
         <li><b>15.3</b> New UI supports</li>
         <li><b>15.2</b> Completion to suggest step parameter types (Java, Kotlin)</li>
         <li><b>15.1</b> Run Cucumber tests from Cucumber+ tool view</li>
         <li><b>15.0</b> Display list of features, let group and filter them by tag names</li>
         <li><b>14.1</b> Kotlin support : generate class and step functions</li>
         <li><b>13.1</b> Java : remove accent from generated method</li>
         <li><b>13.0</b> Java : fix gherkin plugin issue when using '*' as step keyword instead of 'when', 'then', etc.</li>
         <li><b>12.0</b> Upgrade Intellij libs to latest 2022</li>
         <li><b>11.5</b> Allow to fold tables and multi line strings</li>
         <li><b>11.4</b> IntelliJ IDEA 2023.1 compatibility</li>
         <li><b>11.3</b> IntelliJ IDEA 2022.3 compatibility</li>
         <li><b>11.0</b> Tool view to let you select Gherkin tags to filter tests execution, feature exportation to PDF.</li>
         <li><b>10.0</b> Tag completion (cursor location after @)</li>
         <li><b>9.2</b> IntelliJ IDEA 2021.3 compatibility</li>
         <li><b>9.1</b> Export features to PDF now supports Cyrillic. You can also put your prefered font at ".cucumber+/cucumber+.font.ttf" </li>
         <li><b>9.0</b> Line markers indicating the number of uses of step implementations (Java, Kotlin). </li>
         <li><b>8.0</b> Step completion suggests all steps already used in current module, not only steps having an implementation </li>
         <li><b>7.0</b> Breakpoint line markers in Gherkin files (Java, Kotlin) </li>
         <li><b>6.0</b> Run Cucumber test for a single line of a Scenario outline (Java, Kotlin) </li>
         <li><b>5.6</b> IntelliJ IDEA 2021.2 compatibility</li>
         <li><b>5.5</b> Clear tests results annotations using quick fix</li>
         <li><b>5.4</b> Deprecated step inspection for Javascript (and Java, Kotlin)</li>
         <li><b>5.3</b> Deprecated step inspection (instead of annotator... to let it be deactivated if you want)</li>
         <li><b>5.2</b> Navigate from Gerkin step definition to its implementation even after modifing the step text</li>
         <li><b>5.1</b> Deprecated step annotator (Java, Kotlin)</li>
         <li><b>5.0</b> Completion for table header or cells, and markdown images </li>
         <li><b>4.2</b> Tests results annotation is persistent until a scenario is modified </li>
         <li><b>4.1</b> Convert markdown to html while exporting to PDF </li>
         <li><b>4.0</b> Markdown support into header. Including pictures completion and annotation. Pdf ex</li>
         <li><b>3.0</b> Run test then add colors according to tests results</li>
         <li><b>2.4</b> Export PDF landscape or portrait</li>
         <li><b>2.3</b> Ask for review plugin or report bugs and suggestions</li>
         <li><b>2.2</b> More customization for PDF summary</li>
         <li><b>2.1</b> Adding a front page and a summary to the PDF</li>
         <li><b>2.0</b> Exporting feature to PDF</li>
         <li><b>1.5</b> Moving rows and lines uo/down and left/right</li>
         <li><b>1.4</b> Deleting rows and lines improvements</li>
         <li><b>1.3</b> Prevent table structure to be corrupted while using DELETE, BACKSPACE, CUT, etc.</li>
         <li><b>1.2</b> Intellij IDEA 2021.1 EAP Compatibility</li>
         <li><b>1.1</b> Copy from Excel to table</li>
         <li><b>1.0</b> Copy from table to Excel</li>
         <li><b>0.4</b> Add new column by pressing pipe</li>
         <li><b>0.3</b> Add new lines by pressing from line end or from last line</li>
         <li><b>0.2</b> Navigate between cells using tab, backtab and enter</li>
         <li><b>0.1</b> Cucumber table formatting as you go</li>
       </ul>
      """

    /**
     * Supports of markdown
     * - https://github.com/commonmark/commonmark-java
     * - https://github.com/vsch/flexmark-java
     */
}

val versions by extra {
    mapOf(
        "intellij-version" to "IU-2022.3.1",

        "gherkin" to "223.7571.113",        //https://plugins.jetbrains.com/plugin/9164-gherkin/versions
        "properties" to "223.7571.117",     //https://plugins.jetbrains.com/plugin/11594-properties/versions
        "psiViewer" to "223-SNAPSHOT",      //https://plugins.jetbrains.com/plugin/227-psiviewer/versions
        "cucumberJava" to "223.7571.123",   //https://plugins.jetbrains.com/plugin/7212-cucumber-for-java/versions
        "scala" to "2022.3.20",             //https://plugins.jetbrains.com/plugin/1347-scala/versions
    )
}

val versions_211 by extra {
    mapOf(
        "intellij-version" to "IU-211.7142.45",
        "gherkin" to "211.6693.111",
        "cucumberJava" to "211.6693.111",
        "properties" to "211.6693.44",
        "scala" to "2021.1.17")
}

allprojects {

    repositories {
        mavenCentral()
        maven {
            url = URI("https://oss.sonatype.org/content/repositories/snapshots/")
        }
        maven {
            url = URI("https://dl.bintray.com/jetbrains/intellij-plugin-service")
        }
    }
}