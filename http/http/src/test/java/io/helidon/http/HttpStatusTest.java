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
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class HttpStatusTest {
    private static final Class<Status> clazz = Status.class;
    private static final Set<String> constants = Stream.of(clazz.getDeclaredFields())
            .filter(it -> Modifier.isStatic(it.getModifiers()))
            .filter(it -> Modifier.isFinal(it.getModifiers()))
            .filter(it -> Modifier.isPublic(it.getModifiers()))
            .map(Field::getName)
            .collect(Collectors.toSet());

    Status custom999_1 = Status.create(999);
    Status custom999_2 = Status.create(999);

    @Test
    void testSameInstanceForKnownStatus() {
        Status ok = Status.create(200);
        Status okFromBoth = Status.create(200, Status.OK_200.reasonPhrase());
        Status custom = Status.create(200, "Very-Fine");

        assertThat("Status from code must be the enum instance", ok, sameInstance(Status.OK_200));
        assertThat("Status from code an reason phrase that matches must be the enum instance",
                   okFromBoth,
                   sameInstance(Status.OK_200));
        assertThat("Status from code with custom phrase must differ", custom, not(sameInstance(Status.OK_200)));
        assertThat("Custom reason phrase should be present", custom.reasonPhrase(), is("Very-Fine"));
    }

    @Test
    void testEqualsAndHashCodeForCustomStatus() {
        assertThat(custom999_1, is(custom999_2));
        assertThat(custom999_1.hashCode(), is(custom999_2.hashCode()));
    }

    @Test
    void testAllConstantsAreValid() throws NoSuchFieldException, IllegalAccessException {
        // this is to test correct initialization (there may be an issue when the constants
        // are defined on the interface and implemented by enum outside of it)
        for (String constant : constants) {
            Status value = (Status) clazz.getField(constant)
                    .get(null);

            assertAll(
                    () -> assertThat(value, notNullValue()),
                    () -> assertThat(value.reasonPhrase(), notNullValue()),
                    () -> assertThat(value.codeText(), notNullValue()),
                    () -> assertThat(value.code(), not(0)),
                    () -> assertThat(value.codeText(), is(String.valueOf(value.code()))),
                    () -> assertThat(constant, endsWith("_" + value.code())),
                    () -> {
                        // except for teapot
                        if (value != Status.I_AM_A_TEAPOT_418) {
                            assertThat(constant,
                                       startsWith(value.reasonPhrase()
                                                          .toUpperCase(Locale.ROOT)
                                                          .replace(' ', '_')
                                                          .replace('-', '_')));
                        }
                    }
            );

        }
    }
}