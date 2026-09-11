/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.compatibility.app;

import java.util.concurrent.atomic.AtomicReference;

import io.helidon.declarative.tests.compatibility.v4.LegacyAsyncService;
import io.helidon.declarative.tests.compatibility.v4.LegacyValidatedValue;
import io.helidon.declarative.tests.compatibility.v4.LegacyValidationService;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.validation.ConstraintViolation;
import io.helidon.validation.ValidationException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class InterceptorCompatibilityChecks {
    private InterceptorCompatibilityChecks() {
    }

    static void verify(ServiceRegistry registry) throws Exception {
        var asyncService = registry.get(LegacyAsyncService.class);
        var executingThread = new AtomicReference<Thread>();
        Thread callerThread = Thread.currentThread();

        String result = asyncService.execute(() -> {
            executingThread.set(Thread.currentThread());
            return "legacy-async";
        });
        assertThat(result, is("legacy-async"));
        assertThat(executingThread.get(), notNullValue());
        assertThat(executingThread.get(), not(sameInstance(callerThread)));

        var failure = new IllegalStateException("legacy-async-failure");
        Throwable propagated = assertThrows(Exception.class, () -> asyncService.execute(() -> {
            throw failure;
        }));
        while (propagated.getCause() != null) {
            propagated = propagated.getCause();
        }
        assertThat("Async invocation preserves its original failure", propagated, sameInstance(failure));

        var validationService = registry.get(LegacyValidationService.class);
        assertThat(validationService.objectParameter(LegacyValidatedValue.create("valid-object")), is("valid-object"));
        assertThat(validationService.scalarResult("valid-result"), is("valid-result"));
        assertThat(validationService.objectResult("valid-return-object").value(), is("valid-return-object"));

        var parameterFailure = assertThrows(ValidationException.class,
                                            () -> validationService.objectParameter(LegacyValidatedValue.create("")));
        assertViolation(parameterFailure, ConstraintViolation.Location.PARAMETER);

        var scalarFailure = assertThrows(ValidationException.class, () -> validationService.scalarResult(""));
        assertViolation(scalarFailure, ConstraintViolation.Location.RETURN_VALUE);

        var objectFailure = assertThrows(ValidationException.class, () -> validationService.objectResult(""));
        assertViolation(objectFailure, ConstraintViolation.Location.RETURN_VALUE);
    }

    private static void assertViolation(ValidationException exception, ConstraintViolation.Location expectedLocation) {
        assertThat(exception.violations(), hasSize(1));
        var violation = exception.violations().getFirst();
        assertThat(violation.invalidValue(), is(""));
        assertThat(violation.location().stream().map(ConstraintViolation.PathElement::location).toList(),
                   hasItem(expectedLocation));
    }
}
