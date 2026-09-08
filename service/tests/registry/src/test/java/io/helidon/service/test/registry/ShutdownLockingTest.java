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
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class ShutdownLockingTest {
    private static final long TIMEOUT_SECONDS = 5;

    @Test
    void factoryLookupDoesNotDeadlockWithShutdown() throws InterruptedException {
        CountDownLatch factoryEntered = new CountDownLatch(1);
        CountDownLatch continueFactory = new CountDownLatch(1);
        CountDownLatch shutdownStarted = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        AtomicReference<Throwable> lookupFailure = new AtomicReference<>();
        AtomicReference<Throwable> shutdownFailure = new AtomicReference<>();

        ServiceRegistryConfig config = ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .addServiceDescriptor(ShutdownLockingTest_LookupServicesFactory__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(ShutdownLockingTest_ShutdownSignal__ServiceDescriptor.INSTANCE)
                .build();
        ServiceRegistryManager manager = ServiceRegistryManager.create(config);
        ServiceRegistry registry = manager.registry();
        LookupServicesFactory.prepare(registry, factoryEntered, continueFactory);
        ShutdownSignal.prepare(shutdownStarted);
        registry.get(ShutdownSignal.class);

        Thread lookupThread = Thread.ofVirtual()
                .name("service-factory-lookup")
                .unstarted(() -> run(lookupFailure, completed, () -> registry.get(FactoryProduct.class)));
        Thread shutdownThread = Thread.ofVirtual()
                .name("service-registry-shutdown")
                .unstarted(() -> run(shutdownFailure, completed, manager::shutdown));

        lookupThread.start();
        assertThat("factory lookup started", factoryEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
        shutdownThread.start();
        assertThat("shutdown started deactivating services",
                   shutdownStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                   is(true));
        continueFactory.countDown();

        assertThat("factory lookup and registry shutdown completed",
                   completed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                   is(true));
        assertThat("factory lookup failure", lookupFailure.get(), nullValue());
        assertThat("registry shutdown failure", shutdownFailure.get(), nullValue());
    }

    private static void run(AtomicReference<Throwable> failure, CountDownLatch completed, Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            failure.set(t);
        } finally {
            completed.countDown();
        }
    }

    interface FactoryProduct {
    }

    @Service.Singleton
    static class LookupServicesFactory implements Service.ServicesFactory<FactoryProduct> {
        private static volatile ServiceRegistry registry;
        private static volatile CountDownLatch factoryEntered;
        private static volatile CountDownLatch continueFactory;

        static void prepare(ServiceRegistry registry,
                            CountDownLatch factoryEntered,
                            CountDownLatch continueFactory) {
            LookupServicesFactory.registry = registry;
            LookupServicesFactory.factoryEntered = factoryEntered;
            LookupServicesFactory.continueFactory = continueFactory;
        }

        @Override
        public List<Service.QualifiedInstance<FactoryProduct>> services() {
            factoryEntered.countDown();
            try {
                assertThat("factory released", continueFactory.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }

            assertThat("registry inactive during shutdown",
                       registry.firstActive(ServiceRegistry.class).isEmpty(),
                       is(true));
            return List.of(Service.QualifiedInstance.create(new FactoryProduct() { }, Set.of()));
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL + 1)
    static class ShutdownSignal {
        private static volatile CountDownLatch shutdownStarted = new CountDownLatch(0);

        static void prepare(CountDownLatch shutdownStarted) {
            ShutdownSignal.shutdownStarted = shutdownStarted;
        }

        @Service.PreDestroy
        void signalShutdown() {
            shutdownStarted.countDown();
        }
    }
}
