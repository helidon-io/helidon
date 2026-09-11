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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.service.registry.ScopeNotActiveException;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShutdownSupplierTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void injectedSupplierReturnsInitializedDependencyDuringPreDestroy() {
        ServiceRegistryManager manager = manager();
        ServiceRegistry registry = manager.registry();
        try {
            Registrar registrar = registry.get(Registrar.class);
            CachedDependency dependency = registrar.cached.get();
            assertThat("dependency completed post construction", dependency.initialized, is(true));
            registrar.onShutdown = () -> {
                assertThat("injected supplier retains the active dependency",
                           registrar.cached.get(),
                           sameInstance(dependency));
                assertThrows(ScopeNotActiveException.class, () -> registrar.registry.get(CachedDependency.class));
            };

            manager.shutdown();

            assertThat("pre-destroy callback count", registrar.callbackInvocations, is(1));
            assertThat("pre-destroy callback failure", registrar.callbackFailure, nullValue());
            assertThrows(ScopeNotActiveException.class, registrar.cached::get);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void injectedSuppliersDoNotCreateServicesDuringPreDestroy() {
        ServiceRegistryManager manager = manager();
        ServiceRegistry registry = manager.registry();
        try {
            Registrar registrar = registry.get(Registrar.class);
            FixtureState state = registry.get(FixtureState.class);
            registry.get(ProductFactory.class);
            assertThat("lazy service starts uninitialized", state.lazyConstructions, is(0));
            assertThat("initialized supplier factory has not produced a value", state.productCalls, is(0));
            registrar.onShutdown = () -> {
                assertThrows(ScopeNotActiveException.class, registrar.lazy::get);
                assertThrows(ScopeNotActiveException.class, registrar.product::get);
            };

            manager.shutdown();

            assertThat("pre-destroy callback count", registrar.callbackInvocations, is(1));
            assertThat("pre-destroy callback failure", registrar.callbackFailure, nullValue());
            assertThat("shutdown did not construct the lazy service", state.lazyConstructions, is(0));
            assertThat("shutdown did not invoke the initialized supplier factory", state.productCalls, is(0));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void injectedSupplierRejectsDestroyedDependencyDuringPreDestroy() {
        ServiceRegistryManager manager = manager();
        ServiceRegistry registry = manager.registry();
        try {
            Registrar registrar = registry.get(Registrar.class);
            FixtureState state = registry.get(FixtureState.class);
            registrar.destroyed.get();
            registrar.onShutdown = () -> {
                assertThat("higher run-level dependency was destroyed first", state.destroyedCallbacks, is(1));
                assertThrows(ScopeNotActiveException.class, registrar.destroyed::get);
            };

            manager.shutdown();

            assertThat("pre-destroy callback count", registrar.callbackInvocations, is(1));
            assertThat("pre-destroy callback failure", registrar.callbackFailure, nullValue());
            assertThat("destroyed dependency was not recreated", state.destroyedConstructions, is(1));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void injectedSupplierFallbackIsLimitedToPreDestroyThread() {
        ServiceRegistryManager manager = manager();
        ServiceRegistry registry = manager.registry();
        try {
            Registrar registrar = registry.get(Registrar.class);
            CachedDependency dependency = registrar.cached.get();
            AtomicReference<Throwable> threadFailure = new AtomicReference<>();
            AtomicReference<CachedDependency> threadResult = new AtomicReference<>();
            registrar.onShutdown = () -> {
                Thread lookup = Thread.ofVirtual()
                        .name("pre-destroy-supplier-lookup")
                        .start(() -> {
                            try {
                                threadResult.set(registrar.cached.get());
                            } catch (Throwable t) {
                                threadFailure.set(t);
                            }
                        });
                try {
                    try {
                        assertThat("lookup from another thread completed", lookup.join(TIMEOUT), is(true));
                    } finally {
                        lookup.interrupt();
                        assertThat("lookup thread terminated", lookup.join(TIMEOUT), is(true));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                assertThat("another thread did not obtain a dependency", threadResult.get(), nullValue());
                assertThat("another thread cannot use the callback context",
                           threadFailure.get(),
                           instanceOf(ScopeNotActiveException.class));
                assertThat("callback thread still has its active dependency",
                           registrar.cached.get(),
                           sameInstance(dependency));
            };

            manager.shutdown();

            assertThat("pre-destroy callback count", registrar.callbackInvocations, is(1));
            assertThat("pre-destroy callback failure", registrar.callbackFailure, nullValue());
            assertThrows(ScopeNotActiveException.class, registrar.cached::get);
        } finally {
            manager.shutdown();
        }
    }

    private static ServiceRegistryManager manager() {
        FixtureState state = new FixtureState();
        ServiceRegistryConfig config = ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .addServiceDescriptor(ShutdownSupplierTest_Registrar__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(ShutdownSupplierTest_CachedDependency__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(ShutdownSupplierTest_LazyDependency__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(ShutdownSupplierTest_DestroyedDependency__ServiceDescriptor.INSTANCE)
                .putServiceInstance(ShutdownSupplierTest_ProductFactory__ServiceDescriptor.INSTANCE,
                                    new ProductFactory(state))
                .putServiceInstance(ShutdownSupplierTest_FixtureState__ServiceDescriptor.INSTANCE, state)
                .build();
        return ServiceRegistryManager.create(config);
    }

    interface Product {
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL + 1)
    static class Registrar {
        private final Supplier<CachedDependency> cached;
        private final Supplier<LazyDependency> lazy;
        private final Supplier<DestroyedDependency> destroyed;
        private final Supplier<Product> product;
        private final ServiceRegistry registry;
        private Runnable onShutdown = () -> { };
        private Throwable callbackFailure;
        private int callbackInvocations;

        @Service.Inject
        Registrar(Supplier<CachedDependency> cached,
                  Supplier<LazyDependency> lazy,
                  Supplier<DestroyedDependency> destroyed,
                  Supplier<Product> product,
                  ServiceRegistry registry) {
            this.cached = cached;
            this.lazy = lazy;
            this.destroyed = destroyed;
            this.product = product;
            this.registry = registry;
        }

        @Service.PreDestroy
        void shutdown() {
            try {
                onShutdown.run();
            } catch (Throwable t) {
                callbackFailure = t;
            } finally {
                callbackInvocations++;
            }
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL - 1)
    static class CachedDependency {
        private boolean initialized;

        @Service.PostConstruct
        void initialize() {
            initialized = true;
        }
    }

    @Service.Singleton
    static class LazyDependency {
        @Service.Inject
        LazyDependency(FixtureState state) {
            state.lazyConstructions++;
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL + 2)
    static class DestroyedDependency {
        private final FixtureState state;

        @Service.Inject
        DestroyedDependency(FixtureState state) {
            this.state = state;
            state.destroyedConstructions++;
        }

        @Service.PreDestroy
        void shutdown() {
            state.destroyedCallbacks++;
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL - 1)
    static class ProductFactory implements Supplier<Product> {
        private final FixtureState state;

        @Service.Inject
        ProductFactory(FixtureState state) {
            this.state = state;
        }

        @Override
        public Product get() {
            state.productCalls++;
            return new Product() { };
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL - 2)
    static class FixtureState {
        private int lazyConstructions;
        private int productCalls;
        private int destroyedConstructions;
        private int destroyedCallbacks;
    }
}
