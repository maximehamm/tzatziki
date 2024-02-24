package io.nimbly.i18n.util

object JavaUtil {

    fun updateField(`object`: Any, field: String?, str: String) {
        try {
            val f1 = `object`.javaClass.getDeclaredField(field)
            f1.isAccessible = true
            f1.set(`object`, str)
        } catch (ignored: Exception) {
        }
    }

    fun updateField(`object`: Any, field: String?, bool: Boolean) {
        try {
            val f1 = `object`.javaClass.getDeclaredField(field)
            f1.isAccessible = true
            f1.setBoolean(`object`, bool)
        } catch (ignored: Exception) {
        }
    }

    fun updateField(`object`: Any, field: String?, inte: Int) {
        try {
            val f1 = `object`.javaClass.getDeclaredField(field)
            f1.isAccessible = true
            f1.setInt(`object`, inte)
        } catch (ignored: Exception) {
        }
    }
}
