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

package io.helidon.declarative.codegen.faulttolerance;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.faulttolerance.Async;
import io.helidon.faulttolerance.Bulkhead;
import io.helidon.faulttolerance.CircuitBreaker;
import io.helidon.faulttolerance.ErrorChecker;
import io.helidon.faulttolerance.FallbackConfig;
import io.helidon.faulttolerance.Ft;
import io.helidon.faulttolerance.FtSupport;
import io.helidon.faulttolerance.Retry;
import io.helidon.faulttolerance.RetryConfig;
import io.helidon.faulttolerance.Timeout;

import org.hamcrest.collection.IsEmptyCollection;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class DeclarativeCodegenFtTypesTest {
    @Test
    void testTypes() {
        // it is really important to test ALL constants on the class, so let's use reflection
        Field[] declaredFields = FtTypes.class.getDeclaredFields();

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
            assertThat(name + " must be final", Modifier.isFinal(declaredField.getModifiers()), is(true));

            toCheck.add(name);
            fields.put(name, declaredField);
        }

        checkField(toCheck, checked, fields, "ERROR_CHECKER", ErrorChecker.class);

        checkField(toCheck, checked, fields, "FALLBACK_CONFIG", FallbackConfig.class);
        checkField(toCheck, checked, fields, "FALLBACK_ANNOTATION", Ft.Fallback.class);
        checkField(toCheck, checked, fields, "FALLBACK_GENERATED_METHOD", FtSupport.FallbackMethod.class);

        checkField(toCheck, checked, fields, "RETRY_ANNOTATION", Ft.Retry.class);
        checkField(toCheck, checked, fields, "RETRY", Retry.class);
        checkField(toCheck, checked, fields, "RETRY_CONFIG", RetryConfig.class);
        checkField(toCheck, checked, fields, "RETRY_GENERATED_METHOD", FtSupport.RetryMethod.class);

        checkField(toCheck, checked, fields, "CIRCUIT_BREAKER_ANNOTATION", Ft.CircuitBreaker.class);
        checkField(toCheck, checked, fields, "CIRCUIT_BREAKER", CircuitBreaker.class);
        checkField(toCheck, checked, fields, "CIRCUIT_BREAKER_GENERATED_METHOD", FtSupport.CircuitBreakerMethod.class);

        checkField(toCheck, checked, fields, "BULKHEAD_ANNOTATION", Ft.Bulkhead.class);
        checkField(toCheck, checked, fields, "BULKHEAD", Bulkhead.class);
        checkField(toCheck, checked, fields, "BULKHEAD_GENERATED_METHOD", FtSupport.BulkheadMethod.class);

        checkField(toCheck, checked, fields, "TIMEOUT_ANNOTATION", Ft.Timeout.class);
        checkField(toCheck, checked, fields, "TIMEOUT", Timeout.class);
        checkField(toCheck, checked, fields, "TIMEOUT_GENERATED_METHOD", FtSupport.TimeoutMethod.class);

        checkField(toCheck, checked, fields, "ASYNC_ANNOTATION", Ft.Async.class);
        checkField(toCheck, checked, fields, "ASYNC", Async.class);
        checkField(toCheck, checked, fields, "ASYNC_GENERATED_METHOD", FtSupport.AsyncMethod.class);


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