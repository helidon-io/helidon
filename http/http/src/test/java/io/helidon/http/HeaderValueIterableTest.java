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
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class HeaderValueIterableTest {
    private static final String[] ordinalNumbers = {"First", "Second", "Third"};

    @ParameterizedTest
    @MethodSource("IterableObjects")
    void testIterableHeaderValues(Iterable<String> ordinals) {
        Header header = HeaderValues.create("Ordinals", ordinals);
        assertThat(header.name().toLowerCase(), is("ordinals"));
        assertThat(header.valueCount(), is(ordinalNumbers.length));
        assertThat(convertIterableStringToList(ordinals), is(header.allValues()));
    }

    @ParameterizedTest
    @MethodSource("IterableObjects")
    void testAddValueOnHeaderValueIterable(Iterable<String> ordinals) {
        HeaderValueIterable headerValueIterable =  new HeaderValueIterable(
                HeaderNames.create("Ordinals"), true, false, ordinals);
        headerValueIterable.addValue("Fourth");
        assertThat(headerValueIterable.valueCount(), is(ordinalNumbers.length + 1));

        // Convert Iterable `ordinals` to a List so we can compare with result of header.allValues()
        List<String> ordinalsList = convertIterableStringToList(ordinals);
        // This will be not equal because "Fourth" was added
        assertThat(ordinalsList, not(headerValueIterable.allValues()));

        ordinalsList.add("Fourth");
        assertThat(ordinalsList, is(headerValueIterable.allValues()));
    }

    // This will allow testing of a Collection and non-Collection type Iterable
    private static Stream<Arguments> IterableObjects() {
        return Stream.of(
                // Collection type iterable
                arguments(Arrays.asList(ordinalNumbers)),
                // non-Collection type iterable
                arguments(new CustomStringIterable(ordinalNumbers))
        );
    }

    private static List<String> convertIterableStringToList(Iterable<String> iterableString) {
        List<String> iterableStringList = new ArrayList<>();
        iterableString.forEach(iterableStringList::add);
        return iterableStringList;
    }

    // Custom non-Collection type Iterable
    static class CustomStringIterable implements Iterable<String> {
        private final String[] data;

        public CustomStringIterable(String[] data) {
            this.data = data;
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
                return currentIndex < data.length;
            }

            @Override
            public String next() {
                if (!hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                return data[currentIndex++];
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Remove operation not supported.");
            }
        }
    }
}
