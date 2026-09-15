/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpMethodTest {
    private static final Class<Method> CLASS = Method.class;
    private static final Set<String> METHOD_CONSTANTS = Stream.of(CLASS.getDeclaredFields())
            .filter(it -> Modifier.isStatic(it.getModifiers()))
            .filter(it -> Modifier.isFinal(it.getModifiers()))
            .filter(it -> Modifier.isPublic(it.getModifiers()))
            .filter(it -> it.getType().equals(Method.class))
            .map(Field::getName)
            .collect(Collectors.toSet());
    private static final Set<String> STRING_CONSTANTS = Stream.of(CLASS.getDeclaredFields())
            .filter(it -> Modifier.isStatic(it.getModifiers()))
            .filter(it -> Modifier.isFinal(it.getModifiers()))
            .filter(it -> Modifier.isPublic(it.getModifiers()))
            .filter(it -> it.getType().equals(String.class))
            .map(Field::getName)
            .collect(Collectors.toSet());

    @Test
    void compatibleCreationNormalizesMethodName() {
        assertThat(Method.create("delete"), sameInstance(Method.DELETE));
        assertThat(Method.create("custom").text(), is("CUSTOM"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"delete", "Delete", "dElEtE"})
    void caseSensitiveCreationPreservesMethodName(String methodName) {
        Method method = Method.createCaseSensitive(methodName);

        assertAll(
                () -> assertThat(method.text(), is(methodName)),
                () -> assertThat(method, not(sameInstance(Method.DELETE)))
        );
    }

    @Test
    void caseSensitiveCreationUsesCanonicalInstanceForExactKnownMethod() {
        assertThat(Method.createCaseSensitive("DELETE"), sameInstance(Method.DELETE));
    }

    @Test
    void caseSensitiveCreationRejectsNullName() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                                                      () -> Method.createCaseSensitive(null));

        assertThat(exception.getMessage(), is("name"));
    }

    @Test
    void caseSensitiveCreationRejectsEmptyName() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> Method.createCaseSensitive(""));

        assertThat(exception.getMessage(), is("HTTP method name must not be empty"));
    }

    @Test
    void testAllMethodConstantsAreValid() throws NoSuchFieldException, IllegalAccessException {
        // this is to test correct initialization (there may be an issue when the constants
        // are defined on the interface and implemented by enum outside of it)
        for (String constant : METHOD_CONSTANTS) {
            Method value = (Method) CLASS.getField(constant)
                    .get(null);

            assertAll(
                    () -> assertThat(constant, value, notNullValue()),
                    () -> assertThat(constant, value.text(), notNullValue()),
                    () -> assertThat(constant, value.length(), not(0))
            );

            // make sure the string constant exists for this value
            String stringConstant = (String) CLASS.getField(constant + "_NAME")
                    .get(null);
            assertAll(
                    () -> assertThat(constant, stringConstant, notNullValue()),
                    () -> assertThat(constant, stringConstant.length(), not(0)),
                    () -> assertThat(constant, stringConstant, is(value.text()))
            );

        }
    }

    @Test
    void testAllStringConstantsAreValid() throws NoSuchFieldException, IllegalAccessException {
        Set<String> allValues = new HashSet<>();

        for (String constant : STRING_CONSTANTS) {
            String value = (String) CLASS.getField(constant)
                    .get(null);

            assertAll(
                    () -> assertThat(constant, value, notNullValue()),
                    () -> assertThat(constant, value, notNullValue()),
                    () -> assertThat(constant, value.length(), not(0))
            );

            assertThat(constant + " has duplicate value: " + value, allValues.add(value), is(true));
        }
    }
}
