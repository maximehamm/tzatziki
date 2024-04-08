package io.nimbly.i18n.translation.engines.deepl

import io.nimbly.i18n.translation.engines.EEngine

class DeepLEnginePro : DeepLEngine() {

    override val type = EEngine.DEEPL_PRO

    override fun needApiKey() = true

    override fun label() = "DeepL API Pro"

    override fun documentation() = """<html>
        Pay as you go. No volume restrictions.<br/>
        Subscribe <a href='https://www.deepl.com/en/pro#developer'>here</a> and receive an API key !
    </html>""".trimIndent()

    override fun getEndpoint()
        = "api.deepl.com"
}