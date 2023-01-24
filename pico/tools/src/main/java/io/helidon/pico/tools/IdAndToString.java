/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.tools;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Convenience for use with handlebars to offer {{.id}} that is different from {{.}}.
 */
class IdAndToString {

    private final String id;
    private final Object toString;

    /**
     * Ctor.
     *
     * @param id        the id
     * @param toString  the toString value
     */
    IdAndToString(
            String id,
            Object toString) {
        this.id = Objects.requireNonNull(id);
        this.toString = toString;
    }

    /**
     * Returns the id.
     *
     * @return the id
     */
    // note that this is called from Mustache, so it needs to be bean-style named!
    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return String.valueOf(toString);
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public boolean equals(
            Object another) {
        if (!(another instanceof IdAndToString)) {
            return false;
        }
        return getId().equals(((IdAndToString) another).getId());
    }

    /**
     * Creates a list of {@link IdAndToString} from a list of T's.
     *
     * @param list the list to convert
     * @param toId the function that will create the {@link IdAndToString}
     * @param <T> the type of the list
     * @return the converted list
     */
    static <T> List<IdAndToString> toList(
            Collection<T> list,
            Function<T, IdAndToString> toId) {
        if (list == null) {
            return null;
        }

        return list.stream()
                .map(toId)
                .collect(Collectors.toList());
    }

}
