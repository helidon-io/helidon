/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.jpa.common.model;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * {@link List} utility.
 */
public abstract class Lists {

    private Lists() {
        // cannot be instantiated
    }

    /**
     * Compare two lists.
     *
     * @param actual actual
     * @param other  other
     * @param <T>    element type
     * @return {@code true} if both lists are equal
     */
    public static <T> boolean equals(List<T> actual, List<T> other) {
        if (actual == other) {
            return true;
        }
        if (actual == null || other == null || actual.size() != other.size()) {
            return false;
        }
        Iterator<T> actualIt = actual.iterator();
        Iterator<T> otherIt = other.iterator();
        while (actualIt.hasNext() && otherIt.hasNext()) {
            T actualElt = actualIt.next();
            T otherElt = otherIt.next();
            if (!Objects.equals(actualElt, otherElt)) {
                return false;
            }
        }
        return !actualIt.hasNext() && !otherIt.hasNext();
    }
}
