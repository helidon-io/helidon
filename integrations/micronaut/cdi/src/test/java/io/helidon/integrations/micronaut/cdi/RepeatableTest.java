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

package io.helidon.integrations.micronaut.cdi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@HelidonTest
class RepeatableTest {

    @Inject
    private RepeatableCdiBean repeatableCdiBean;
    @Inject
    private RepeatableMicronautBean repeatableMicronautBean;

    @Test
    void testCdiInvocationCounter() {
        repeatableCdiBean.get();
        // Verify it is not invoked more than 1 time
        assertEquals(1, CounterCdiInterceptor.counter.get());
    }

    @Test
    void testMicronautInvocationCounter() {
        repeatableMicronautBean.get();
        // Verify it is not invoked more than 1 time
        assertEquals(1, CounterMicronautInterceptor.counter.get());
    }
}
