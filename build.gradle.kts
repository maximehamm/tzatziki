@file:Suppress("PropertyName")

import java.net.URI

allprojects {
    group = "io.nimbly.tzatziki"
    version = "22.0.0"
}

val notes by extra {"""
       <b>Please kindly report any problem... and Rate &amp; Review this plugin !</b><br/>
       <br/>
       Change notes :
       <ul>

         <li><b>22.0.0</b> <b>JavaScript &amp; TypeScript support</b> — Cucumber+ now works with cucumber-js step definitions, just like Java/Kotlin/Scala: set a breakpoint on a Gherkin step and it syncs to the matching <code>Given/When/Then(...)</code> in your <code>.js</code>/<code>.ts</code> file (and back), enable/disable propagates both ways, the debugger stops only on the example row you flag, and step-defs get the "used by N scenarios" gutter icon. Requires the bundled <i>JavaScript</i> support (Ultimate / WebStorm) and the <i>Cucumber.js</i> plugin. <i>(TypeScript step-defs need <code>-r ts-node/register</code> in the run configuration.)</i><br/>This feature is brand new and hasn't been tested on large real-world projects yet — kind feedback and bug reports are very welcome!</li>
         <li><b>21.8.14</b> Cucumber test tree polish: <code>Example #N</code> nodes now show their row values (<code>Age: 22, Score: 75, Prenom: Clara</code>), step parameters (quoted strings, numbers, <code>&lt;placeholders&gt;</code>) render in grey italic, and inherited tags no longer leak onto Example or step nodes.</li>
         <li><b>21.8.12</b> Enable/disable on a Gherkin step breakpoint now propagates to the paired code breakpoint (was only working in the code → Gherkin direction).</li>
         <li><b>21.8.11</b> Fix breakpoints set on Kotlin step definitions not stopping the debugger.</li>
         <li><b>21.8.9</b> Fix editor freezes during autocompletion when breakpoints are present — the Gherkin&#8596;Java/Kotlin breakpoint sync no longer re-scans the project on every keystroke.</li>
         <li><b>21.8.7</b> Test tree decoration on Windows + WSL: some Cucumber runners don't publish SMTRunner events on the project bus — our listener was silently never fired so the file-name + feature title decoration was missing. Now we (1) subscribe to BOTH the project AND application message bus, and (2) lazily run the decoration from the styled renderer on first paint as a safety net.</li>
         <li><b>21.8.6</b> Test tree suite label on Windows + WSL: more robust VFS resolution of cucumber-jvm <code>file:///…</code> location URLs — tries <code>findFileByUrl</code>, then <code>URI.path</code> with leading-slash normalisation for Windows drive letters (<code>/C:/…</code> → <code>C:/…</code>), then a brute-force fallback. The <code>file.feature  /  Feature title [Business Need title]</code> label now appears correctly on Windows + WSL setups.</li>
         <li><b>21.8.5</b> Fix #124 — 20s+ editor freezes on IntelliJ 2026.1.x EAP: the overridden <code>GotoDeclaration</code> action (<code>TzGotoStepDefAction</code>) is now declared with explicit <code>text</code> and <code>description</code> attributes, satisfying the platform's stricter menu validation that was throwing <code>PluginException: Empty menu item text</code> on every menu repaint. Also: test tree wrapper "Cucumber+" rendered in default style (no bold), and Windows-side VFS resolution falls back to <code>LocalFileSystem.findFileByPath</code> when <code>findFileByUrl</code> returns null on <code>file:///C:/…</code> URLs — restores the <code>file.feature  /  Feature title [Business Need title]</code> suite label that was collapsing to <code>file.feature  /  Business Need title</code> on Windows.</li>
         <li><b>21.8.4</b> Cucumber test tree: split the file name from the feature header label (file in default style, header in grey). Fix the dbl-click-to-navigate handler on Windows by mirroring the platform's <code>mouseClicked</code> event (was <code>mousePressed</code> — consume didn't propagate cross-event on Windows AWT). Also walks the file root when resolving feature/business-need keyword pairs, in case the parser splits them into sibling features.</li>
         <li><b>21.8.3</b> Cucumber test tree: surface Gherkin tags (e.g. <code>@Production @Chrome</code>) at the end of each suite node in grey — Feature-level tags written above the keyword are surfaced on the top suite, scenario / outline / background tags on their own node. Also catches tree-replacement on test re-runs so double-click-to-navigate keeps working when IntelliJ reuses the run tab.</li>
         <li><b>21.8.2</b> Cucumber test tree styled rendering: the outermost feature suite renders as multi-fragment text — file name in grey, primary header (Feature) in <b>bold</b>, secondary headers (Business Need / Ability) in <i>grey italic</i> between brackets. The "Cucumber+" wrapper node is also bolded. No change to behaviour, just a clearer visual.</li>
         <li><b>21.8.1</b> Fix #122 — extreme CPU usage in 21.8.0: replaced the global ToolWindowManagerListener that re-traversed the Run/Debug/Services component trees on every UI state change with a per-tool-window ContentManagerListener that scans only newly added test contents. Also drops usage of the internal <code>PluginManagerCore.getPlugin(PluginId)</code> flagged by the Marketplace verifier.</li>
         <li><b>21.8.0</b> Cucumber test tree polish: rename the wrapper category to <code>Cucumber+</code>, show the feature file name + every header (Business Need / Ability / Feature) on the top suite node (e.g. <code>France.feature  /  Cocktail Ordering [toto]</code>), and make double-click on a Feature / Scenario node open the .feature file at that line instead of just toggling the tree expansion.</li>
         <li><b>21.7.3</b> <code># @header: column</code> highlight now spans the whole logical table — comments / blank lines interleaved between rows no longer truncate the coloring.</li>
         <li><b>21.7.2</b> Test result highlights: wipe before repaint so re-running a scenario no longer stacks fresh highlights on top of the previous run.</li>
         <li><b>21.7.1</b> Description folding: correctly skip the Feature keyword + name line when <code>Business Need:</code> precedes <code>Feature:</code> on adjacent lines — the fold now starts at the actual prose, not the name.</li>
         <li><b>21.7.0</b> Folding for Gherkin descriptions (Feature / Business Need / Scenario / Scenario Outline / Background / Rule) with one-line teaser and per-file persistence — works across all Gherkin dialects (English, Chinese, French…). The <code># @header: row|column</code> annotation folds to a discreet <code>Header row</code> / <code>Header column</code> placeholder; expands on caret entry and re-collapses on exit. Chinese (zh-CN) sample feature added under <code>sample/rich-example/supplier/chinese</code>.</li>
         <li><b>21.6.3</b> Breakpoints on Gherkin tables are now restricted to <code>Examples:</code> rows (excluding the header). Top-border drag zone tightened — no longer bleeds onto the line above.</li>
         <li><b>21.6.2</b> Fix #115 — CJK (Chinese / Japanese / Korean) PDF export: drop a CJK TTF (e.g. Noto Sans SC) at <code>.cucumber+/cucumber+.font.ttf</code> and characters render correctly.</li>
         <li><b>21.6.0</b> Reorder Gherkin table rows and columns by drag-and-drop — grab the top frame for a column, the left frame for a row. An orange marker now also flags the affected row / column on shift, add, and while the table popup is open. Fix #109: shift actions no longer ship with default <code>Shift+Ctrl+Arrow</code> shortcuts (they clashed with platform bindings) — users can rebind them via Settings → Keymap.</li>
         <li><b>21.5.1</b> Fix #104 — Create-step picker honours <code>.cucumber-scope</code>, and an unresolved step that only matches a step def outside the scope is now correctly reported. Compatibility fix for IntelliJ IDEA 2026.2 EAP.</li>
         <li><b>21.5.0</b> Custom Cucumber+ breakpoint type — green disc on Gherkin, green badge over the standard red dot on Java/Kotlin — replacing the legacy <code>"Cucumber+"!=null</code> condition trick. Plain Java line breakpoints on step-def body lines are auto-promoted and synced with Gherkin.</li>
         <li><b>21.4.0</b> Fix #104 — per-app step indexing scope: drop a <code>.cucumber-scope</code> file at the root of an app folder and Cucumber+ narrows step resolution, completion and Find Usages to that folder. Goto Declaration on a Gherkin step honours the scope filter.</li>
         <li><b>21.3.0</b> Fix #103 — step-usages popup shows feature file, line, scenario name and step text; richer gutter tooltip when there is a single usage.</li>
         <li><b>21.2.5</b> Cucumber+ tool window: coalesce tag-tree refreshes. Reduces contention on slow filesystems (WSL UNC, network drives) and improves responsiveness on large projects.</li>
         <li><b>21.2.4</b> Fix #120 — regression introduced in 21.2.3: clicking the run gutter on a Scenario Outline example was running every example instead of only the clicked one.</li>
         <li><b>21.2</b> Table renderer: insert and delete rows/columns directly from the table popup, with modernized shift / add / delete icons. Shift actions are no longer added to the general and right-click menus — they remain available from the table popup and from keyboard shortcuts (<code>Shift+Ctrl+Arrow</code>). IntelliJ IDEA 2025.3.4 compatibility: fix Gherkin&#8596;Java/Kotlin breakpoint synchronization, restore the test execution highlighter, eliminate slow operations on EDT.</li>
         <li><b>21.1</b> Table renderer: header support — add <code># @header: row</code> or <code># @header: column</code> above a DataTable to highlight its first row or column as a header. Hover the table frame to reveal a context menu: toggle header type, shift rows/columns, and toggle Cucumber+</li>
         <li><b>21.0</b> Table renderer: Gherkin tables are now visually framed with rounded-corner borders and subtle column separators directly in the editor</li>
         <li><b>20.0</b> Minimum version is now 2025.3.3 - Kotlin 2.0 - Fix #94</li>
        
         <li><b>18.6</b> Fix #98, #102, #112</li>
         <li><b>18.5</b> Fix #118, #119, #80, #99</li>
         <li><b>18.4</b> Fix test result highlights refresh</li>
         <li><b>18.3</b> Fix breakpoint set and un unset issue</li>
         <li><b>18.2</b> IntelliJ IDEA 2026.1 compatibility</li>
         <li><b>18.1</b> IntelliJ IDEA 2025.2 compatibility</li>
         <li><b>18.0</b> IntelliJ IDEA 2025.1.1 compatibility</li>
         <li><b>17.0</b> Rewriting breakpoint supports. Now you can set breakpoints from Gherkin ! (Java, Kotlin, Scala only) <br/>      
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

//    const val PLATFORM_TYPE_ANDROID_STUDIO = "AI"
//    const val PLATFORM_TYPE_CLION = "CL"
//    const val PLATFORM_TYPE_GATEWAY = "GW"
//    const val PLATFORM_TYPE_GOLAND = "GO"
//    const val PLATFORM_TYPE_INTELLIJ_COMMUNITY = "IC"
//    const val PLATFORM_TYPE_INTELLIJ_ULTIMATE = "IU"
//    const val PLATFORM_TYPE_PHPSTORM = "PS"
//    const val PLATFORM_TYPE_PYCHARM = "PY"
//    const val PLATFORM_TYPE_PYCHARM_COMMUNITY = "PC"
//    const val PLATFORM_TYPE_RIDER = "RD"
//    const val PLATFORM_TYPE_RUSTROVER = "RR"
//    const val PLATFORM_TYPE_FLEET = "FLIJ"

//val versions by extra {
//    mapOf(
//        "intellij-version" to "IU-2022.3.1",
//        "cucumberJavascript" to "223.7571.113", // https://plugins.jetbrains.com/plugin/7418-cucumber-js
//        "gherkin" to "223.7571.113",            //https://plugins.jetbrains.com/plugin/9164-gherkin/versions
//        "properties" to "223.7571.117",         //https://plugins.jetbrains.com/plugin/11594-properties/versions
//        "psiViewer" to "223-SNAPSHOT",          //https://plugins.jetbrains.com/plugin/227-psiviewer/versions
//        "cucumberJava" to "223.7571.123",       //https://plugins.jetbrains.com/plugin/7212-cucumber-for-java/versions
//        "scala" to "2022.3.20",                 //https://plugins.jetbrains.com/plugin/1347-scala/versions
//    )
//}

val versions by extra {
    mapOf(
        "intellij-version" to "IU-2025.3.3",

        "gherkin" to "253.28294.218",       //https://plugins.jetbrains.com/plugin/9164-gherkin/versions
        "psiViewer" to "252.23892.248",     //https://plugins.jetbrains.com/plugin/227-psiviewer/versions
        "cucumberJava" to "253.30387.20",   //https://plugins.jetbrains.com/plugin/7212-cucumber-for-java/versions
        "cucumberJavascript" to "253.28294.218", //https://plugins.jetbrains.com/plugin/7418-cucumber-js/versions
        "python" to "253.31033.145",        //https://plugins.jetbrains.com/plugin/631-python (Pythonid, Pro)
        "scala" to "2025.3.39",             //https://plugins.jetbrains.com/plugin/1347-scala/versions
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