/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class HeaderValueIterableTest {
    private static final List<String> ordinalNumbers = Arrays.asList("First", "Second", "Third");
    private static final String FourthOrdinal = "Fourth";
    private static final List<String> emptyStringIterable = Arrays.asList("");

   @ParameterizedTest
   @MethodSource("IterableObjects")
   void testIterableHeaderValues(Iterable<String> ordinals) {
       Header header = HeaderValues.create("Ordinals", ordinals);
       assertThat(header.name().toLowerCase(), is("ordinals"));
       assertThat(header.valueCount(), is(ordinalNumbers.size()));
       assertThat(convertIterableStringToList(ordinals), is(header.allValues()));
   }

    @ParameterizedTest
    @MethodSource("MutableIterableObjects")
    void testMutatingIterableHeaderValues(Object mutableIterableObjects) {
        Iterable<String> ordinals;

        // Initialize the Iterable
        ordinals = mutableIterableObjects instanceof List
                ? (List<String>) mutableIterableObjects
                : (CustomStringIterable) mutableIterableObjects;
        Header header = HeaderValues.create("MutatingOrdinals", ordinals);
        assertThat(header.name().toLowerCase(), is("mutatingordinals"));
        assertThat(header.valueCount(), is(ordinalNumbers.size()));
        assertThat(convertIterableStringToList(ordinals), is(header.allValues()));

        // Mutate the underlying Iterable by adding another element
        if (mutableIterableObjects instanceof List) {
            ((List<String>) mutableIterableObjects).add(FourthOrdinal);
        } else {
            ((CustomStringIterable) mutableIterableObjects).add(FourthOrdinal);
        }
        assertThat(header.valueCount(), is(ordinalNumbers.size() + 1));
        assertThat(convertIterableStringToList(ordinals), is(header.allValues()));
    }

    // This will test that empty string value is still allowed
    @ParameterizedTest
    @MethodSource("EmptyStringIterableObjects")
    void testEmptyStringIterableHeaderValues(Iterable<String> ordinals) {
        Header header = HeaderValues.create("EmptyString", ordinals);
        assertThat(header.name().toLowerCase(), is("emptystring"));
        assertThat(header.valueCount(), is(emptyStringIterable.size()));
        assertThat(header.allValues().getFirst(), is(""));
    }

    // This will allow testing of a Collection and non-Collection type Iterable
    private static Stream<Arguments> IterableObjects() {
        return Stream.of(
                // Collection type iterable
                arguments(ordinalNumbers),
                // non-Collection type iterable
                arguments(new CustomStringIterable(ordinalNumbers))
        );
    }

    // This will allow testing of a Collection and non-Collection type Iterable
    private static Stream<Arguments> MutableIterableObjects() {
        return Stream.of(
                // Collection type iterable
                arguments(new ArrayList<>(ordinalNumbers)),
                // non-Collection type iterable
                arguments(new CustomStringIterable(ordinalNumbers))
        );
    }

    private static Stream<Arguments> EmptyStringIterableObjects() {
        return Stream.of(
                // Collection type iterable
                arguments(emptyStringIterable),
                // non-Collection type iterable
                arguments(new CustomStringIterable(emptyStringIterable))
        );
    }

    private static List<String> convertIterableStringToList(Iterable<String> iterableString) {
        List<String> iterableStringList = new ArrayList<>();
        iterableString.forEach(iterableStringList::add);
        return iterableStringList;
    }

    // Custom non-Collection type Iterable
    static class CustomStringIterable implements Iterable<String> {
        private List<String> data;

        public CustomStringIterable(List<String> data) {
            // Create a mutable List so we can add elements
            this.data = new ArrayList<>(data);
        }

        public boolean add(String element) {
            return data.add(element);
        }

        @Override
        public Iterator<String> iterator() {
            return new CustomStringIterator();
        }

        // Inner class for the custom Iterator
        private class CustomStringIterator implements Iterator<String> {
            private int currentIndex = 0;

            @Override
            public boolean hasNext() {
                return currentIndex < data.size();
            }

            @Override
            public String next() {
                if (!hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                return data.get(currentIndex++);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Remove operation not supported.");
            }
        }
    }
}
