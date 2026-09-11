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

package io.helidon.service.registry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.GeneratedService.ServicesFactoryInterceptionWrapper;
import io.helidon.service.registry.Service.QualifiedInstance;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShutdownCachedInstancesTest {
    private static final int TIMEOUT_SECONDS = 10;
    private static final TypeName SCOPE = TypeName.create("test.CleanupScope");
    private static final TypeName CONTRACT = TypeName.create("test.Dependency");
    private static final Lookup LOOKUP = Lookup.builder().addContract(CONTRACT).build();
    private static final Lookup INJECTED_LOOKUP = Lookup.builder(LOOKUP)
            .dependency(Dependency.builder()
                                .service(TypeName.create("test.Cleanup"))
                                .name("dependency")
                                .contract(CONTRACT)
                                .descriptor(TypeName.create("test.CleanupDescriptor"))
                                .descriptorConstant("DEPENDENCY")
                                .typeName(TypeName.builder(TypeName.create(Supplier.class)).addTypeArgument(CONTRACT).build())
                                .isSupplier(true)
                                .build())
            .build();

    @Test
    void cleanupSupplierUsesOnlyItsOwnScope() throws InterruptedException {
        ServiceRegistryManager registryManager = registryManager();
        CoreServiceRegistry registry = (CoreServiceRegistry) registryManager.registry();
        CountDownLatch otherCleanupStarted = new CountDownLatch(1);
        CountDownLatch continueOtherCleanup = new CountDownLatch(1);
        AtomicReference<Throwable> otherShutdownFailure = new AtomicReference<>();
        AtomicInteger cleanupInvocations = new AtomicInteger();

        try (TestScope first = new TestScope(registry, "first");
             TestScope second = new TestScope(registry, "second")) {
            TestDescriptor dependency = new TestDescriptor("Dependency", 1, FactoryType.SERVICE, Object::new, _ -> { });
            ServiceManager<Object> firstManager = manager(registry, first, dependency);
            ServiceManager<Object> secondManager = manager(registry, second, dependency);
            Supplier<Object> firstSupply = new ServiceSupplies.ServiceSupply<>(INJECTED_LOOKUP, List.of(firstManager));
            Supplier<Object> secondSupply = new ServiceSupplies.ServiceSupply<>(INJECTED_LOOKUP, List.of(secondManager));
            Supplier<Object> firstLookup = new ServiceSupplies.ServiceSupply<>(LOOKUP, List.of(firstManager));
            Supplier<Object> secondLookup = new ServiceSupplies.ServiceSupply<>(LOOKUP, List.of(secondManager));
            Object firstInstance = firstSupply.get();
            Object secondInstance = secondSupply.get();
            assertThat("separate scope instances", firstInstance, not(sameInstance(secondInstance)));

            TestDescriptor firstCleanup = new TestDescriptor("FirstCleanup", 2, FactoryType.SERVICE, Object::new, _ -> {
                assertThat("cached dependency in the callback's scope", firstSupply.get(), sameInstance(firstInstance));
                assertThrows(ScopeNotActiveException.class, secondSupply::get);
                assertThrows(ScopeNotActiveException.class, firstLookup::get);
                cleanupInvocations.incrementAndGet();
            });
            TestDescriptor secondCleanup = new TestDescriptor("SecondCleanup", 2, FactoryType.SERVICE, Object::new, _ -> {
                otherCleanupStarted.countDown();
                await(continueOtherCleanup, "continue the second scope cleanup");
            });
            new ServiceSupplies.ServiceSupply<>(LOOKUP, List.of(manager(registry, first, firstCleanup))).get();
            new ServiceSupplies.ServiceSupply<>(LOOKUP, List.of(manager(registry, second, secondCleanup))).get();

            Thread otherShutdown = thread("second-scope-shutdown", second::close, otherShutdownFailure);
            otherShutdown.start();
            try {
                assertThat("second scope cleanup started",
                           otherCleanupStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                           is(true));
                assertThrows(ScopeNotActiveException.class, secondLookup::get);
                assertThrows(ScopeNotActiveException.class, secondSupply::get);
                first.close();
                assertThat("first scope cleanup completed", cleanupInvocations.get(), is(1));
                assertThrows(ScopeNotActiveException.class, firstSupply::get);
            } finally {
                continueOtherCleanup.countDown();
                otherShutdown.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            }

            assertThat("second scope shutdown completed", otherShutdown.isAlive(), is(false));
            assertThat("second scope shutdown failure", otherShutdownFailure.get(), nullValue());
        } finally {
            registryManager.shutdown();
        }
    }

    @Test
    void cleanupDoesNotWaitForAnUnfinishedSupplierFactory() throws InterruptedException {
        ServiceRegistryManager registryManager = registryManager();
        CoreServiceRegistry registry = (CoreServiceRegistry) registryManager.registry();
        CountDownLatch factoryStarted = new CountDownLatch(1);
        CountDownLatch continueFactory = new CountDownLatch(1);
        CountDownLatch cleanupCompleted = new CountDownLatch(1);
        AtomicReference<Throwable> activationFailure = new AtomicReference<>();
        AtomicReference<Throwable> shutdownFailure = new AtomicReference<>();
        AtomicReference<Object> supplied = new AtomicReference<>();
        AtomicInteger factoryInvocations = new AtomicInteger();
        AtomicInteger cleanupInvocations = new AtomicInteger();
        Object instance = new Object();

        try (TestScope scope = new TestScope(registry, "factory")) {
            Supplier<Object> factory = () -> {
                factoryInvocations.incrementAndGet();
                factoryStarted.countDown();
                await(continueFactory, "finish the supplier factory");
                return instance;
            };
            TestDescriptor dependency = new TestDescriptor("DependencyFactory", 1, FactoryType.SUPPLIER, () -> factory,
                                                            _ -> { });
            ServiceManager<Object> dependencyManager = manager(registry, scope, dependency);
            Supplier<Object> dependencySupply = new ServiceSupplies.ServiceSupply<>(INJECTED_LOOKUP,
                                                                                   List.of(dependencyManager));
            Activator<Object> dependencyActivator = dependencyManager.activator();
            TestDescriptor cleanup = new TestDescriptor("Cleanup", 2, FactoryType.SERVICE, Object::new, _ -> {
                try {
                    assertThrows(ScopeNotActiveException.class, dependencySupply::get);
                    cleanupInvocations.incrementAndGet();
                } finally {
                    cleanupCompleted.countDown();
                }
            });
            new ServiceSupplies.ServiceSupply<>(LOOKUP, List.of(manager(registry, scope, cleanup))).get();

            Thread activation = thread("supplier-factory-activation", () -> supplied.set(dependencySupply.get()),
                                       activationFailure);
            Thread shutdown = thread("factory-scope-shutdown", scope::close, shutdownFailure);
            activation.start();
            try {
                assertThat("supplier factory started", factoryStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
                assertThat("factory is publishing its target instance", dependencyActivator.phase(), is(ActivationPhase.ACTIVE));
                shutdown.start();
                assertThat("cleanup rejected the unfinished factory without waiting",
                           cleanupCompleted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                           is(true));
                assertThat("factory has not been released", continueFactory.getCount(), is(1L));
                assertThat("cleanup invocation completed", cleanupInvocations.get(), is(1));
                assertThat("factory has not supplied an instance", supplied.get(), nullValue());
            } finally {
                continueFactory.countDown();
                activation.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
                shutdown.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            }

            assertThat("supplier activation completed", activation.isAlive(), is(false));
            assertThat("scope shutdown completed", shutdown.isAlive(), is(false));
            assertThat("supplier activation failure", activationFailure.get(), nullValue());
            assertThat("scope shutdown failure", shutdownFailure.get(), nullValue());
            assertThat("supplier factory called once", factoryInvocations.get(), is(1));
            assertThat("original factory invocation returned", supplied.get(), sameInstance(instance));
        } finally {
            registryManager.shutdown();
        }
    }

    @Test
    void cleanupDoesNotWaitForAnUnfinishedInterceptionWrapper() throws InterruptedException {
        ServiceRegistryManager registryManager = registryManager();
        CoreServiceRegistry registry = (CoreServiceRegistry) registryManager.registry();
        CountDownLatch wrappingStarted = new CountDownLatch(1);
        CountDownLatch continueWrapping = new CountDownLatch(1);
        CountDownLatch cleanupCompleted = new CountDownLatch(1);
        AtomicReference<Throwable> lookupFailure = new AtomicReference<>();
        AtomicReference<Throwable> shutdownFailure = new AtomicReference<>();
        AtomicReference<Object> supplied = new AtomicReference<>();
        AtomicInteger wrapInvocations = new AtomicInteger();
        AtomicInteger cleanupInvocations = new AtomicInteger();
        Object original = new Object();
        Object wrapped = new Object();

        try (TestScope scope = new TestScope(registry, "unfinished-interception")) {
            var factory = new ServicesFactoryInterceptionWrapper<Object>(() -> List.of(QualifiedInstance.create(original))) {
                @Override
                protected Object wrap(Object originalInstance) {
                    assertThat("instance passed to interception", originalInstance, sameInstance(original));
                    wrapInvocations.incrementAndGet();
                    wrappingStarted.countDown();
                    try {
                        if (!continueWrapping.await(2L * TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            throw new AssertionError("Timed out waiting to finish interception wrapping");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("Interrupted waiting to finish interception wrapping", e);
                    }
                    return wrapped;
                }
            };
            TestDescriptor dependency = new TestDescriptor("InterceptedServicesFactory", 1, FactoryType.SERVICES,
                                                            () -> factory, _ -> { });
            ServiceManager<Object> dependencyManager = manager(registry, scope, dependency);
            Supplier<Object> dependencySupply = new ServiceSupplies.ServiceSupply<>(INJECTED_LOOKUP,
                                                                                   List.of(dependencyManager));
            TestDescriptor cleanup = new TestDescriptor("Cleanup", 2, FactoryType.SERVICE, Object::new, _ -> {
                try {
                    assertThrows(ScopeNotActiveException.class, dependencySupply::get);
                    cleanupInvocations.incrementAndGet();
                } finally {
                    cleanupCompleted.countDown();
                }
            });
            new ServiceSupplies.ServiceSupply<>(LOOKUP, List.of(manager(registry, scope, cleanup))).get();

            Thread lookup = thread("interception-wrapper-lookup", () -> supplied.set(dependencySupply.get()), lookupFailure);
            Thread shutdown = thread("interception-scope-shutdown", scope::close, shutdownFailure);
            lookup.start();
            try {
                assertThat("interception wrapping started",
                           wrappingStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                           is(true));
                shutdown.start();
                assertThat("cleanup rejected the unfinished wrapper without waiting",
                           cleanupCompleted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                           is(true));
                assertThat("wrapping has not been released", continueWrapping.getCount(), is(1L));
                assertThat("cleanup invocation completed", cleanupInvocations.get(), is(1));
                assertThat("wrapping has not supplied an instance", supplied.get(), nullValue());
            } finally {
                continueWrapping.countDown();
                lookup.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
                shutdown.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            }

            assertThat("interception lookup completed", lookup.isAlive(), is(false));
            assertThat("scope shutdown completed", shutdown.isAlive(), is(false));
            assertThat("interception lookup failure", lookupFailure.get(), nullValue());
            assertThat("scope shutdown failure", shutdownFailure.get(), nullValue());
            assertThat("interception wrapper called once", wrapInvocations.get(), is(1));
            assertThat("original lookup returned the wrapped instance", supplied.get(), sameInstance(wrapped));
        } finally {
            registryManager.shutdown();
        }
    }

    @Test
    void cleanupDoesNotInitializeAnUnusedInterceptionWrapper() {
        ServiceRegistryManager registryManager = registryManager();
        CoreServiceRegistry registry = (CoreServiceRegistry) registryManager.registry();
        AtomicInteger wrapInvocations = new AtomicInteger();
        AtomicInteger cleanupInvocations = new AtomicInteger();
        Object original = new Object();
        Object wrapped = new Object();

        try (TestScope scope = new TestScope(registry, "unused-interception")) {
            var factory = new ServicesFactoryInterceptionWrapper<Object>(() -> List.of(QualifiedInstance.create(original))) {
                @Override
                protected Object wrap(Object originalInstance) {
                    assertThat("instance passed to interception", originalInstance, sameInstance(original));
                    wrapInvocations.incrementAndGet();
                    return wrapped;
                }
            };
            TestDescriptor dependency = new TestDescriptor("InterceptedServicesFactory", 1, FactoryType.SERVICES,
                                                            () -> factory, _ -> { });
            ServiceManager<Object> dependencyManager = manager(registry, scope, dependency);
            Supplier<Object> dependencySupply = new ServiceSupplies.ServiceSupply<>(INJECTED_LOOKUP,
                                                                                   List.of(dependencyManager));
            ServiceInstance<Object> cached = new ServiceSupplies.ServiceInstanceSupply<>(LOOKUP,
                                                                                         List.of(dependencyManager)).get();
            assertThat("factory product is available without dereferencing it", cached, notNullValue());
            assertThat("interception wrapper has not been initialized", wrapInvocations.get(), is(0));
            TestDescriptor cleanup = new TestDescriptor("Cleanup", 2, FactoryType.SERVICE, Object::new, _ -> {
                assertThrows(ScopeNotActiveException.class, dependencySupply::get);
                assertThat("cleanup did not initialize interception", wrapInvocations.get(), is(0));
                cleanupInvocations.incrementAndGet();
            });
            new ServiceSupplies.ServiceSupply<>(LOOKUP, List.of(manager(registry, scope, cleanup))).get();

            scope.close();

            assertThat("cleanup invocation completed", cleanupInvocations.get(), is(1));
            assertThat("unused interception wrapper was never initialized", wrapInvocations.get(), is(0));
        } finally {
            registryManager.shutdown();
        }
    }

    @Test
    void cleanupCanUseAnInitializedInterceptionWrapper() {
        ServiceRegistryManager registryManager = registryManager();
        CoreServiceRegistry registry = (CoreServiceRegistry) registryManager.registry();
        AtomicInteger wrapInvocations = new AtomicInteger();
        AtomicInteger cleanupInvocations = new AtomicInteger();
        Object original = new Object();
        Object wrapped = new Object();

        try (TestScope scope = new TestScope(registry, "initialized-interception")) {
            var factory = new ServicesFactoryInterceptionWrapper<Object>(() -> List.of(QualifiedInstance.create(original))) {
                @Override
                protected Object wrap(Object originalInstance) {
                    assertThat("instance passed to interception", originalInstance, sameInstance(original));
                    wrapInvocations.incrementAndGet();
                    return wrapped;
                }
            };
            TestDescriptor dependency = new TestDescriptor("InterceptedServicesFactory", 1, FactoryType.SERVICES,
                                                            () -> factory, _ -> { });
            ServiceManager<Object> dependencyManager = manager(registry, scope, dependency);
            Supplier<Object> dependencySupply = new ServiceSupplies.ServiceSupply<>(INJECTED_LOOKUP,
                                                                                   List.of(dependencyManager));
            assertThat("initial lookup returned the wrapped instance", dependencySupply.get(), sameInstance(wrapped));
            assertThat("interception wrapper was initialized once", wrapInvocations.get(), is(1));
            TestDescriptor cleanup = new TestDescriptor("Cleanup", 2, FactoryType.SERVICE, Object::new, _ -> {
                assertThat("cleanup uses the cached wrapped instance", dependencySupply.get(), sameInstance(wrapped));
                assertThat("cleanup did not initialize interception again", wrapInvocations.get(), is(1));
                cleanupInvocations.incrementAndGet();
            });
            new ServiceSupplies.ServiceSupply<>(LOOKUP, List.of(manager(registry, scope, cleanup))).get();

            scope.close();

            assertThat("cleanup invocation completed", cleanupInvocations.get(), is(1));
            assertThat("interception wrapper called once", wrapInvocations.get(), is(1));
        } finally {
            registryManager.shutdown();
        }
    }

    private static ServiceRegistryManager registryManager() {
        return ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                     .discoverServices(false)
                                                     .discoverServicesFromServiceLoader(false)
                                                     .interceptionEnabled(false)
                                                     .build());
    }

    private static ServiceManager<Object> manager(CoreServiceRegistry registry, TestScope scope, TestDescriptor descriptor) {
        var provider = new ServiceProvider<>(registry, descriptor);
        return new ServiceManager<>(registry, () -> scope, provider, false, Activators.create(registry, provider));
    }

    private static Thread thread(String name, Runnable action, AtomicReference<Throwable> failure) {
        return Thread.ofVirtual().name(name).unstarted(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
    }

    private static void await(CountDownLatch latch, String description) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to " + description);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted waiting to " + description, e);
        }
    }

    private static final class TestScope implements Scope {
        private final ScopedRegistryImpl registry;

        private TestScope(CoreServiceRegistry registry, String id) {
            this.registry = new ScopedRegistryImpl(registry, SCOPE, id, Map.of());
            this.registry.activate();
        }

        @Override
        public void close() {
            registry.deactivate();
        }

        @Override
        public ScopedRegistry registry() {
            return registry;
        }
    }

    private static final class TestDescriptor implements ServiceDescriptor<Object> {
        private final TypeName typeName;
        private final double runLevel;
        private final FactoryType factoryType;
        private final Supplier<?> instanceSupplier;
        private final Consumer<Object> cleanup;

        private TestDescriptor(String typeName,
                               double runLevel,
                               FactoryType factoryType,
                               Supplier<?> instanceSupplier,
                               Consumer<Object> cleanup) {
            this.typeName = TypeName.create("test." + typeName);
            this.runLevel = runLevel;
            this.factoryType = factoryType;
            this.instanceSupplier = instanceSupplier;
            this.cleanup = cleanup;
        }

        @Override
        public TypeName serviceType() {
            return typeName;
        }

        @Override
        public TypeName descriptorType() {
            return typeName;
        }

        @Override
        public TypeName scope() {
            return SCOPE;
        }

        @Override
        public Optional<Double> runLevel() {
            return Optional.of(runLevel);
        }

        @Override
        public FactoryType factoryType() {
            return factoryType;
        }

        @Override
        public Set<ResolvedType> contracts() {
            return Set.of(ResolvedType.create(CONTRACT));
        }

        @Override
        public Object instantiate(DependencyContext ctx, InterceptionMetadata interceptionMetadata) {
            return instanceSupplier.get();
        }

        @Override
        public void preDestroy(Object instance) {
            cleanup.accept(instance);
        }
    }
}
