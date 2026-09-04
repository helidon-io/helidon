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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.security.providers.ProviderForTesting;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.InterceptionMetadata;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;
import io.helidon.tracing.Tracer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

@Testing.Test(perMethod = true)
class SecurityTracingTest {
    @Test
    void managedSecurityUsesOwningRegistryTracer() {
        Tracer tracer = mock(Tracer.class);
        ServiceRegistryManager manager = manager(tracer, true);
        try {
            Security security = manager.registry().get(Security.class);

            assertThat("Security tracer", security.tracer(), sameInstance(tracer));
            assertThat("Security context tracer", security.contextBuilder("unitTest").build().tracer(), sameInstance(tracer));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void managedSecurityHonorsDisabledTracing() {
        Tracer tracer = mock(Tracer.class);
        ServiceRegistryManager manager = manager(tracer, false);
        try {
            Security security = manager.registry().get(Security.class);

            assertThat("Disabled security tracer", security.tracer(), not(sameInstance(tracer)));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void disabledManagedSecurityDoesNotInstantiateTracer() {
        AtomicBoolean tracerInstantiated = new AtomicBoolean();
        Tracer tracer = mock(Tracer.class);
        Config config = Config.just(ConfigSources.create(Map.of("security.tracing.enabled", "false")));
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .putContractInstance(Config.class, config)
                                                                                .addServiceDescriptor(new TracerDescriptor(tracer,
                                                                                                                           tracerInstantiated))
                                                                                .build());
        try {
            Security security = manager.registry().get(Security.class);

            assertThat("Disabled security tracer", security.tracer().enabled(), is(false));
            assertThat("Tracer instantiated", tracerInstantiated.get(), is(false));
        } finally {
            manager.shutdown();
        }
    }

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

    private static ServiceRegistryManager manager(Tracer tracer, boolean tracingEnabled) {
        Config config = Config.just(ConfigSources.create(Map.of("security.tracing.enabled",
                                                                 Boolean.toString(tracingEnabled))));
        return ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                     .putContractInstance(Config.class, config)
                                                     .putContractInstance(Tracer.class, tracer)
                                                     .build());
    }

    private static final class TracerDescriptor implements ServiceDescriptor<Tracer> {
        private static final TypeName SERVICE_TYPE = TypeName.create(Tracer.class);
        private static final TypeName DESCRIPTOR_TYPE = TypeName.create(TracerDescriptor.class);

        private final Tracer tracer;
        private final AtomicBoolean instantiated;

        private TracerDescriptor(Tracer tracer, AtomicBoolean instantiated) {
            this.tracer = tracer;
            this.instantiated = instantiated;
        }

        @Override
        public Object instantiate(DependencyContext ctx, InterceptionMetadata interceptionMetadata) {
            instantiated.set(true);
            return tracer;
        }

        @Override
        public TypeName serviceType() {
            return SERVICE_TYPE;
        }

        @Override
        public TypeName descriptorType() {
            return DESCRIPTOR_TYPE;
        }

        @Override
        public Set<ResolvedType> contracts() {
            return Set.of(ResolvedType.create(SERVICE_TYPE));
        }

        @Override
        public double weight() {
            return 1000;
        }
    }
}
