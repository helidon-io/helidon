package io.helidon.core.utils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// ReflectionUtils from Micronaut Core
public class ReflectionUtils {

    private static final Map<Class<?>, Class<?>> PRIMITIVES_TO_WRAPPERS;

    static {
        LinkedHashMap<Class<?>, Class<?>> m = new LinkedHashMap<>();
        m.put(boolean.class, Boolean.class);
        m.put(byte.class, Byte.class);
        m.put(char.class, Character.class);
        m.put(double.class, Double.class);
        m.put(float.class, Float.class);
        m.put(int.class, Integer.class);
        m.put(long.class, Long.class);
        m.put(short.class, Short.class);
        m.put(void.class, Void.class);
        PRIMITIVES_TO_WRAPPERS = Collections.unmodifiableMap(m);
    }

    /**
     * Obtain the wrapper type for the given primitive.
     *
     * @param primitiveType The primitive type
     * @return The wrapper type
     */
    public static Class getWrapperType(Class primitiveType) {
        if (primitiveType.isPrimitive()) {
            return PRIMITIVES_TO_WRAPPERS.get(primitiveType);
        }
        return primitiveType;
    }

}
