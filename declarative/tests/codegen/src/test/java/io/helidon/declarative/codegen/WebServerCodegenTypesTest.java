/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.helidon.common.context.Context;
import io.helidon.common.context.Context__ServiceDescriptor;
import io.helidon.common.types.TypeName;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers__ServiceDescriptor;
import io.helidon.http.Http;
import io.helidon.http.Method;
import io.helidon.http.Prologue__ServiceDescriptor;
import io.helidon.http.Status;
import io.helidon.service.inject.api.Scope;
import io.helidon.webserver.ServerRequest__ServiceDescriptor;
import io.helidon.webserver.ServerResponse__ServiceDescriptor;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.hamcrest.CoreMatchers;
import org.hamcrest.collection.IsEmptyCollection;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/*
This class must use the same package, to access package local type.
 */
public class WebServerCodegenTypesTest {
    @Test
    void testTypes() {
        // it is really important to test ALL constants on the class, so let's use relfection
        Field[] declaredFields = WebServerCodegenTypes.class.getDeclaredFields();

        Set<String> toCheck = new HashSet<>();
        Set<String> checked = new HashSet<>();
        Map<String, Field> fields = new HashMap<>();

        for (Field declaredField : declaredFields) {
            String name = declaredField.getName();

            assertThat(name + " must be a TypeName", declaredField.getType(), CoreMatchers.sameInstance(TypeName.class));
            assertThat(name + " must be static", Modifier.isStatic(declaredField.getModifiers()), is(true));
            assertThat(name + " must not be private", Modifier.isPrivate(declaredField.getModifiers()), is(false));
            assertThat(name + " must be final", Modifier.isFinal(declaredField.getModifiers()), is(true));

            toCheck.add(name);
            fields.put(name, declaredField);
        }

        checkField(toCheck, checked, fields, "COMMON_CONTEXT", Context.class);

        checkField(toCheck, checked, fields, "SERVER_REQUEST", ServerRequest.class);
        checkField(toCheck, checked, fields, "SERVER_RESPONSE", ServerResponse.class);
        checkField(toCheck, checked, fields, "SERVER_HTTP_FEATURE", HttpFeature.class);
        checkField(toCheck, checked, fields, "SERVER_HTTP_ROUTING_BUILDER", HttpRouting.Builder.class);
        checkField(toCheck, checked, fields, "SERVER_HTTP_RULES", HttpRules.class);

        checkField(toCheck, checked, fields, "HTTP_METHOD", Method.class);
        checkField(toCheck, checked, fields, "HTTP_STATUS", Status.class);
        checkField(toCheck, checked, fields, "HTTP_HEADER_NAME", HeaderName.class);
        checkField(toCheck, checked, fields, "HTTP_HEADER_NAMES", HeaderNames.class);
        checkField(toCheck, checked, fields, "HTTP_PATH_ANNOTATION", Http.Path.class);
        checkField(toCheck, checked, fields, "HTTP_METHOD_ANNOTATION", Http.HttpMethod.class);
        checkField(toCheck, checked, fields, "HTTP_STATUS_ANNOTATION", Http.Status.class);
        checkField(toCheck, checked, fields, "HTTP_PATH_PARAM_ANNOTATION", Http.PathParam.class);
        checkField(toCheck, checked, fields, "HTTP_QUERY_PARAM_ANNOTATION", Http.QueryParam.class);
        checkField(toCheck, checked, fields, "HTTP_HEADER_PARAM_ANNOTATION", Http.HeaderParam.class);
        checkField(toCheck, checked, fields, "HTTP_ENTITY_PARAM_ANNOTATION", Http.Entity.class);

        checkField(toCheck, checked, fields, "SERVICE_CONTEXT", Context__ServiceDescriptor.class);
        checkField(toCheck, checked, fields, "SERVICE_PROLOGUE", Prologue__ServiceDescriptor.class);
        checkField(toCheck, checked, fields, "SERVICE_HEADERS", Headers__ServiceDescriptor.class);
        checkField(toCheck, checked, fields, "SERVICE_SERVER_REQUEST", ServerRequest__ServiceDescriptor.class);
        checkField(toCheck, checked, fields, "SERVICE_SERVER_RESPONSE", ServerResponse__ServiceDescriptor.class);

        checkField(toCheck, checked, fields, "INJECT_SCOPE", Scope.class);

        assertThat(toCheck, IsEmptyCollection.empty());
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
