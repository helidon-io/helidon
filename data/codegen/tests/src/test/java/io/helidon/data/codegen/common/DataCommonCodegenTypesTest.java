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
package io.helidon.data.codegen.common;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.data.Data;
import io.helidon.data.Page;
import io.helidon.data.Slice;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.jupiter.api.Assertions.fail;

public class DataCommonCodegenTypesTest {

    private static final Set<Field> types = new HashSet<>();
    private static final Set<String> toCheck = new HashSet<>();
    private static final Set<String> checked = new HashSet<>();
    private static final Map<String, Field> fields = new HashMap<>();

    @BeforeAll
    static void before() {
        Field[] declaredFields = DataCommonCodegenTypes.class.getDeclaredFields();
        for (Field declaredField : declaredFields) {
            if (declaredField.getType() == TypeName.class) {
                types.add(declaredField);
            }
        }
        for (Field declaredField : types) {
            String name = declaredField.getName();
            toCheck.add(name);
            fields.put(name, declaredField);
        }
    }

    @AfterAll
    static void after() {
        // Verify that all fields were checked
        assertThat(toCheck, empty());
    }

    @Test
    void allFieldsTest() {
        for (Field declaredField : types) {
            String name = declaredField.getName();
            assertThat(name + " must be a TypeName", declaredField.getType(), CoreMatchers.sameInstance(TypeName.class));
            assertThat(name + " must be static", Modifier.isStatic(declaredField.getModifiers()), is(true));
            assertThat(name + " must be package local, not public",
                       Modifier.isPublic(declaredField.getModifiers()),
                       is(false));
            assertThat(name + " must be package local, not private",
                       Modifier.isPrivate(declaredField.getModifiers()),
                       is(false));
            assertThat(name + " must be package local, not protected",
                       Modifier.isProtected(declaredField.getModifiers()),
                       is(false));
            assertThat(name + " must be final", Modifier.isFinal(declaredField.getModifiers()), is(true));
        }
    }

    @Test
    void testSlice() {
        checkField("SLICE", Slice.class);
    }

    @Test
    void testPage() {
        checkField("PAGE", Page.class);
    }

    @Test
    void testDataProvider() {
        checkField("PROVIDER", Data.Provider.class);
    }

    private static void checkField(String name, Class<?> expectedType) {
        Field field = fields.get(name);
        assertThat("Field " + name + " does not exist in the class", field, notNullValue());
        try {
            toCheck.remove(name);
            if (checked.add(name)) {
                TypeName value = (TypeName) field.get(null);
                assertThat("Field " + name, value.fqName(), is(expectedType.getCanonicalName()));
            } else {
                fail("Field " + name + " is checked more than once.class");
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
