<#-- TOP (Company+ logo) -->
<table style="width:100%">
    <tr><td style="width:50%; border:0"></td>
        <td style="height: 250px; border:0">
            <#if logo??>
                <h1 style="text-align:center;">
                    ${logo.content60}
                </h1>
            </#if>
        </td>
        <td style="width:50%; border:0"></td>
    </tr>
</table>

<#-- CENTER -->
<#function gap>
    <#if orientation == 'portrait'>
        <#return '630px'>
    <#else>
        <#return '300px'>
    </#if>
</#function>

<table style="width:100%; height:${gap()}">
    <tr><td style="border:0">
            <h1 style="text-align:center; font-size:40px; margin-bottom: 50px">
                ${frontpage.title}
            </h1>
        </td>
    </tr>

    <tr><td style="border:0">
            <h1 style="text-align: center;">
                <div style="width:500px;font-size:25px;margin:0 auto;">
                    <p>${frontpage.description}</p>
                </div>
            </h1>
        </td>
    </tr>
</table>

<#-- BOTTOM (Cucumber+ logo) -->
<#if orientation == 'portrait'>
    <table style="width: 100%">
        <tr><td style="width:50%; border:0"></td>
            <td style="border:0">
                ${logo.content40}
            </td>
            <td style="width:50%; border:0"></td>
        </tr>
    </table>
</#if>