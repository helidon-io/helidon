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
package io.helidon.servicecommon.restcdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Records information about an intercepted executable.
 * <p>
 *     Specifically:
 * <ul>
 *     <li>the work items to be updated when the corresponding executable is intercepted, organized by the annotation class that
 *     gave rise to each work item; and</li>
 *     <li>the {@code InterceptionRunner} to use in updating the work items and invoking the method or constructor.</li>
 * </ul>
 *
 * @param <T> base type of the work items handled by the interceptor represented by this instance
 */
public class InterceptionTargetInfo<T> {

    private final InterceptionRunner runner;

    private final Map<Class<?>, Collection<T>> workItemsByAnnotationType = new HashMap<>();

    /**
     * Creates a new instance based on the provided {@code Executable}.
     *
     * @param executable the constructor or method subject to interception
     * @param <T> the type of work items associated with the target info
     * @return the new instance
     */
    public static <T> InterceptionTargetInfo<T> create(Executable executable) {
        return new InterceptionTargetInfo<>(InterceptionRunnerFactory.create(executable));
    }

    private InterceptionTargetInfo(InterceptionRunner runner) {
        this.runner = runner;
    }

    /**
     *
     * @return the {@code InterceptionRunner} for this instance
     */
    public InterceptionRunner runner() {
        return runner;
    }

    /**
     * Returns the work items for the given annotation type.
     *
     * @param annotationType type of annotation for which work items are requested
     * @return the work items
     */
    public Iterable<T> workItems(Class<? extends Annotation> annotationType) {
        /*
         * Build a supplier of the iterable, because before-and-after runners will need to process the work items twice so we need
         *  to give the runner a supplier.
         */
        return workItemsByAnnotationType.get(annotationType);
    }

    /**
     * Adds a work item to this info, identifying the annotation type that led to this work item.
     * @param annotationType type of the interceptor
     * @param workItem the newly-created workItem
     */
    public void addWorkItem(Class<? extends Annotation> annotationType, T workItem) {

        // Using a set for the actual collection subtly handles the case where a class-level and a method- or constructor-level
        // annotation both indicate the same workItem. We do not want to update the same workItem twice in that case.

        Collection<T> workItems = workItemsByAnnotationType.computeIfAbsent(annotationType, c -> new HashSet<>());
        workItems.add(workItem);
    }
}
