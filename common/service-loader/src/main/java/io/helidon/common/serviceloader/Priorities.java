/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.common.serviceloader;

import java.util.Comparator;
import java.util.List;

import javax.annotation.Priority;

import io.helidon.common.Prioritized;

/**
 * Priority utilities.
 */
public final class Priorities {
    private Priorities() {
    }

    /**
     * Find priority from class annotation, or return default if none found.
     *
     * @param aClass class to analyzed
     * @param defaultPriority default priority of this class
     * @return priority from {@link Priority}or the default provided
     */
    public static int find(Class<?> aClass, int defaultPriority) {
        Priority priorityAnnot = aClass.getAnnotation(Priority.class);
        if (null != priorityAnnot) {
            return priorityAnnot.value();
        }

        return defaultPriority;
    }

    /**
     * Find priority for an instance.
     * First checks if instance is {@link io.helidon.common.Prioritized}. If so,
     * uses the value from it.
     * Then checks for {@link Priority} annotation.
     * If none of the above is found, returns the default priority.
     *
     * @param anObject object to find priority for
     * @param defaultPriority default priority to use
     * @return priority of the object or default provided
     */
    public static int find(Object anObject, int defaultPriority) {
        if (anObject instanceof Class) {
            return find((Class<?>) anObject, defaultPriority);
        }
        if (anObject instanceof Prioritized) {
            return ((Prioritized) anObject).priority();
        }
        Priority prio = anObject.getClass().getAnnotation(Priority.class);
        if (null == prio) {
            return defaultPriority;
        }
        return prio.value();
    }

    /**
     * Sort the prioritized list based on priorities.
     * @param list list to sort
     */
    public static void sort(List<? extends Prioritized> list) {
        list.sort(Comparator.comparingInt(Prioritized::priority));
    }

    /**
     * Sort the list based on priorities.
     * <ul>
     * <li>If element implements {@link io.helidon.common.Prioritized}, uses its priority.</li>
     * <li>If element is a class and has annotation {@link javax.annotation.Priority}, uses its priority</li>
     * <li>If element is any object and its class has annotation {@link javax.annotation.Priority}, uses its priority</li>
     * </ul>
     * @param list list to sort
     * @param defaultPriority default priority for elements that do not have it
     */
    public static void sort(List<?> list, int defaultPriority) {
        list.sort(priorityComparator(defaultPriority));
    }

    /**
     * Returns a comparator for two objects, the classes for which are implementations of
     * {@link io.helidon.common.Prioritized}, and/or optionally annotated with {@link javax.annotation.Priority}
     * and which applies a specified default priority if either or both classes lack the annotation.
     *
     * @param <S> type of object being compared
     * @param defaultPriority used if the classes for either or both objects
     * lack the {@code Priority} annotation
     * @return comparator
     */
    public static <S> Comparator<S> priorityComparator(int defaultPriority) {
        return Comparator.comparingInt(it -> {
            if (it instanceof Class) {
                return find((Class<?>) it, defaultPriority);
            } else {
                return find(it, defaultPriority);
            }
        });
    }
}
