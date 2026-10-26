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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.helidon.common.types.TypeName;

import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Isolated;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

@Isolated("Changes the scoped registry logger level")
class ScopedRegistryImplTest {
    private static final long TIMEOUT_SECONDS = 5;
    private static final ServiceDescriptor<Object> BLOCKING_DESCRIPTOR = new TestDescriptor("Blocking", 1);
    private static final ServiceDescriptor<Object> PENDING_DESCRIPTOR = new TestDescriptor("Pending", 0);

    @TestFactory
    Stream<DynamicContainer> shutdownAtEachLogLevel() {
        return Stream.of(false, true)
                .map(debug -> dynamicContainer("DEBUG=" + debug, Stream.of(
                        dynamicTest("INIT activator becomes terminal",
                                    () -> withDebug(debug, this::handedOutInitActivatorIsTerminalAfterShutdown)),
                        dynamicTest("INIT activator rejects activation during shutdown",
                                    () -> withDebug(debug, this::initActivatorIsUnavailableDuringShutdown)),
                        dynamicTest("shutdown interrupts construction",
                                    () -> withDebug(debug, () -> inProgressInitActivatorIsDestroyedDuringShutdown(
                                            ActivationPhase.CONSTRUCTING))),
                        dynamicTest("shutdown interrupts injection",
                                    () -> withDebug(debug, () -> inProgressInitActivatorIsDestroyedDuringShutdown(
                                            ActivationPhase.INJECTING))),
                        dynamicTest("shutdown interrupts post construction",
                                    () -> withDebug(debug, () -> inProgressInitActivatorIsDestroyedDuringShutdown(
                                            ActivationPhase.POST_CONSTRUCTING))),
                        dynamicTest("completed construction skips pre destroy",
                                    () -> withDebug(debug, this::completedConstructionIsNotPreDestroyedDuringShutdown)),
                        dynamicTest("completed injection skips pre destroy",
                                    () -> withDebug(debug, this::completedInjectionIsNotPreDestroyedDuringShutdown)),
                        dynamicTest("completed post construction runs pre destroy",
                                    () -> withDebug(debug, this::completedPostConstructIsPreDestroyedDuringShutdown)),
                        dynamicTest("shutdown at post construction handoff runs pre destroy",
                                    () -> withDebug(debug, () -> shutdownAtPostConstructHandoff(null))),
                        dynamicTest("shutdown at post construction handoff reports cleanup failure",
                                    () -> withDebug(debug, () -> shutdownAtPostConstructHandoff(
                                            new IllegalStateException("Handoff cleanup failed")))),
                        dynamicTest("queued pre destroy reports failure",
                                    () -> withDebug(debug, this::queuedCleanupFailureIsReported)),
                        dynamicTest("active activator runs pre destroy",
                                    () -> withDebug(debug, this::activeActivatorIsPreDestroyedDuringShutdown)),
                        dynamicTest("cleanup failures aggregate after all services are attempted",
                                    () -> withDebug(debug, this::deactivationFailuresAreAggregated)),
                        dynamicTest("limited activation skips pre destroy",
                                    () -> withDebug(debug, this::limitedActivationSkipsPreDestroy)),
                        dynamicTest("failed initialization skips pre destroy",
                                    () -> withDebug(debug, this::failedInitializationSkipsPreDestroy))
                )));
    }

    private static void withDebug(boolean debug, Executable action) throws Throwable {
        Logger logger = Logger.getLogger(ScopedRegistryImpl.class.getName());
        Level previousLevel = logger.getLevel();
        try {
            logger.setLevel(debug ? Level.FINE : Level.INFO);
            assertThat("actual DEBUG logging", System.getLogger(logger.getName()).isLoggable(System.Logger.Level.DEBUG),
                       is(debug));
            action.execute();
        } finally {
            logger.setLevel(previousLevel);
        }
    }

