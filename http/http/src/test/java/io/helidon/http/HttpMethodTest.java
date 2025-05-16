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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class HttpMethodTest {
    private static final Class<Method> clazz = Method.class;
    private static final Set<String> constants = Stream.of(clazz.getDeclaredFields())
            .filter(it -> Modifier.isStatic(it.getModifiers()))
            .filter(it -> Modifier.isFinal(it.getModifiers()))
            .filter(it -> Modifier.isPublic(it.getModifiers()))
            .map(Field::getName)
            .collect(Collectors.toSet());


    @Test
    void testAllConstantsAreValid() throws NoSuchFieldException, IllegalAccessException {
        // this is to test correct initialization (there may be an issue when the constants
        // are defined on the interface and implemented by enum outside of it)
        for (String constant : constants) {
            Method value = (Method) clazz.getField(constant)
                    .get(null);

            assertAll(
                    () -> assertThat(value, notNullValue()),
                    () -> assertThat(value.text(), notNullValue()),
                    () -> assertThat(value.length(), not(0))
            );

        }
    }
}