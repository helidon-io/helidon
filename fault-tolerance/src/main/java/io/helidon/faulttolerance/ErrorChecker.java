/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

@FunctionalInterface
interface ErrorChecker {
    boolean shouldSkip(Throwable throwable);

    static ErrorChecker create(Set<Class<? extends Throwable>> skipOnSet, Set<Class<? extends Throwable>> applyOnSet) {
        Set<Class<? extends Throwable>> skipOn = Set.copyOf(skipOnSet);
        Set<Class<? extends Throwable>> applyOn = Set.copyOf(applyOnSet);

        if (skipOn.isEmpty()) {
            if (applyOn.isEmpty()) {
                return throwable -> false;
            } else {
                return throwable -> applyOn.stream().filter(t -> t.isAssignableFrom(throwable.getClass())).count() == 0;
            }
        } else {
            if (applyOn.isEmpty()) {
                return throwable -> skipOn.stream().filter(t -> t.isAssignableFrom(throwable.getClass())).count() > 0;
            } else {
                throw new IllegalArgumentException("You have defined both skip and apply set of exception classes. "
                                                           + "This cannot be correctly handled; skipOn: " + skipOn
                                                           + " applyOn: " + applyOn);
            }

        }
    }
}
