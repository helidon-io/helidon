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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class HeaderValueIterableTest {
    private static final List<String> ordinalNumbers = Arrays.asList("First", "Second", "Third");
    private static final Set<String> ordinalNumbersSet = new HashSet<>(ordinalNumbers);
    private static final CustomIterableString ordinalNumbersCustomIterable = new CustomIterableString(ordinalNumbers);
    private static final List<String> emptyStringIterable = Arrays.asList("");
    private static final CustomIterableString emptyCustomIterable = new CustomIterableString(emptyStringIterable);
    private static final String FourthOrdinal = "Fourth";

   @ParameterizedTest
   @MethodSource("IterableObjects")
   void testIterableHeaderValues(Iterable<String> ordinals) {
       Header header = HeaderValues.create("Ordinals", ordinals);
       assertThat(header.name().toLowerCase(), is("ordinals"));
       assertThat(header.valueCount(), is(ordinalNumbers.size()));
       assertThat(convertIterableStringToList(ordinals), is(header.allValues()));
   }

    @ParameterizedTest
    @MethodSource("IterableObjects")
    void testAddValueOnHeaderValueIterable(Iterable<String> ordinals) {
        Header header = HeaderValues.create("Ordinals", ordinals);
        HeaderValueList headerValueList = (HeaderValueList) header;
        headerValueList.addValue(FourthOrdinal);
        assertThat(headerValueList.valueCount(), is(ordinalNumbers.size() + 1));

        // Convert Iterable `ordinals` to a List so we can compare with result of header.allValues()
        List<String> ordinalsList = convertIterableStringToList(ordinals);
        // This will be not equal because "Fourth" was added
        assertThat(ordinalsList, not(headerValueList.allValues()));

        List<String> ordinalsListWithFourth = new ArrayList<>(ordinalsList);
        ordinalsListWithFourth.add("Fourth");
        assertThat(ordinalsListWithFourth, is(headerValueList.allValues()));
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
                arguments(ordinalNumbersSet),
                // non-Collection type iterable
                arguments(ordinalNumbersCustomIterable)
        );
    }

    // This will test empty value iterables will still work
    private static Stream<Arguments> EmptyStringIterableObjects() {
        return Stream.of(
                // Collection type iterable
                arguments(emptyStringIterable),
                // non-Collection type iterable
                arguments(emptyCustomIterable)
        );
    }

    private static List<String> convertIterableStringToList(Iterable<String> iterableString) {
        if (iterableString instanceof List) {
            return (List<String>) iterableString;
        }
        return StreamSupport.stream(iterableString.spliterator(), false).toList();
    }

    // Custom non-Collection type Iterable
    static class CustomIterableString implements Iterable<String> {
        private final List<String> data;

        public CustomIterableString(List<String> data) {
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
