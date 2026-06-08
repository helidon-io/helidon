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

package io.helidon.security;

import io.helidon.security.providers.ProviderForTesting;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;
import io.helidon.tracing.Tracer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

@Testing.Test(perMethod = true)
class SecurityTracingTest {
    @Test
    void defaultTracerComesFromServiceRegistry() {
        Tracer tracer = mock(Tracer.class);
        Services.set(Tracer.class, tracer);
        ProviderForTesting provider = new ProviderForTesting("DENY");

        Security security = Security.builder()
                .addProvider(provider)
                .authenticationProvider(provider)
                .authorizationProvider(provider)
                .build();

        assertThat("Security tracer", security.tracer(), sameInstance(tracer));
        assertThat("Security context tracer", security.contextBuilder("unitTest").build().tracer(), sameInstance(tracer));
    }
}