    private static void completedCallbackIsDeactivatedDuringShutdown(ActivationPhase blockedPhase,
                                                                     int expectedLifecycleInvocations,
                                                                     Throwable cleanupFailure)
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
                                                                                 blockedPhase,
                                                                                 cleanupFailure);
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
        if (cleanupFailure == null) {
            assertThat("shutdown failure", shutdownFailure.get(), nullValue());
        } else {
            assertThat("shutdown failure", shutdownFailure.get(), instanceOf(ServiceRegistryException.class));
            assertThat("suppressed failures", shutdownFailure.get().getSuppressed().length, is(1));
            assertThat("cleanup failure", shutdownFailure.get().getSuppressed()[0], sameInstance(cleanupFailure));
        }
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

    private void handedOutInitActivatorIsTerminalAfterShutdown() {
        ScopedRegistryImpl registry = registry();
        TestActivator pending = TestActivator.init(PENDING_DESCRIPTOR);
        Activator<Object> handedOut = registry.activator(pending.descriptor(), () -> pending);

        registry.deactivate();
        handedOut.instances(Lookup.EMPTY);

        assertThat(pending.phase(), is(ActivationPhase.DESTROYED));
    }

    private void initActivatorIsUnavailableDuringShutdown() throws InterruptedException {
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

    private void inProgressInitActivatorIsDestroyedDuringShutdown(ActivationPhase blockedPhase) throws InterruptedException {
        CountDownLatch activationStarted = new CountDownLatch(1);
        CountDownLatch continueActivation = new CountDownLatch(1);
        CountDownLatch deactivationStarted = new CountDownLatch(1);
        CountDownLatch deactivationCompleted = new CountDownLatch(1);
        ScopedRegistryImpl registry = registry();
        BlockingActivationActivator pending = new BlockingActivationActivator(PENDING_DESCRIPTOR,
                                                                                 activationStarted,
                                                                                 continueActivation,
                                                                                 deactivationStarted,
                                                                                 blockedPhase,
                                                                                 null);
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
            assertThat("activator phase", pending.phase(), is(blockedPhase));
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

    private void completedConstructionIsNotPreDestroyedDuringShutdown() throws InterruptedException {
        completedCallbackIsDeactivatedDuringShutdown(ActivationPhase.CONSTRUCTING, 0, null);
    }

    private void completedInjectionIsNotPreDestroyedDuringShutdown() throws InterruptedException {
        completedCallbackIsDeactivatedDuringShutdown(ActivationPhase.INJECTING, 0, null);
    }

    private void completedPostConstructIsPreDestroyedDuringShutdown() throws InterruptedException {
        completedCallbackIsDeactivatedDuringShutdown(ActivationPhase.POST_CONSTRUCTING, 1, null);
    }

    private void shutdownAtPostConstructHandoff(Throwable cleanupFailure) {
        AtomicBoolean postConstructCompleted = new AtomicBoolean();
        AtomicBoolean shutdownStarted = new AtomicBoolean();
        AtomicReference<Throwable> shutdownFailure = new AtomicReference<>();
        ScopedRegistryImpl registry = new ScopedRegistryImpl(null, Service.Singleton.TYPE, "handoff", Map.of()) {
            @Override
            boolean activationAllowed() {
                // Reenter shutdown at the first eligibility check after the callback returned, before activation
                // has handled interruption. This exposes the handoff without depending on thread scheduling.
                if (postConstructCompleted.get() && shutdownStarted.compareAndSet(false, true)) {
                    try {
                        deactivate();
                    } catch (Throwable t) {
                        shutdownFailure.set(t);
                    }
                }
                return super.activationAllowed();
            }
        };
        registry.activate();
        LifecycleActivator activator = new LifecycleActivator(PENDING_DESCRIPTOR, response -> {
            if (cleanupFailure != null) {
                response.success(false).error(cleanupFailure);
            }
        }) {
            @Override
            void postConstruct(ActivationResult.Builder response) {
                postConstructCompleted.set(true);
            }
        };
        registry.activator(activator.descriptor(), () -> activator);

        ActivationResult result = activator.activate(ActivationRequest.builder().targetPhase(ActivationPhase.ACTIVE).build());

        assertThat("shutdown reached the post construct handoff", shutdownStarted.get(), is(true));
        assertThat("activation interrupted", result.failure(), is(true));
        assertThat("target instances not published", activator.targetInstancesSet(), is(false));
        assertThat("pre destroy invocations", activator.preDestroyInvocations(), is(1));
        assertThat("terminal phase", activator.phase(), is(ActivationPhase.DESTROYED));
        if (cleanupFailure == null) {
            assertThat("shutdown failure", shutdownFailure.get(), nullValue());
        } else {
            assertThat("shutdown failure", shutdownFailure.get(), instanceOf(ServiceRegistryException.class));
            assertThat("suppressed failures", shutdownFailure.get().getSuppressed().length, is(1));
            assertThat("cleanup failure", shutdownFailure.get().getSuppressed()[0], sameInstance(cleanupFailure));
        }
        registry.deactivate();
        assertThat("pre destroy is not repeated", activator.preDestroyInvocations(), is(1));
    }

    private void queuedCleanupFailureIsReported() throws InterruptedException {
        completedCallbackIsDeactivatedDuringShutdown(ActivationPhase.POST_CONSTRUCTING,
                                                    1,
                                                    new IllegalStateException("Queued cleanup failed"));
    }

    private void activeActivatorIsPreDestroyedDuringShutdown() {
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

    private void deactivationFailuresAreAggregated() {
        List<String> cleanupOrder = new ArrayList<>();
        var cause = new IllegalStateException("Returned cleanup failure");
        var thrown = new IllegalArgumentException("Thrown cleanup failure");
        LifecycleActivator withCause = new LifecycleActivator(new TestDescriptor("WithCause", 100), response -> {
            cleanupOrder.add("WithCause");
            response.success(false).error(cause);
        });
        LifecycleActivator withoutCause = new LifecycleActivator(new TestDescriptor("WithoutCause", 0), response -> {
            cleanupOrder.add("WithoutCause");
            response.success(false);
        });
        LifecycleActivator throwing = new LifecycleActivator(new TestDescriptor("Throwing", -100), _ -> {
            cleanupOrder.add("Throwing");
            throw thrown;
        });
        LifecycleActivator remaining = new LifecycleActivator(new TestDescriptor("Remaining", -200), _ -> {
            cleanupOrder.add("Remaining");
        });
        ScopedRegistryImpl registry = registry();
        List<LifecycleActivator> activators = List.of(withCause, withoutCause, throwing, remaining);
        for (LifecycleActivator activator : activators) {
            registry.activator(activator.descriptor(), () -> activator);
            assertThat(activator.activate(ActivationRequest.builder().targetPhase(ActivationPhase.ACTIVE).build()).success(),
                       is(true));
        }
        // Dependencies take precedence over run level, even when a dependent's cleanup fails.
        registry.dependency(withCause.descriptor(), withoutCause.descriptor());

        ServiceRegistryException failure = assertThrows(ServiceRegistryException.class, registry::deactivate);

        assertThat(failure.getMessage(), is("Deactivation failed"));
        assertThat(cleanupOrder, is(List.of("WithoutCause", "WithCause", "Throwing", "Remaining")));
        Throwable[] suppressed = failure.getSuppressed();
        assertThat(suppressed.length, is(3));
        assertThat(suppressed[0], instanceOf(ServiceRegistryException.class));
        assertThat(suppressed[0].getMessage(), containsString("Failed to deactivate WithoutCause"));
        assertThat(suppressed[0].getMessage(), containsString("no exception received"));
        assertThat(suppressed[1], sameInstance(cause));
        assertThat(suppressed[2], instanceOf(ServiceRegistryException.class));
        assertThat(suppressed[2].getMessage(), containsString("Failed to deactivate Throwing"));
        assertThat(suppressed[2].getCause(), sameInstance(thrown));
        assertThrows(ScopeNotActiveException.class,
                     () -> registry.existingActivator(remaining.descriptor()));

        registry.deactivate();
        for (LifecycleActivator activator : activators) {
            assertThat(activator.description() + " cleanup invocations", activator.preDestroyInvocations(), is(1));
        }
    }

    private void limitedActivationSkipsPreDestroy() {
        for (ActivationPhase limit : List.of(ActivationPhase.CONSTRUCTING,
                                             ActivationPhase.INJECTING,
                                             ActivationPhase.POST_CONSTRUCTING)) {
            ScopedRegistryImpl registry = registry();
            LifecycleActivator activator = new LifecycleActivator(PENDING_DESCRIPTOR);
            registry.activator(activator.descriptor(), () -> activator);
            ActivationResult result = activator.activate(ActivationRequest.builder().targetPhase(limit).build());
            assertThat(limit + " activation result", result.success(), is(true));
            assertThat(limit + " phase before shutdown", activator.phase(), is(limit));

            registry.deactivate();
            registry.deactivate();

            assertThat(limit + " cleanup invocations", activator.preDestroyInvocations(), is(0));
            assertThat(limit + " terminal phase", activator.phase(), is(ActivationPhase.DESTROYED));
        }
    }

    private void failedInitializationSkipsPreDestroy() {
        for (ActivationPhase phase : List.of(ActivationPhase.CONSTRUCTING,
                                             ActivationPhase.INJECTING,
                                             ActivationPhase.POST_CONSTRUCTING)) {
            ScopedRegistryImpl registry = registry();
            var cause = new IllegalStateException("Initialization failed during " + phase);
            LifecycleActivator activator = new LifecycleActivator(PENDING_DESCRIPTOR) {
                @Override
                void construct(ActivationResult.Builder response) {
                    if (phase == ActivationPhase.CONSTRUCTING) {
                        throw cause;
                    }
                }

                @Override
                void inject(ActivationResult.Builder response) {
                    if (phase == ActivationPhase.INJECTING) {
                        throw cause;
                    }
                }

                @Override
                void postConstruct(ActivationResult.Builder response) {
                    if (phase == ActivationPhase.POST_CONSTRUCTING) {
                        throw cause;
                    }
                }
            };
            registry.activator(activator.descriptor(), () -> activator);
            assertThat(assertThrows(IllegalStateException.class,
                                    () -> activator.activate(ActivationRequest.builder()
                                                                    .targetPhase(ActivationPhase.ACTIVE)
                                                                    .build())),
                       sameInstance(cause));

            registry.deactivate();

            assertThat(phase + " cleanup invocations", activator.preDestroyInvocations(), is(0));
            assertThat(phase + " terminal phase", activator.phase(), is(ActivationPhase.DESTROYED));
        }
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
        private final Throwable cleanupFailure;
        private int postConstructInvocations;
        private int preDestroyInvocations;
        private boolean targetInstancesSet;

        private BlockingActivationActivator(ServiceDescriptor<Object> descriptor,
                                             CountDownLatch activationStarted,
                                             CountDownLatch continueActivation,
                                             CountDownLatch deactivationStarted,
                                             ActivationPhase blockedPhase,
                                             Throwable cleanupFailure) {
            super(null, null);
            this.descriptor = descriptor;
            this.activationStarted = activationStarted;
            this.continueActivation = continueActivation;
            this.deactivationStarted = deactivationStarted;
            this.blockedPhase = blockedPhase;
            this.cleanupFailure = cleanupFailure;
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
            if (cleanupFailure != null) {
                response.success(false).error(cleanupFailure);
            }
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

    private static class LifecycleActivator extends Activators.BaseActivator<Object> {
        private final ServiceDescriptor<Object> descriptor;
        private final Consumer<ActivationResult.Builder> cleanup;
        private int preDestroyInvocations;
        private boolean targetInstancesSet;

        private LifecycleActivator(ServiceDescriptor<Object> descriptor) {
            this(descriptor, _ -> { });
        }

        private LifecycleActivator(ServiceDescriptor<Object> descriptor, Consumer<ActivationResult.Builder> cleanup) {
            super(null, null);
            this.descriptor = descriptor;
            this.cleanup = cleanup;
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
            cleanup.accept(response);
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
