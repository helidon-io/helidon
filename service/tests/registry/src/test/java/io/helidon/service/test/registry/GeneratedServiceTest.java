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

package io.helidon.service.test.registry;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.FactoryType;
import io.helidon.service.registry.GeneratedService;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneratedServiceTest {
    @Test
    void optionalSupplierFactoryInterceptionWrapperRejectsNullDelegate() {
        NullPointerException e = assertThrows(NullPointerException.class, () -> new TestOptionalSupplierWrapper(null));

        assertThat(e.getMessage(), is("delegate"));
    }

    @Test
    void broadLookupsSkipHiddenFactoryProviderDescriptors() {
        var descriptor = new HiddenFactoryProviderInfo();

        assertThat(Lookup.EMPTY.matches(descriptor), is(false));
        assertThat(Lookup.builder()
                           .addScope(Service.Singleton.TYPE)
                           .runLevel(Service.RunLevel.NORMAL)
                           .build()
                           .matches(descriptor),
                   is(false));
        assertThat(Lookup.builder()
                           .addFactoryType(FactoryType.NONE)
                           .build()
                           .matches(descriptor),
                   is(false));
        assertThat(Lookup.builder()
                           .addContract(HiddenFactoryProviderInfo.PROVIDER_CONTRACT)
                           .addFactoryType(FactoryType.SUPPLIER)
                           .build()
                           .matches(descriptor),
                   is(true));
        assertThat(Lookup.builder()
                           .serviceType(HiddenFactoryProviderInfo.SERVICE_TYPE)
                           .build()
                           .matches(descriptor),
                   is(true));
    }

    @Test
    void servicesFactoryInterceptionWrapperWrapsQualifiedInstanceLazily() {
        Qualifier qualifier = Qualifier.createNamed("test");
        Set<Qualifier> qualifiers = Set.of(qualifier);
        AtomicInteger gets = new AtomicInteger();
        AtomicInteger wraps = new AtomicInteger();
        Service.QualifiedInstance<String> qualifiedInstance = new Service.QualifiedInstance<>() {
            @Override
            public String get() {
                gets.incrementAndGet();
                return "value";
            }

            @Override
            public Set<Qualifier> qualifiers() {
                return qualifiers;
            }
        };
        TestServicesFactoryWrapper wrapper = new TestServicesFactoryWrapper(() -> List.of(qualifiedInstance), wraps);

        List<Service.QualifiedInstance<String>> wrappedInstances = wrapper.services();

        assertThat(wrappedInstances, is(List.of(wrappedInstances.getFirst())));
        assertThat(wrappedInstances.getFirst().qualifiers(), is(qualifiers));
        assertThat(gets.get(), is(0));
        assertThat(wraps.get(), is(0));

        assertThat(wrappedInstances.getFirst().get(), is("wrapped-value"));
        assertThat(wrappedInstances.getFirst().get(), is("wrapped-value"));
        assertThat(gets.get(), is(1));
        assertThat(wraps.get(), is(1));
    }

    @Test
    void servicesFactoryInterceptionWrapperWrapsQualifiedInstanceOnceUnderContention() throws Exception {
        AtomicInteger gets = new AtomicInteger();
        AtomicInteger wraps = new AtomicInteger();
        CountDownLatch delegateEntered = new CountDownLatch(1);
        CountDownLatch releaseDelegate = new CountDownLatch(1);
        Service.QualifiedInstance<String> qualifiedInstance = new Service.QualifiedInstance<>() {
            @Override
            public String get() {
                gets.incrementAndGet();
                try {
                    delegateEntered.countDown();
                    assertThat(releaseDelegate.await(5, TimeUnit.SECONDS), is(true));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                return "value";
            }

            @Override
            public Set<Qualifier> qualifiers() {
                return Set.of();
            }
        };
        TestServicesFactoryWrapper wrapper = new TestServicesFactoryWrapper(() -> List.of(qualifiedInstance), wraps);
        Service.QualifiedInstance<String> wrappedInstance = wrapper.services().getFirst();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return wrappedInstance.get();
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return wrappedInstance.get();
            });

            assertThat(ready.await(5, TimeUnit.SECONDS), is(true));
            start.countDown();
            assertThat(delegateEntered.await(5, TimeUnit.SECONDS), is(true));
            releaseDelegate.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS), is("wrapped-value"));
            assertThat(second.get(5, TimeUnit.SECONDS), is("wrapped-value"));
        } finally {
            executor.shutdownNow();
        }

        assertThat(gets.get(), is(1));
        assertThat(wraps.get(), is(1));
    }

    private static final class TestOptionalSupplierWrapper
            extends GeneratedService.OptionalSupplierFactoryInterceptionWrapper<String> {
        private TestOptionalSupplierWrapper(Supplier<Optional<String>> delegate) {
            super(delegate);
        }

        @Override
        protected String wrap(String originalInstance) {
            return originalInstance;
        }
    }

    private static final class HiddenFactoryProviderInfo implements ServiceInfo {
        private static final TypeName SERVICE_TYPE = TypeName.create("io.helidon.service.test.registry.HiddenProvider");
        private static final TypeName DESCRIPTOR_TYPE =
                TypeName.create("io.helidon.service.test.registry.HiddenProvider__ServiceDescriptor");
        private static final ResolvedType PROVIDER_CONTRACT = ResolvedType.create(Supplier.class);

        @Override
        public TypeName serviceType() {
            return SERVICE_TYPE;
        }

        @Override
        public TypeName descriptorType() {
            return DESCRIPTOR_TYPE;
        }

        @Override
        public Set<ResolvedType> factoryContracts() {
            return Set.of(PROVIDER_CONTRACT);
        }

        @Override
        public Optional<Double> runLevel() {
            return Optional.of(Service.RunLevel.NORMAL);
        }

        @Override
        public FactoryType factoryType() {
            return FactoryType.SUPPLIER;
        }

        @Override
        public TypeName scope() {
            return Service.Singleton.TYPE;
        }
    }

    private static final class TestServicesFactoryWrapper
            extends GeneratedService.ServicesFactoryInterceptionWrapper<String> {
        private final AtomicInteger wraps;

        private TestServicesFactoryWrapper(Service.ServicesFactory<String> delegate, AtomicInteger wraps) {
            super(delegate);
            this.wraps = wraps;
        }

        @Override
        protected String wrap(String originalInstance) {
            wraps.incrementAndGet();
            return "wrapped-" + originalInstance;
        }
    }
}
