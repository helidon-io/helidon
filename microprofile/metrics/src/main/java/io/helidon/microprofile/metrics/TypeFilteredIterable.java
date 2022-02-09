/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

/**
 * {@link Iterable} which exposes only those elements of another iterable that are of a given subtype.
 *
 */
class TypeFilteredIterable {

    private TypeFilteredIterable() {
    }

    /**
     * Creates a type-filtered {@code Iterable}, including only those elements of the original iterable that are of a given
     * subtype of the original iterable's element type.
     *
     * @param original the original {@code Iterable}
     * @param <S> subtype to be accepted
     * @param sClass class of the subtype to be accepted
     * @return {@code Iterable<S>} containing only those elements of the original which are of the specified subtype
     */
    public static <S> Iterable<S> create(Iterable<? super S> original, Class<S> sClass) {
        return new Iterable<>() {

            @Override
            public Iterator<S> iterator() {
                return new Iterator<>() {

                    private final Iterator<? super S> it = original.iterator();

                    private S next = findNext();

                    private S findNext() {
                        while (it.hasNext()) {
                            Object nextOriginal = it.next();
                            if (sClass.isInstance(nextOriginal)) {
                                return sClass.cast(nextOriginal);
                            }
                        }
                        return null;
                    }

                    @Override
                    public boolean hasNext() {
                        return next != null;
                    }

                    @Override
                    public S next() {
                        if (next == null) {
                            throw new NoSuchElementException();
                        }
                        S result = next;
                        next = findNext();
                        return result;
                    }

                    @Override
                    public void forEachRemaining(Consumer<? super S> action) {
                        Iterator.super.forEachRemaining(t -> {
                            if (sClass.isInstance(t)) {
                                action.accept(t);
                            }
                        });
                    }
                };
            }

            @Override
            public void forEach(Consumer<? super S> action) {
                Iterable.super.forEach(t -> {
                    if (sClass.isInstance(t)) {
                        action.accept(t);
                    }
                });
            }
        };
    }
}
