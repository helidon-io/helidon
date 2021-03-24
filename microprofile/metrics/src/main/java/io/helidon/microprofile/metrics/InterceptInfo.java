/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.microprofile.metrics;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Records information about an intercepted method.
 * <p>
 *     Specifically:
 * <ul>
 *     <li>the work items to be updated when the corresponding method is intercepted, organized by the annotation class that
 *     gave rise to each work item; and</li>
 *     <li>the {@code InterceptRunner} to use in updating the work items and invoking the method or constructor.</li>
 * </ul>
 * </p>
 *
 * @param <T> base type of the work items handled by the interceptor represented by this instance
 */
class InterceptInfo<T> {

    private final InterceptRunner runner;

    private final Map<Class<?>, Collection<T>> workItemsByAnnotationType = new HashMap<>();

    /**
     * Creates a new instance based on the provided {@code Executable}.
     *
     * @param executable the constructor or method subject to interception
     * @return the new instance
     */
    static <T> InterceptInfo<T> create(Executable executable) {
        return new InterceptInfo<>(InterceptRunnerImpl.create(executable));
    }

    private InterceptInfo(InterceptRunner runner) {
        this.runner = runner;
    }

    InterceptRunner runner() {
        return runner;
    }

    Supplier<Iterable<T>> workItems(Class<? extends Annotation> annotationType) {
        /*
         * Build a supplier of the iterable, because before-and-after runners will need to process the work items twice so we need
         *  to give the runner a supplier.
         */
        return () -> workItemsByAnnotationType.get(annotationType);
    }

    /**
     * Adds a work item to this info, identifying the annotation type that led to this work item.
     * @param annotationType type of the interceptor
     * @param workItem the newly-created workItem
     */
    void addWorkItem(Class<? extends Annotation> annotationType, T workItem) {

        // Using a set for the actual collection subtly handles the case where a class-level and a method- or constructor-level
        // annotation both indicate the same workItem. We do not want to update the same workItem twice in that case.

        Collection<T> workItems = workItemsByAnnotationType.computeIfAbsent(annotationType, c -> new HashSet<>());
        workItems.add(workItem);
    }
}
