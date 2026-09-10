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
import static org.hamcrest.CoreMatchers.notNullValue;
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
        LifecycleActivator pending = new LifecycleActivator(PENDING_DESCRIPTOR);
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
            ActivationResult activationResult = pending.activate(ActivationRequest.builder()
                                                                          .targetPhase(ActivationPhase.ACTIVE)
                                                                          .build());
            assertThat("activation interrupted", activationResult.failure(), is(true));
            assertThat("target instances not published", pending.targetInstancesSet(), is(false));
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

    @Test
    void inProgressInitActivatorIsDestroyedDuringShutdown() throws InterruptedException {
        CountDownLatch activationStarted = new CountDownLatch(1);
        CountDownLatch continueActivation = new CountDownLatch(1);
        CountDownLatch deactivationStarted = new CountDownLatch(1);
        CountDownLatch deactivationCompleted = new CountDownLatch(1);
        ScopedRegistryImpl registry = registry();
        BlockingActivationActivator pending = new BlockingActivationActivator(PENDING_DESCRIPTOR,
                                                                                 activationStarted,
                                                                                 continueActivation,
                                                                                 deactivationStarted,
                                                                                 ActivationPhase.POST_CONSTRUCTING);
        registry.activator(pending.descriptor(), () -> pending);

        AtomicReference<ActivationResult> activationResult = new AtomicReference<>();
        AtomicReference<Throwable> activationFailure = new AtomicReference<>();
        Thread activationThread = Thread.ofVirtual()
                .name("service-activation")
                .unstarted(() -> {
                    try {
                        activationResult.set(pending.activate(ActivationRequest.builder()
                                                                        .targetPhase(ActivationPhase.ACTIVE)
                                                                        .build()));
                    } catch (Throwable t) {
                        activationFailure.set(t);
                    }
                });
        AtomicReference<Throwable> shutdownFailure = new AtomicReference<>();
        Thread shutdownThread = Thread.ofVirtual()
                .name("scoped-registry-shutdown")
                .unstarted(() -> {
                    try {
                        registry.deactivate();
                    } catch (Throwable t) {
                        shutdownFailure.set(t);
                    } finally {
                        deactivationCompleted.countDown();
                    }
                });

        activationThread.start();
        try {
            assertThat("activation started", activationStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
            assertThat("activator phase", pending.phase(), is(ActivationPhase.POST_CONSTRUCTING));
            shutdownThread.start();
            assertThat("deactivation started", deactivationStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
            assertThat("shutdown completed during activation",
                       deactivationCompleted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                       is(true));
            assertThat("activator phase during shutdown", pending.phase(), is(ActivationPhase.DESTROYED));
        } finally {
            continueActivation.countDown();
            activationThread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            shutdownThread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        }

        assertThat("activation completed", activationThread.isAlive(), is(false));
        assertThat("shutdown completed", shutdownThread.isAlive(), is(false));
        assertThat("activation failure", activationFailure.get(), nullValue());
        assertThat("activation result", activationResult.get(), notNullValue());
        assertThat("activation interrupted", activationResult.get().failure(), is(true));
        assertThat("shutdown failure", shutdownFailure.get(), nullValue());
        assertThat("target instances not published", pending.targetInstancesSet(), is(false));
        assertThat("pre destroy skipped for interrupted callback", pending.preDestroyInvocations(), is(0));
        assertThat(pending.phase(), is(ActivationPhase.DESTROYED));
    }

    @Test
    void completedConstructionIsNotPreDestroyedDuringShutdown() throws InterruptedException {
        completedCallbackIsDeactivatedDuringShutdown(ActivationPhase.CONSTRUCTING, 0);
    }

    @Test
    void completedInjectionIsNotPreDestroyedDuringShutdown() throws InterruptedException {
        completedCallbackIsDeactivatedDuringShutdown(ActivationPhase.INJECTING, 0);
    }

    @Test
    void completedPostConstructIsPreDestroyedDuringShutdown() throws InterruptedException {
        completedCallbackIsDeactivatedDuringShutdown(ActivationPhase.POST_CONSTRUCTING, 1);
    }

    @Test
    void activeActivatorIsPreDestroyedDuringShutdown() {
        ScopedRegistryImpl registry = registry();
        LifecycleActivator activator = new LifecycleActivator(PENDING_DESCRIPTOR);
        registry.activator(activator.descriptor(), () -> activator);
        ActivationResult activationResult = activator.activate(ActivationRequest.builder()
                                                                       .targetPhase(ActivationPhase.ACTIVE)
                                                                       .build());

        registry.deactivate();

        assertThat("activation succeeded", activationResult.failure(), is(false));
        assertThat("pre destroy invocations", activator.preDestroyInvocations(), is(1));
        assertThat(activator.phase(), is(ActivationPhase.DESTROYED));
    }

    private static void completedCallbackIsDeactivatedDuringShutdown(ActivationPhase blockedPhase,
                                                                     int expectedLifecycleInvocations)
            throws InterruptedException {
        CountDownLatch activationStarted = new CountDownLatch(1);
        CountDownLatch continueActivation = new CountDownLatch(1);
        CountDownLatch deactivationStarted = new CountDownLatch(1);
        CountDownLatch continueDeactivation = new CountDownLatch(1);
        CountDownLatch pendingDeactivationStarted = new CountDownLatch(1);
        ScopedRegistryImpl registry = registry();
        TestActivator blocker = TestActivator.active(BLOCKING_DESCRIPTOR,
                                                      deactivationStarted,
                                                      continueDeactivation);
        BlockingActivationActivator pending = new BlockingActivationActivator(PENDING_DESCRIPTOR,
                                                                                 activationStarted,
                                                                                 continueActivation,
                                                                                 pendingDeactivationStarted,
                                                                                 blockedPhase);
        registry.activator(blocker.descriptor(), () -> blocker);
        registry.activator(pending.descriptor(), () -> pending);

        AtomicReference<ActivationResult> activationResult = new AtomicReference<>();
        AtomicReference<Throwable> activationFailure = new AtomicReference<>();
        Thread activationThread = Thread.ofVirtual()
                .name("service-activation")
                .unstarted(() -> {
                    try {
                        activationResult.set(pending.activate(ActivationRequest.builder()
                                                                        .targetPhase(ActivationPhase.ACTIVE)
                                                                        .build()));
                    } catch (Throwable t) {
                        activationFailure.set(t);
                    }
                });
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

        activationThread.start();
        try {
            assertThat("activation started", activationStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
            assertThat("blocked callback phase", pending.phase(), is(blockedPhase));
            shutdownThread.start();
            assertThat("higher run-level deactivation started",
                       deactivationStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                       is(true));
            continueActivation.countDown();
            activationThread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            assertThat("activation completed before pending deactivation", activationThread.isAlive(), is(false));
            assertThat("pending deactivation not started", pendingDeactivationStarted.getCount(), is(1L));
            assertThat("pre destroy waits for pending deactivation", pending.preDestroyInvocations(), is(0));
        } finally {
            continueActivation.countDown();
            continueDeactivation.countDown();
            activationThread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            shutdownThread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        }

        assertThat("activation completed", activationThread.isAlive(), is(false));
        assertThat("shutdown completed", shutdownThread.isAlive(), is(false));
        assertThat("activation failure", activationFailure.get(), nullValue());
        assertThat("activation result", activationResult.get(), notNullValue());
        assertThat("activation interrupted", activationResult.get().failure(), is(true));
        assertThat("shutdown failure", shutdownFailure.get(), nullValue());
        assertThat("target instances not published", pending.targetInstancesSet(), is(false));
        assertThat("pending deactivation started", pendingDeactivationStarted.getCount(), is(0L));
        assertThat("post construct invocations", pending.postConstructInvocations(), is(expectedLifecycleInvocations));
        assertThat("pre destroy invocations", pending.preDestroyInvocations(), is(expectedLifecycleInvocations));
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

    private static final class BlockingActivationActivator extends Activators.BaseActivator<Object> {
        private final ServiceDescriptor<Object> descriptor;
        private final CountDownLatch activationStarted;
        private final CountDownLatch continueActivation;
        private final CountDownLatch deactivationStarted;
        private final ActivationPhase blockedPhase;
        private int postConstructInvocations;
        private int preDestroyInvocations;
        private boolean targetInstancesSet;

        private BlockingActivationActivator(ServiceDescriptor<Object> descriptor,
                                             CountDownLatch activationStarted,
                                             CountDownLatch continueActivation,
                                             CountDownLatch deactivationStarted,
                                             ActivationPhase blockedPhase) {
            super(null, null);
            this.descriptor = descriptor;
            this.activationStarted = activationStarted;
            this.continueActivation = continueActivation;
            this.deactivationStarted = deactivationStarted;
            this.blockedPhase = blockedPhase;
        }

        @Override
        public ServiceDescriptor<Object> descriptor() {
            return descriptor;
        }

        @Override
        public ActivationResult deactivate() {
            deactivationStarted.countDown();
            return super.deactivate();
        }

        @Override
        public String description() {
            return descriptor.serviceType().fqName() + ":" + phase();
        }

        @Override
        void construct(ActivationResult.Builder response) {
            awaitCallback(ActivationPhase.CONSTRUCTING);
        }

        @Override
        void inject(ActivationResult.Builder response) {
            awaitCallback(ActivationPhase.INJECTING);
        }

        @Override
        void postConstruct(ActivationResult.Builder response) {
            awaitCallback(ActivationPhase.POST_CONSTRUCTING);
            postConstructInvocations++;
        }

        @Override
        void preDestroy(ActivationResult.Builder response) {
            preDestroyInvocations++;
        }

        @Override
        void setTargetInstances() {
            targetInstancesSet = true;
        }

        private int preDestroyInvocations() {
            return preDestroyInvocations;
        }

        private boolean targetInstancesSet() {
            return targetInstancesSet;
        }

        private int postConstructInvocations() {
            return postConstructInvocations;
        }

        private void awaitCallback(ActivationPhase phase) {
            if (phase != blockedPhase) {
                return;
            }
            activationStarted.countDown();
            try {
                if (!continueActivation.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting to continue activation");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    private static final class LifecycleActivator extends Activators.BaseActivator<Object> {
        private final ServiceDescriptor<Object> descriptor;
        private int preDestroyInvocations;
        private boolean targetInstancesSet;

        private LifecycleActivator(ServiceDescriptor<Object> descriptor) {
            super(null, null);
            this.descriptor = descriptor;
        }

        @Override
        public ServiceDescriptor<Object> descriptor() {
            return descriptor;
        }

        @Override
        public String description() {
            return descriptor.serviceType().fqName() + ":" + phase();
        }

        @Override
        void preDestroy(ActivationResult.Builder response) {
            preDestroyInvocations++;
        }

        @Override
        void setTargetInstances() {
            targetInstancesSet = true;
        }

        private int preDestroyInvocations() {
            return preDestroyInvocations;
        }

        private boolean targetInstancesSet() {
            return targetInstancesSet;
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
