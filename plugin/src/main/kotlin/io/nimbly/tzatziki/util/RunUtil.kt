package io.nimbly.tzatziki.util

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey

class TzDataContext : DataContext {

    private val myMap = mutableMapOf<String?, Any?>()

    override fun getData(dataId: String): Any? {
        return myMap[dataId]
    }

    fun put(dataId: String, data: Any?) {
        myMap[dataId] = data
    }

    fun <T> put(dataKey: DataKey<T>, data: T) {
        this.put(dataKey.name, data)
    }

    @Suppress("DEPRECATION") // Compatibility issue
    fun configutation()
        = ConfigurationContext.getFromContext(this)
}


fun emptyConfigurationContext()
        = TzDataContext().configutation()
