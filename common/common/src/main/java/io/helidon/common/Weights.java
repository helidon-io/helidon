/*
 * Copyright (c) 2019, 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.common;

import java.util.Comparator;
import java.util.List;

/**
 * Weight utilities.
 */
public final class Weights {
    private Weights() {
    }

    /**
     * Find weight from class annotation, or return default if none found.
     *
     * @param aClass        class to analyzed
     * @param defaultWeight default weight of this class
     * @return weight from {@link Weight} or the default provided
     */
    public static double find(Class<?> aClass, double defaultWeight) {
        Weight weightAnnot = aClass.getAnnotation(Weight.class);
        if (null != weightAnnot) {
            return weightAnnot.value();
        }

        return defaultWeight;
    }

    /**
     * Find weight for an instance.
     * First checks if instance is {@link io.helidon.common.Weighted}. If so,
     * uses the value from it.
     * Then checks for {@link Weight} annotation.
     * If none of the above is found, returns the default weight.
     *
     * @param anObject      object to find weight for
     * @param defaultWeight default weight to use
     * @return weight of the object or default provided
     */
    public static double find(Object anObject, double defaultWeight) {
        if (anObject instanceof Class) {
            return find((Class<?>) anObject, defaultWeight);
        }
        if (anObject instanceof Weighted) {
            return ((Weighted) anObject).weight();
        }
        Weight weight = null;
        // first go through super classes
        Class<?> current = anObject.getClass();
        while (weight == null) {
            weight = current.getAnnotation(Weight.class);
            current = current.getSuperclass();
            if (current.equals(Object.class)) {
                break;
            }
        }
        if (weight == null) {
            // then through interfaces (only first level)
            Class<?>[] interfaces = anObject.getClass().getInterfaces();
            for (Class<?> anInterface : interfaces) {
                weight = anInterface.getAnnotation(Weight.class);
                if (weight != null) {
                    break;
                }
            }
        }

        if (weight == null) {
            return defaultWeight;
        }
        return weight.value();
    }

    /**
     * Sort the list based on weights.
     * <ul>
     * <li>If element implements {@link io.helidon.common.Weighted}, uses its weight.</li>
     * <li>If element is a class and has annotation {@link Weight}, uses its weight</li>
     * <li>If element is any object and its class has annotation {@link Weight}, uses its weight</li>
     * </ul>
     *
     * @param list list to sort
     */
    public static void sort(List<?> list) {
        list.sort(weightComparator());
    }

    /**
     * Returns a comparator for two objects, the classes for which are implementations of
     * {@link io.helidon.common.Weighted}, and/or optionally annotated with {@link Weight}
     * and which applies a specified default weight if either or both classes lack the annotation.
     *
     * @param <S> type of object being compared
     * @return comparator
     */
    public static <S> Comparator<S> weightComparator() {
        return (o1, o2) -> {
            if (o1 == null) {
                return o2 == null ? 0 : 1;
            }
            if (o2 == null) {
                return -1;
            }

            double firstWeight = find(o1, Weighted.DEFAULT_WEIGHT);
            double secondWeight = find(o2, Weighted.DEFAULT_WEIGHT);

            if (firstWeight != secondWeight) {
                // only return if they differ
                return Double.compare(secondWeight, firstWeight);
            }

            // same weight, compare based on class name
            return o1.getClass().getName().compareTo(o2.getClass().getName());
        };
    }
}
