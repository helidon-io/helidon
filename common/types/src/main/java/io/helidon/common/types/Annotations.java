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

package io.helidon.common.types;

import java.util.Collection;
import java.util.Optional;

/**
 * Annotation utilities.
 */
public final class Annotations {
    /**
     * Override annotation.
     */
    public static final Annotation OVERRIDE = Annotation.create(Override.class);

    /**
     * Deprecated annotation.
     */
    public static final Annotation DEPRECATED = Annotation.create(Deprecated.class);

    private Annotations() {
    }

    /**
     * Attempts to find the annotation in the provided collection.
     *
     * @param annoTypeName  the annotation type name
     * @param coll          the collection to search
     * @param <T>           annotation type
     * @return the result of the find
     */
    public static <T extends Annotation> Optional<T> findFirst(TypeName annoTypeName,
                                                               Collection<T> coll) {
        String name = annoTypeName.name();
        return coll.stream()
                .filter(it -> it.typeName().name().equals(name))
                .findFirst();
    }

    /**
     * Attempts to find the annotation in the provided collection.
     *
     * @param annoType    the annotation type
     * @param coll        the collection to search
     * @param <T>           annotation type
     * @return the result of the find
     */
    public static <T extends Annotation> Optional<T> findFirst(Class<? extends java.lang.annotation.Annotation> annoType,
                                                               Collection<T> coll) {
        return findFirst(annoType.getTypeName(), coll);
    }

    /**
     * Attempts to find the annotation in the provided collection.
     *
     * @param annoTypeName  the annotation type name
     * @param coll          the collection to search
     * @param <T>           annotation type
     * @return the result of the find
     */
    public static <T extends Annotation> Optional<T> findFirst(String annoTypeName,
                                                               Collection<T> coll) {
        assert (!annoTypeName.isBlank());
        return coll.stream()
                .filter(it -> it.typeName().name().equals(annoTypeName))
                .findFirst();
    }
}
