package io.nimbly.tzatziki.pdf

import io.nimbly.tzatziki.util.now
import java.time.format.DateTimeFormatter

private const val TAG = "@tablesOfContentTag#"

class PdfBuilder(

    val style: PdfStyle,

    private val tableOfContents: TableOfContents) {
    private var sb = StringBuilder()
    private var output = StringBuilder()
    private var isTableOfContentsInserted = false

    fun addTableOfContentsEntry(level: Int, label: String) {
        tableOfContents.addEntry(level, label)
        output.append("\n").append("<div id=\"${tableOfContents.currentId}\"></div>")
    }

    fun breakPage() {
        output.append("\n").append("<page-before></page-before>")
    }

    fun append(content: String) {
        output.append("\n").append(content)
    }

    fun addParagraph(content: String) {
        paragraphStarts()
        append(content)
        paragraphEnds()
    }

    fun paragraphStarts() {
        output.append("\n").append("<page-inside-avoid>")
    }

    fun paragraphEnds() {
        output.append("\n").append("</page-inside-avoid>")
    }

    fun insertTableOfContents() {
        if (isTableOfContentsInserted)
            throw Exception("Table of Contents is already inserted")
        output.append("\n")
        output.append(TAG)
        isTableOfContentsInserted = true
    }

    private fun insertTableOfContent() {
        val pos = output.indexOf(TAG)
        if (pos > -1) {
            val before = output.toString().substringBefore(TAG)
            val after = output.toString().substringAfter(TAG)
            output = StringBuilder()
            output.append(before)
            output.append(tableOfContents.generate())
            output.append(after)
        }
    }

    fun generate(): String {
        sb.append("\n<html>")
        sb.append("\n<head>")
        sb.append("\n<style>")

        style.apply {
            first?.apply {
                sb.append("\n@page:first {")
                sb.append('\n' + pagePdfStyle)
                sb.append('\n' + updatePagePdfStyle)
                sb.append("}")
            }
            sb.append("\n@page {")
            sb.append('\n' + pagePdfStyle)
            sb.append('\n' + updatePagePdfStyle)
            sb.append("}")
            sb.append(standardPdfStyle)
            sb.append(contentStyle)
        }

        sb.append("\n</style>")
        sb.append("\n</head>")
        sb.append("\n<body>\n")
        insertTableOfContent()
        sb.append(output)
        sb.append("\n</body>")
        sb.append("\n</html>")
        return sb.toString()
    }
}

open class PdfStyle(
    var bodyFontSize: String = "16px",
    var topFontSize: String = "16px",
    var bottomFontSize: String = "12px",
    var topLeft: String = "Company Name",
    var topCenter: String = "",
    var topRight: String = now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")),
    var bottomLeft: String = "Cucumber+",
    var bottomCenter: String = "",
    var bottomRight: String = "'Page ' counter(page) ' / ' counter(pages);",
    var first: PdfStyle? = null)
{

    val defaultPagePdfStyle = """
        size: a4;
        margin: 50px;	
        
        @top-left{
              font-size:$topFontSize;
              content: '$topLeft';
        }
        
        @top-center{
              font-size:$topFontSize;
              content: '$topCenter';
        }
        
        @top-right{
              font-size:$topFontSize;
              content: '$topRight';
        }
        
        @bottom-left{
            font-size:$bottomFontSize;
            content: '$bottomLeft';	
        }
        
        @bottom-center {
            font-size:$bottomFontSize;
            content: '$bottomCenter';	
        }
        
        @bottom-right {
             font-size:$bottomFontSize;
              content:$bottomRight 	
        }""".trimIndent()

    val defaultStandardPdfStyle = """    
        /* The body margin is in addition to the page margin,
         * but the top body margin only applies to the first page and 
         * the bottom margin to the last page. */
        body {
           margin: 0;
           font-size:$bodyFontSize;
        }
        
        page-after {
          /* Most page elements only work on block or block-like elements. */
          display: block;
          /* Create a page break after this element. */
          page-break-after: always;	
        }
        
        page-before {
          display: block;
          /* Create a page break before this element. */
          page-break-before: always;	
        }
        
        .toc li::after {
          /* The target-counter function is useful for creating a 
           * table-of-contents or directing the user to a specific page.
           * It takes as its first argument the hash link (in the form #id)
           * to the element and returns the page that element is located on.
           * We can use the attr function to pick up the href from the html. */
          content: leader(dotted) target-counter(attr(href), page);
        }
        
        page-inside-avoid {
          display:block;
          page-break-inside: avoid;
        }
        
        running {
          /* We mark this element as running by using the running function 
           * to specify a named position. The name can be any valid CSS identifier.
           * See the @page rule above. */
          position: running(header);
        }
        
        /* The widows property allows us to specify the minimum number of lines
         * to fall onto the next page, if there is a page break inside our element.
         * For example, you can use this to avoid a single line falling onto a
         * new page. The widows property is satisfied by inserting space above 
         * the widows count of lines to make them fall onto a new page.
         *
         * Try: Changing widows to 0 and seeing how many lines are left on the new
         * page. The default initial value of widows is 2.
         */
        
        widows {
          padding: 0 10px;
          border: 1px solid red;
          page-break-before: always;
          display: block;
          widows: 5;
          line-height: 20px;
          font-size: 16px;
          margin-top: 698px;
        }
        
        spacer {
          page-break-before: always;
          display:block;
          height: 878px;	
        }
        spacer.four-lines {
          height: 798px;	
        }
        
        /* Orphans property is the pair of widows. It allows the author to specify
         * the minimum number of lines that should occur on the page before a 
         * page-break. For example, we might want to prevent one line on the first page,
         * followed by ten lines on the next.
         * This property is satisfied by adding a new page before the element, if the 
         * orphans constraint is violated.
         */
        orphans {
          padding: 0 10px;
          border: 1px solid green;
          display: block;
          widows: 0;
          orphans: 3;
          line-height: 20px;
          font-size: 16px;
        }
        
        named-page {
          page-break-before: always;
          /* The page property allows us to marry up an element with a @page rule. */
          page: named;
          display: block;
        } 
        
        a{
            color:black;
            text-decoration: none;
        }""".trimIndent()

    val defaultContentStyle = """
        td, th {
            border-bottom: 1px dashed gray;
        }
        
        table {
            /* With -fs-table-paginate on, the header and footer
             * of a table will be repeated on each page that the table falls on.
             */
            -fs-table-paginate: paginate;
        
            /* Similar to the orphans property, this property allows the author
             * to specify how much of the block must show before a page-break.
             * If the constraint is violated, a page break is added before the element.
             * Very useful on elements not made of lines, such as tables, etc.
             * TRY: Uncomment this property and see how the table moves to a new page
             * to satisfy the constraint.
             */
            /* -fs-page-break-min-height: 100px; */
        }""".trimIndent()

    var pagePdfStyle = defaultPagePdfStyle
    var updatePagePdfStyle = defaultPagePdfStyle
    var standardPdfStyle = defaultStandardPdfStyle
    var contentStyle = defaultContentStyle
}
