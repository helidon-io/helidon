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

package io.helidon.common.http;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.emptyCollectionOf;
import static org.junit.jupiter.api.Assertions.assertAll;

class HeaderNamesTest {
    private static final Class<Http.Header> clazz = Http.Header.class;
    private static final Set<String> constants = Stream.of(clazz.getDeclaredFields())
            .filter(it -> Modifier.isStatic(it.getModifiers()))
            .filter(it -> Modifier.isFinal(it.getModifiers()))
            .filter(it -> Modifier.isPublic(it.getModifiers()))
            .map(Field::getName)
            .collect(Collectors.toSet());

    @Test
    void testAllEnumValuesHaveConstants() {
        HeaderEnum[] expectedNames = HeaderEnum.values();

        Set<String> missing = new LinkedHashSet<>();

        for (HeaderEnum expectedName : expectedNames) {
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
            if (!Http.HeaderName.class.equals(clazz.getField(constant).getType())) {
                continue;
            }

            Http.HeaderName value = (Http.HeaderName) clazz.getField(constant)
                    .get(null);

            assertAll(
                    () -> assertThat(value, notNullValue()),
                    () -> assertThat(value.defaultCase(), notNullValue()),
                    () -> assertThat(value.lowerCase(), notNullValue()),
                    () -> assertThat(value.index(), not(-1))
            );

        }
    }
}
