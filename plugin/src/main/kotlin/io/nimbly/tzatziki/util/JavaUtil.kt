package io.nimbly.tzatziki.util;

import java.lang.reflect.Field;

public class JavaUtil {

    public static void updateField(Object object, String field, boolean bool) {
        try {
            Field f1 = object.getClass().getDeclaredField(field);
            f1.setAccessible(true);
            f1.setBoolean(object, bool);
        } catch (Exception ignored) {
        }
    }

    public static void updateField(Object object, String field, int inte) {
        try {
            Field f1 = object.getClass().getDeclaredField(field);
            f1.setAccessible(true);
            f1.setInt(object, inte);
        } catch (Exception ignored) {
        }
    }
}
