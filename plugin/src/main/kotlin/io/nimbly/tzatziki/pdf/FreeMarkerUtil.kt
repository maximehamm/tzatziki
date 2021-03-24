package io.nimbly.tzatziki.pdf

import freemarker.cache.StringTemplateLoader
import freemarker.ext.beans.BooleanModel
import freemarker.ext.beans.DateModel
import freemarker.ext.beans.NumberModel
import freemarker.ext.beans.StringModel
import freemarker.template.*
import java.util.Date
import java.util.regex.Pattern
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
fun initFreeMarker(
    vararg wrapper: KotlinWrapper<*>): Configuration {

    val version = Configuration.VERSION_2_3_23
    return Configuration(version).apply {

        defaultEncoding = "UTF-8"
        templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        logTemplateExceptions = false

        val toMap: Map<KClass<*>, KotlinWrapper<Any>> = wrapper.map { it.kclass to it as KotlinWrapper<Any> }.toMap()
        objectWrapper = SmartKotlinWrapper(version, toMap, true).apply {
            isAPIBuiltinEnabled = true
        }

        setClassForTemplateLoading(javaClass, "/")
    }
}

fun Configuration.registerTemplates(vararg templates: Pair<String, String?>) {

    val loader = StringTemplateLoader()
    templates.forEach {

        val templateName = it.first
        val template = it.second
            ?: throw Exception("Template '$templateName' is null !")

        loader.putTemplate(templateName, template)
    }
    templateLoader = loader
}

private class SmartKotlinWrapper(
        incompatibleImprovements: Version,
        val wrappers: Map<KClass<*>, KotlinWrapper<Any>>,
        val allEscapeHtml: Boolean) : DefaultObjectWrapper(incompatibleImprovements) {

    override fun wrap(obj: Any?): TemplateModel? {
        if (obj!=null) {
            val block = wrappers[obj::class]
            if (block != null) {
                return SmartKotlinModel(obj, this, allEscapeHtml)
            }
            else if (allEscapeHtml && obj is String) {
                return SimpleScalar(obj.escape())
            }
            else if (allEscapeHtml
                    && obj !is Map<*, *> && obj !is List<*>
                    && obj !is Date && obj !is Number && obj !is Boolean) {
                return SmartKotlinModel(obj, this, allEscapeHtml)
            }
        }
        return super.wrap(obj)
    }

    private class SmartKotlinModel(
            objet: Any,
            wrapper: SmartKotlinWrapper,
            val allEscapeHtml: Boolean
    ): StringModel(objet, wrapper) {

        private val kotlinWrapper = wrapper
        override fun get(key: String?): TemplateModel? {
            if (`object`!=null && key!=null) {

                val wrapper = kotlinWrapper.wrappers[`object`::class]
                if (wrapper != null) {

                    val v = (wrapper.wrapper)(`object`, key)
                    if (v != null) {
                        if (v is String)
                            return SimpleScalar(if (wrapper.escapeForHtml) v.escape() else v)
                        else if (v is Boolean)
                            return BooleanModel(v, kotlinWrapper)
                        else if (v is Number)
                            return NumberModel(v, kotlinWrapper)
                        else if (v is Date)
                            return DateModel(v, kotlinWrapper)
                        else
                            return SmartKotlinModel(v, kotlinWrapper, allEscapeHtml)
                    }
                }
            }

            return super.get(key);
        }

        override fun getAsString(): String {
            val s = super.getAsString()
            return if (allEscapeHtml) s.escape() else s
        }
    }
}

infix fun <T: Any> KClass<*>.wrap(that: (T, String?) -> Any?): KotlinWrapper<T>
    = KotlinWrapper(this, that, true)

infix fun <T: Any> KClass<*>.wrapNoEscape(that: (T, String?) -> Any?): KotlinWrapper<T>
        = KotlinWrapper(this, that, false)

open class KotlinWrapper<T: Any>(
    val kclass: KClass<*>,
    val wrapper: (T, String?) -> Any?,
    val escapeForHtml: Boolean = true) {
}

fun String?.noblank(): String? {
    if (this == null)
        return null
    if (this.isBlank())
        return null
    return this
}

object PictureWrapper : KotlinWrapper<Picture>(
    escapeForHtml = false,
    kclass = Picture::class,
    wrapper = { it: Picture, key: String? ->
        val picture: String?
        if (key?.matches(Regex("content[0-9]+"))!!) {
            val height = key.substringAfter("content").toInt()
            picture = it.resized(height = height)
        } else {
            picture = null
        }
        picture
    })

class Picture(
    val name: String,
    val content: String,
    val type: String
)

fun Picture.resized(width: Int? = null, height: Int? = null)
    = scaleSVG(content, height = height)

fun scaleSVG(image: String, width: Int? = null, height: Int? = null): String {

    var svgImage = image
    if (width != null) {
        val widthPattern = Pattern.compile("(?s).*<svg[^>]*width\\s*=\\s*\"(\\d*)")
        val widthMatcher = widthPattern.matcher(svgImage)
        if (widthMatcher.find()) {
            val originalWidthAsString = widthMatcher.group(1)
            val originalWidth = originalWidthAsString.toInt()
            svgImage = svgImage.replaceFirst("width\\s*=\\s*\"\\d*".toRegex(), "width=\"$width")
            if (height == null) {
                // Must also scale height manually relative to width if explicit height is not set.
                val heightPattern = Pattern.compile("(?s).*<svg[^>]*height\\s*=\\s*\"(\\d*)")
                val heightMatcher = heightPattern.matcher(svgImage)
                if (heightMatcher.find()) {
                    val originalHeightAsString = heightMatcher.group(1)
                    val originalHeight = originalHeightAsString.toInt()
                    val computedScaledHeight = ((originalHeight * width).toDouble() / originalWidth.toDouble()).toInt()
                    svgImage = svgImage.replaceFirst("height\\s*=\\s*\"\\d*".toRegex(), "height=\"$computedScaledHeight")
                }
            }
        } else {
            svgImage = svgImage.replaceFirst("<svg".toRegex(), "<svg width=\"$width\"")
        }
    }
    if (height != null) {
        val heightPattern = Pattern.compile("(?s).*<svg[^>]*height\\s*=\\s*\"(\\d*)")
        val heightMatcher = heightPattern.matcher(svgImage)
        if (heightMatcher.find()) {
            val originalHeightAsString = heightMatcher.group(1)
            val originalHeight = originalHeightAsString.toInt()
            svgImage = svgImage.replaceFirst("height\\s*=\\s*\"\\d*".toRegex(), "height=\"$height")
            if (width == null) {
                // Must also scale width manually relative to height if explicit width is not set.
                val widthPattern = Pattern.compile("(?s).*<svg[^>]*width\\s*=\\s*\"(\\d*)")
                val widthMatcher = widthPattern.matcher(svgImage)
                if (widthMatcher.find()) {
                    val originalWidthAsString = widthMatcher.group(1)
                    val originalWidth = originalWidthAsString.toInt()
                    val computedScaledWidth = ((originalWidth * height).toDouble() / originalHeight.toDouble()).toInt()
                    svgImage = svgImage.replaceFirst("width\\s*=\\s*\"\\d*".toRegex(), "width=\"$computedScaledWidth")
                }
            }
        } else {
            svgImage = svgImage.replaceFirst("<svg".toRegex(), "<svg height=\"$height\"")
        }
    }
    return svgImage
}
