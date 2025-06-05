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

package io.helidon.declarative.codegen.http;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Http;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Method;
import io.helidon.http.Status;

import org.hamcrest.collection.IsEmptyCollection;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings("deprecation")
class DeclarativeCodegenHttpTypesTest {
    @Test
    void testTypes() {
        // it is really important to test ALL constants on the class, so let's use reflection
        Field[] declaredFields = HttpTypes.class.getDeclaredFields();

        Set<String> toCheck = new HashSet<>();
        Set<String> checked = new HashSet<>();
        Map<String, Field> fields = new HashMap<>();

        for (Field declaredField : declaredFields) {
            String name = declaredField.getName();

            if (!declaredField.getType().equals(TypeName.class)) {
                // ignore other types
                continue;
            }
            assertThat(name + " must be static", Modifier.isStatic(declaredField.getModifiers()), is(true));
            assertThat(name + " must be public", Modifier.isPublic(declaredField.getModifiers()), is(true));
            assertThat(name + " must be final", Modifier.isFinal(declaredField.getModifiers()), is(true));

            toCheck.add(name);
            fields.put(name, declaredField);
        }

        checkField(toCheck, checked, fields, "HTTP_METHOD", Method.class);
        checkField(toCheck, checked, fields, "HTTP_STATUS", Status.class);
        checkField(toCheck, checked, fields, "HTTP_HEADER_NAME", HeaderName.class);
        checkField(toCheck, checked, fields, "HTTP_HEADER_NAMES", HeaderNames.class);
        checkField(toCheck, checked, fields, "HTTP_HEADER", Header.class);
        checkField(toCheck, checked, fields, "HTTP_HEADER_VALUES", HeaderValues.class);
        checkField(toCheck, checked, fields, "HTTP_PATH_ANNOTATION", Http.Path.class);
        checkField(toCheck, checked, fields, "HTTP_METHOD_ANNOTATION", Http.HttpMethod.class);
        checkField(toCheck, checked, fields, "HTTP_PRODUCES_ANNOTATION", Http.Produces.class);
        checkField(toCheck, checked, fields, "HTTP_CONSUMES_ANNOTATION", Http.Consumes.class);
        checkField(toCheck, checked, fields, "HTTP_PATH_PARAM_ANNOTATION", Http.PathParam.class);
        checkField(toCheck, checked, fields, "HTTP_QUERY_PARAM_ANNOTATION", Http.QueryParam.class);
        checkField(toCheck, checked, fields, "HTTP_HEADER_PARAM_ANNOTATION", Http.HeaderParam.class);
        checkField(toCheck, checked, fields, "HTTP_ENTITY_ANNOTATION", Http.Entity.class);
        checkField(toCheck, checked, fields, "HTTP_MEDIA_TYPE", HttpMediaType.class);

        assertThat("If the collection is not empty, please add appropriate checkField line to this test",
                   toCheck,
                   IsEmptyCollection.empty());
    }

    private void checkField(Set<String> namesToCheck,
                            Set<String> checkedNames,
                            Map<String, Field> namesToFields,
                            String name,
                            Class<?> expectedType) {
        Field field = namesToFields.get(name);
        assertThat("Field " + name + " does not exist in the class", field, notNullValue());
        try {
            namesToCheck.remove(name);
            if (checkedNames.add(name)) {
                TypeName value = (TypeName) field.get(null);
                assertThat("Field " + name, value.fqName(), is(expectedType.getCanonicalName()));
            } else {
                fail("Field " + name + " is checked more than once");
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}