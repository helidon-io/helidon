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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.types.TypeName;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScopedRegistryImplTest {
    private static final long TIMEOUT_SECONDS = 5;
    private static final ServiceDescriptor<Object> BLOCKING_DESCRIPTOR = new TestDescriptor("Blocking", 1);
    private static final ServiceDescriptor<Object> PENDING_DESCRIPTOR = new TestDescriptor("Pending", 0);

    @Test
    void handedOutInitActivatorIsTerminalAfterShutdown() {
        ScopedRegistryImpl registry = registry();
        TestActivator pending = TestActivator.init(PENDING_DESCRIPTOR);
        Activator<Object> handedOut = registry.activator(pending.descriptor(), () -> pending);

        registry.deactivate();
        handedOut.instances(Lookup.EMPTY);

        assertThat(pending.phase(), is(ActivationPhase.DESTROYED));
    }

    @Test
    void initActivatorIsUnavailableDuringShutdown() throws InterruptedException {
        CountDownLatch deactivationStarted = new CountDownLatch(1);
        CountDownLatch continueDeactivation = new CountDownLatch(1);
        ScopedRegistryImpl registry = registry();
        TestActivator blocker = TestActivator.active(BLOCKING_DESCRIPTOR,
                                                      deactivationStarted,
                                                      continueDeactivation);
        TestActivator pending = TestActivator.init(PENDING_DESCRIPTOR);
        registry.activator(blocker.descriptor(), () -> blocker);
        registry.activator(pending.descriptor(), () -> pending);

        AtomicReference<Throwable> shutdownFailure = new AtomicReference<>();
        Thread shutdownThread = Thread.ofVirtual()
                .name("scoped-registry-shutdown")
                .unstarted(() -> {
                    try {
                        registry.deactivate();
                    } catch (Throwable t) {
                        shutdownFailure.set(t);
                    }
                });

        shutdownThread.start();
        try {
            assertThat("shutdown started", deactivationStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
            assertThat("pending activator phase", pending.phase(), is(ActivationPhase.INIT));
            assertThrows(ScopeNotActiveException.class,
                         () -> registry.activator(pending.descriptor(), () -> pending));
            assertThrows(ScopeNotActiveException.class,
                         () -> registry.activator(blocker.descriptor(), () -> blocker));
            assertThrows(ScopeNotActiveException.class,
                         () -> registry.existingActivator(pending.descriptor()));
            assertThat(registry.existingActivator(blocker.descriptor()).orElseThrow(), sameInstance(blocker));
        } finally {
            continueDeactivation.countDown();
            shutdownThread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        }

        assertThat("shutdown completed", shutdownThread.isAlive(), is(false));
        assertThat("shutdown failure", shutdownFailure.get(), nullValue());
        assertThat(pending.phase(), is(ActivationPhase.DESTROYED));
    }

    private static ScopedRegistryImpl registry() {
        ScopedRegistryImpl registry = new ScopedRegistryImpl(null,
                                                             Service.Singleton.TYPE,
                                                             "test",
                                                             Map.of());
        registry.activate();
        return registry;
    }

    private static final class TestDescriptor implements ServiceDescriptor<Object> {
        private final TypeName typeName;
        private final double runLevel;

        private TestDescriptor(String typeName, double runLevel) {
            this.typeName = TypeName.create(typeName);
            this.runLevel = runLevel;
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
        public Optional<Double> runLevel() {
            return Optional.of(runLevel);
        }
    }

    private static final class TestActivator implements Activator<Object> {
        private final ServiceDescriptor<Object> descriptor;
        private final CountDownLatch deactivationStarted;
        private final CountDownLatch continueDeactivation;

        private volatile ActivationPhase phase;

        private TestActivator(ServiceDescriptor<Object> descriptor,
                              ActivationPhase phase,
                              CountDownLatch deactivationStarted,
                              CountDownLatch continueDeactivation) {
            this.descriptor = descriptor;
            this.phase = phase;
            this.deactivationStarted = deactivationStarted;
            this.continueDeactivation = continueDeactivation;
        }

        static TestActivator init(ServiceDescriptor<Object> descriptor) {
            return new TestActivator(descriptor, ActivationPhase.INIT, null, null);
        }

        static TestActivator active(ServiceDescriptor<Object> descriptor,
                                    CountDownLatch deactivationStarted,
                                    CountDownLatch continueDeactivation) {
            return new TestActivator(descriptor,
                                     ActivationPhase.ACTIVE,
                                     deactivationStarted,
                                     continueDeactivation);
        }

        @Override
        public ServiceDescriptor<Object> descriptor() {
            return descriptor;
        }

        @Override
        public Optional<List<Service.QualifiedInstance<Object>>> instances(Lookup lookup) {
            if (phase != ActivationPhase.DESTROYED) {
                phase = ActivationPhase.ACTIVE;
            }
            return Optional.empty();
        }

        @Override
        public ActivationResult activate(ActivationRequest activationRequest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ActivationResult deactivate() {
            ActivationPhase startingPhase = phase;
            if (deactivationStarted != null) {
                deactivationStarted.countDown();
                try {
                    if (!continueDeactivation.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to continue deactivation");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
            phase = ActivationPhase.DESTROYED;
            return ActivationResult.builder()
                    .success(true)
                    .startingActivationPhase(startingPhase)
                    .targetActivationPhase(ActivationPhase.DESTROYED)
                    .finishingActivationPhase(ActivationPhase.DESTROYED)
                    .build();
        }

        @Override
        public ActivationPhase phase() {
            return phase;
        }

        @Override
        public String description() {
            return descriptor.serviceType().fqName() + ":" + phase;
        }
    }
}
