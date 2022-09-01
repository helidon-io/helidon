package io.helidon.core.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// CollectionUtils from Micronaut Core
public class CollectionUtils {

    /**
     * Creates a set of the given objects.
     *
     * @param objects The objects
     * @param <T>     The type
     * @return The set
     */
    @SafeVarargs
    public static <T> Set<T> setOf(T... objects) {
        if (objects == null || objects.length == 0) {
            return new HashSet<>(0);
        }
        return new HashSet<>(Arrays.asList(objects));
    }

}
