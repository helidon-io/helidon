/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.jpa;

import java.util.AbstractMap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toUnmodifiableSet;
import static java.util.stream.StreamSupport.stream;

final class StackedMap<K, V> extends AbstractMap<K, V> {


    /*
     * Instance fields.
     */


    private final Supplier<? extends Set<? extends K>> ks;

    private final Function<? super K, ? extends V> vf;


    /*
     * Constructors.
     */


    StackedMap(Iterable<? extends Supplier<? extends Stream<? extends K>>> kss,
               Iterable<? extends Function<? super K, ? extends V>> vfs) {
        this(union(kss), vf(vfs));
    }

    StackedMap(Supplier<Set<? extends K>> ks, Function<? super K, ? extends V> vf) {
        super();
        this.ks = Objects.requireNonNull(ks, "ks");
        this.vf = Objects.requireNonNull(vf, "vf");
    }


    /*
     * Instance methods.
     */


    @Override // AbstractMap<K, V>
    public Set<Entry<K, V>> entrySet() {
        return new EntrySet();
    }


    /*
     * Static methods.
     */


    private static <K> Supplier<Set<? extends K>> union(Iterable<? extends Supplier<? extends Stream<? extends K>>> i) {
        Objects.requireNonNull(i, "i");
        return () -> stream(i.spliterator(), false)
            .flatMap(Supplier::get)
            .collect(toUnmodifiableSet());
    }

    private static <K, V> Function<? super K, ? extends V> vf(Iterable<? extends Function<? super K, ? extends V>> vfs) {
        Objects.requireNonNull(vfs, "vfs");
        return k -> stream(vfs.spliterator(), false)
            .flatMap(vf -> Optional.ofNullable(vf.apply(k)).stream())
            .findFirst()
            .orElse(null);
    }


    /*
     * Inner and nested classes.
     */


    private final class EntrySet extends AbstractSet<Entry<K, V>> {


        /*
         * Constructors.
         */


        private EntrySet() {
            super();
        }


        /*
         * Instance methods.
         */


        @Override // AbstractSet<Entry<K, V>>
        public int size() {
            return ks.get().size();
        }

        @Override // AbstractSet<Entry<K, V>>
        public Iterator<Entry<K, V>> iterator() {
            return new I();
        }


        /*
         * Inner and nested classes.
         */


        private final class I implements Iterator<Entry<K, V>> {


            /*
             * Instance fields.
             */


            private final Iterator<? extends K> ki;


            /*
             * Constructors.
             */


            private I() {
                super();
                this.ki = ks.get().iterator();
            }


            /*
             * Instance methods.
             */


            @Override // Iterator<Entry<K, V>>
            public boolean hasNext() {
                return this.ki.hasNext();
            }

            @Override // Iterator<Entry<K, V>>
            public Entry<K, V> next() {
                K k = this.ki.next();
                return new SimpleImmutableEntry<>(k, vf.apply(k));
            }

        }

    }

}
