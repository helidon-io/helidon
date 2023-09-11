/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.emptyCollectionOf;
import static org.junit.jupiter.api.Assertions.assertAll;

class HeaderNamesTest {
    private static final Class<HeaderNames> clazz = HeaderNames.class;
    private static final Set<String> constants = Stream.of(clazz.getDeclaredFields())
            .filter(it -> Modifier.isStatic(it.getModifiers()))
            .filter(it -> Modifier.isFinal(it.getModifiers()))
            .filter(it -> Modifier.isPublic(it.getModifiers()))
            .map(Field::getName)
            .collect(Collectors.toSet());

    @Test
    void testAllEnumValuesHaveConstants() {
        HeaderNameEnum[] expectedNames = HeaderNameEnum.values();

        Set<String> missing = new LinkedHashSet<>();

        for (HeaderNameEnum expectedName : expectedNames) {
            String name = expectedName.name();
            if (!constants.contains(name)) {
                missing.add(name);
            }
        }

        assertThat(missing, emptyCollectionOf(String.class));
    }

    @Test
    void testAllConstantsAreValid() throws NoSuchFieldException, IllegalAccessException {
        // this is to test correct initialization (there may be an issue when the constants
        // are defined on the interface and implemented by enum outside of it)
        for (String constant : constants) {
            if (!HeaderName.class.equals(clazz.getField(constant).getType())) {
                continue;
            }

            HeaderName value = (HeaderName) clazz.getField(constant)
                    .get(null);

            assertAll(
                    () -> assertThat(value, notNullValue()),
                    () -> assertThat(value.defaultCase(), notNullValue()),
                    () -> assertThat(value.lowerCase(), notNullValue()),
                    () -> assertThat(value.index(), not(-1))
            );

        }
    }

    @Test
    void testEqualsAndHashCodeKnownHeader() {
        HeaderName customAccept = HeaderNames.create("ACCEPT");

        assertThat(customAccept, equalTo(HeaderNames.ACCEPT));
        assertThat(HeaderNames.ACCEPT, equalTo(customAccept));
        assertThat(customAccept.hashCode(), is(HeaderNames.ACCEPT.hashCode()));


        customAccept = HeaderNames.create("accept", "ACCEPT");

        assertThat(customAccept, equalTo(HeaderNames.ACCEPT));
        assertThat(HeaderNames.ACCEPT, equalTo(customAccept));
        assertThat(customAccept.hashCode(), is(HeaderNames.ACCEPT.hashCode()));
    }

    @Test
    void testEqualsAndHashCodeCustomHeader() {
        HeaderName custom1 = HeaderNames.create("My-Custom-Header");
        HeaderName custom2 = HeaderNames.create("my-custom-header");


        assertThat(custom1, equalTo(custom2));
        assertThat(custom2, equalTo(custom1));
        assertThat(custom1.hashCode(), is(custom2.hashCode()));


        custom1 = HeaderNames.create("my-custom-header", "My-Custom-Header");
        custom2 = HeaderNames.create("my-custom-header", "my-custom-header");

        assertThat(custom1, equalTo(custom2));
        assertThat(custom2, equalTo(custom1));
        assertThat(custom1.hashCode(), is(custom2.hashCode()));

        assertThat(custom1.lowerCase(), is("my-custom-header"));
        assertThat(custom1.defaultCase(), is("My-Custom-Header"));
        assertThat(custom2.lowerCase(), is("my-custom-header"));
        assertThat(custom2.defaultCase(), is("my-custom-header"));
    }
}
