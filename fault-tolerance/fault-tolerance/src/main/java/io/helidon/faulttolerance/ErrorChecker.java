/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import java.util.Set;

/**
 * Support for checking {@code skipOn} and {@code applyOn} sets of throwables.
 */
@FunctionalInterface
public interface ErrorChecker {
    /**
     * Returns ErrorChecker that skips if throwable is in skipOnSet or if applyOnSet
     * is not empty and throwable is not in it. Note that if applyOnSet is empty, then
     * it is equivalent to it containing {@code Throwable.class}. Sets are copied
     * because they are mutable.
     *
     * @param skipOnSet  set of throwables to skip logic on.
     * @param applyOnSet set of throwables to apply logic on.
     * @return An error checker.
     */
    static ErrorChecker create(Set<Class<? extends Throwable>> skipOnSet, Set<Class<? extends Throwable>> applyOnSet) {
        Set<Class<? extends Throwable>> skipOn = Set.copyOf(skipOnSet);
        Set<Class<? extends Throwable>> applyOn = Set.copyOf(applyOnSet);
        return throwable -> containsThrowable(skipOn, throwable)
                || !applyOn.isEmpty() && !containsThrowable(applyOn, throwable);
    }

    /**
     * Should the exception be skipped, or applied on.
     *
     * @param throwable to check
     * @return {@code true} if this throwable should be skipped, {@code false} if this throwable should be applied on
     */
    boolean shouldSkip(Throwable throwable);

    private static boolean containsThrowable(Set<Class<? extends Throwable>> set, Throwable throwable) {
        return set.stream().anyMatch(t -> t.isAssignableFrom(throwable.getClass()));
    }
}
