package io.nimbly.tzatziki.pdf

private const val TAG = "@tablesOfContentTag#"

class PdfBuilder(private val style: PdfStyle) {

    private var sb = StringBuilder()
    private var out = StringBuilder()
    private var isSummaryInserted = false
    private val summary = Summary()

    fun addSummaryEntry(level: Int, label: String) {
        summary.addEntry(level, label)
        out.append("\n").append("<div id=\"${summary.currentId}\"></div>")
    }

    fun breakPage() {
        out.append("\n").append("<page-before></page-before>")
    }

    fun append(content: String) {
        out.append("\n").append(content)
    }

    fun addParagraph(content: String) {
        paragraphStarts()
        append(content)
        paragraphEnds()
    }

    fun paragraphStarts() {
        out.append("\n").append("<page-inside-avoid>")
    }

    fun paragraphEnds() {
        out.append("\n").append("</page-inside-avoid>")
    }

    fun insertSummary() {
        if (isSummaryInserted)
            throw Exception("Table of Contents is already inserted")
        out.append("\n")
        out.append(TAG)
        isSummaryInserted = true
    }

    private fun insertSummaryNow() {
        val pos = out.indexOf(TAG)
        if (pos > -1) {
            val before = out.toString().substringBefore(TAG)
            val after = out.toString().substringAfter(TAG)
            out = StringBuilder()
            out.append(before)
            out.append(summary.generate())
            out.append(after)
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
        insertSummaryNow()
        sb.append(out)
        sb.append("\n</body>")
        sb.append("\n</html>")
        return sb.toString()
    }
}

open class PdfStyle(
    var bodyFontSize: String,
    var topFontSize: String,
    var bottomFontSize: String,
    var topLeft: String,
    var topCenter: String,
    var topRight: String,
    var bottomLeft: String,
    var bottomCenter: String,
    var bottomRight: String,
    var dateFormat: String,
    var contentStyle: String,
    var first: PdfStyle? = null) {

    private val defaultPagePdfStyle = """
        size: a4;
        margin: 80px;	
        
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

    private val defaultStandardPdfStyle = """    
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

    var pagePdfStyle = defaultPagePdfStyle
    var updatePagePdfStyle = defaultPagePdfStyle
    var standardPdfStyle = defaultStandardPdfStyle
}
