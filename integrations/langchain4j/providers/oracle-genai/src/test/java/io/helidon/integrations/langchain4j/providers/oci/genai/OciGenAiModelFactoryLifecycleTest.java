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

package io.helidon.integrations.langchain4j.providers.oci.genai;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.testing.junit5.Testing;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceAsyncClient;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import dev.langchain4j.community.model.oracle.oci.genai.OciGenAiStreamingChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test(perMethod = true)
class OciGenAiModelFactoryLifecycleTest {
    @BeforeEach
    void resetLifecycleTestModel() {
        LifecycleTestModel.reset();
        LifecycleTestModelShutdownObserver.reset();
    }

    @Test
    void cachesServicesAndLeavesSharedRegistryClientOpenOnShutdown(ServiceRegistry registry) {
        var client = registry.get(GenerativeAiInferenceClient.class);
        registry.get(MockGenAiUtilBean.class);
        var factory = chatFactory(twoModelConfig());

        var first = factory.services();
        var second = factory.services();

        assertThat(first, hasSize(2));
        assertThat(second, sameInstance(first));
        assertThat(second.getFirst().get(), sameInstance(first.getFirst().get()));
        assertThat(first.get(1).get(), not(sameInstance(first.getFirst().get())));
        long closesBeforeShutdown = closeInvocationCount(client);

        factory.preDestroy();
        assertThat(factory.services(), is(empty()));
        assertThat(closeInvocationCount(client), is(closesBeforeShutdown));
        assertThat(first.getFirst().get().chat("after shutdown"), is("OK"));
        assertThat(first.get(1).get().chat("after shutdown"), is("OK"));

        factory.preDestroy();
        assertThat(factory.services(), is(empty()));
        assertThat(closeInvocationCount(client), is(closesBeforeShutdown));
    }

    @Test
    void allOciConfigsOptOutOfGeneratedModelShutdown() {
        var authProvider = Mockito.mock(BasicAuthenticationDetailsProvider.class);
        var syncClient = Mockito.mock(GenerativeAiInferenceClient.class);
        var asyncClient = Mockito.mock(GenerativeAiInferenceAsyncClient.class);
        var ownedSyncConfig = OciGenAiChatModelConfig.builder()
                .modelName("model-name")
                .compartmentId("compartment-id")
                .region(Region.US_ASHBURN_1)
                .authProvider(authProvider)
                .genAiClientDiscoverServices(false)
                .build();
        var borrowedSyncConfig = OciGenAiChatModelConfig.builder(ownedSyncConfig)
                .genAiClient(syncClient)
                .build();
        var ownedStreamingConfig = OciGenAiStreamingChatModelConfig.builder()
                .modelName("model-name")
                .compartmentId("compartment-id")
                .region(Region.US_ASHBURN_1)
                .authProvider(authProvider)
                .genAiClientDiscoverServices(false)
                .genAiAsyncClientDiscoverServices(false)
                .build();
        var borrowedStreamingSyncConfig = OciGenAiStreamingChatModelConfig.builder(ownedStreamingConfig)
                .genAiClient(syncClient)
                .build();
        var borrowedStreamingAsyncConfig = OciGenAiStreamingChatModelConfig.builder(ownedStreamingConfig)
                .genAiAsyncClient(asyncClient)
                .build();
        var ownedCohereStreamingConfig = OciGenAiCohereStreamingChatModelConfig.builder()
                .modelName("model-name")
                .compartmentId("compartment-id")
                .region(Region.US_ASHBURN_1)
                .authProvider(authProvider)
                .genAiClientDiscoverServices(false)
                .genAiAsyncClientDiscoverServices(false)
                .build();
        var borrowedCohereStreamingConfig = OciGenAiCohereStreamingChatModelConfig.builder(ownedCohereStreamingConfig)
                .genAiAsyncClient(asyncClient)
                .build();

        assertThat(ownedSyncConfig.closeModelOnShutdown(), is(false));
        assertThat(borrowedSyncConfig.closeModelOnShutdown(), is(false));
        assertThat(ownedStreamingConfig.closeModelOnShutdown(), is(false));
        assertThat(borrowedStreamingSyncConfig.closeModelOnShutdown(), is(false));
        assertThat(borrowedStreamingAsyncConfig.closeModelOnShutdown(), is(false));
        assertThat(ownedCohereStreamingConfig.closeModelOnShutdown(), is(false));
        assertThat(borrowedCohereStreamingConfig.closeModelOnShutdown(), is(false));
    }

    @Test
    void synchronousConfigsDoNotExposeIrrelevantAsyncClient() {
        assertThrows(NoSuchMethodException.class,
                     () -> OciGenAiChatModelConfig.class.getMethod("genAiAsyncClient"));
        assertThrows(NoSuchMethodException.class,
                     () -> OciGenAiCohereChatModelConfig.class.getMethod("genAiAsyncClient"));
    }

    @Test
    void leavesSyncModelAndRegistryAsyncClientOpenForMixedProviderConfig(ServiceRegistry registry) {
        var asyncClient = registry.get(GenerativeAiInferenceAsyncClient.class);
        var config = mixedAuthAndAsyncClientConfig();
        var streamingConfig = OciGenAiStreamingChatModelConfig.builder()
                .serviceRegistry(registry)
                .config(OciGenAiConstants.create(config, OciGenAiStreamingChatModel.class, "mixed"))
                .build();
        var syncFactory = chatFactory(config);
        var streamingFactory = streamingFactory(config);
        assertThat(syncFactory.services(), hasSize(1));
        long closesBeforeShutdown = closeInvocationCount(asyncClient);

        assertThat(streamingConfig.genAiAsyncClient().orElseThrow(), sameInstance(asyncClient));
        assertThat(streamingConfig.closeModelOnShutdown(), is(false));
        assertThat(streamingFactory.services(), hasSize(1));

        syncFactory.preDestroy();
        assertThat(closeInvocationCount(asyncClient), is(closesBeforeShutdown));

        streamingFactory.preDestroy();
        assertThat(closeInvocationCount(asyncClient), is(closesBeforeShutdown));
    }

    @Test
    void retainsFailedRollbackAsTerminalFailure() {
        var constructionFailure = new IllegalArgumentException("model construction failed");
        var cleanupFailure = new IllegalStateException("model cleanup failed");
        var closeAttempt = new AtomicInteger();
        var model = LifecycleTestModel.create(() -> {
            if (closeAttempt.getAndIncrement() == 0) {
                throw cleanupFailure;
            }
        });
        LifecycleTestModel.plan("first-plan", () -> model);
        LifecycleTestModel.plan("second-plan", () -> {
            throw constructionFailure;
        });
        var lifecycle = new LifecycleTestModelFactoryLifecycle();
        var factory = new LifecycleTestModelFactory(twoLifecycleModelConfig(), lifecycle);

        var actual = assertThrows(IllegalArgumentException.class, factory::services);

        assertThat(actual, sameInstance(constructionFailure));
        assertThat(actual.getSuppressed(), arrayContaining(cleanupFailure));
        assertThat(model.closeCount(), is(1));
        assertThat(LifecycleTestModel.buildCount(), is(2));

        var cleanupFailed = assertThrows(IllegalStateException.class, factory::services);
        assertThat(cleanupFailed.getCause(), sameInstance(cleanupFailure));
        assertThat(LifecycleTestModel.buildCount(), is(2));

        var firstShutdown = assertThrows(IllegalStateException.class, lifecycle::preDestroy);
        assertThat(firstShutdown.getMessage(), is("Failed to close LangChain4j model instances."));
        assertThat(firstShutdown.getCause(), sameInstance(cleanupFailure));
        assertThat(model.closeCount(), is(1));

        var repeatedShutdown = assertThrows(IllegalStateException.class, lifecycle::preDestroy);
        assertThat(repeatedShutdown.getMessage(), is("Failed to close LangChain4j model instances."));
        assertThat(repeatedShutdown.getCause(), sameInstance(cleanupFailure));
        assertThat(model.closeCount(), is(1));
    }

    @Test
    void retriesInitializationAfterConstructionFailure() {
        var model = LifecycleTestModel.create();
        var constructionFailure = new IllegalArgumentException("first construction failed");
        var buildAttempt = new AtomicInteger();
        LifecycleTestModel.plan("retry-plan", () -> {
            if (buildAttempt.getAndIncrement() == 0) {
                throw constructionFailure;
            }
            return model;
        });
        var lifecycle = new LifecycleTestModelFactoryLifecycle();
        var factory = new LifecycleTestModelFactory(oneLifecycleModelConfig("retry-plan"), lifecycle);

        assertThat(assertThrows(IllegalArgumentException.class, factory::services), sameInstance(constructionFailure));
        assertThat(factory.services(), hasSize(1));
        assertThat(LifecycleTestModel.buildCount(), is(2));

        lifecycle.preDestroy();
        assertThat(model.closeCount(), is(1));
    }

    @Test
    void closesRepeatedOwnedModelOnlyOnce() {
        var model = LifecycleTestModel.create();
        LifecycleTestModel.plan("first-plan", () -> model);
        LifecycleTestModel.plan("second-plan", () -> model);
        var lifecycle = new LifecycleTestModelFactoryLifecycle();
        var factory = new LifecycleTestModelFactory(twoLifecycleModelConfig(), lifecycle);

        assertThat(factory.services(), hasSize(2));

        lifecycle.preDestroy();
        assertThat(model.closeCount(), is(1));
    }

    @Test
    void doesNotRetryShutdownCloseAfterResourcesWereReleased() {
        var cleanupFailure = new IllegalStateException("shutdown cleanup failed");
        var resourceReleaseCount = new AtomicInteger();
        var closedModel = LifecycleTestModel.create();
        var failedModel = LifecycleTestModel.create(() -> {
            resourceReleaseCount.incrementAndGet();
            throw cleanupFailure;
        });
        LifecycleTestModel.plan("first-plan", () -> closedModel);
        LifecycleTestModel.plan("second-plan", () -> failedModel);
        var lifecycle = new LifecycleTestModelFactoryLifecycle();
        var factory = new LifecycleTestModelFactory(twoLifecycleModelConfig(), lifecycle);

        assertThat(factory.services(), hasSize(2));

        var actual = assertThrows(IllegalStateException.class, lifecycle::preDestroy);
        assertThat(actual.getMessage(), is("Failed to close LangChain4j model instances."));
        assertThat(actual.getCause(), sameInstance(cleanupFailure));
        assertThat(closedModel.closeCount(), is(1));
        assertThat(closedModel.closed(), is(true));
        assertThat(failedModel.closeCount(), is(1));
        assertThat(failedModel.closed(), is(false));
        assertThat(resourceReleaseCount.get(), is(1));

        var cleanupFailed = assertThrows(IllegalStateException.class, factory::services);
        assertThat(cleanupFailed.getCause(), sameInstance(cleanupFailure));

        var repeatedShutdown = assertThrows(IllegalStateException.class, lifecycle::preDestroy);
        assertThat(repeatedShutdown.getMessage(), is("Failed to close LangChain4j model instances."));
        assertThat(repeatedShutdown.getCause(), sameInstance(cleanupFailure));
        assertThat(closedModel.closeCount(), is(1));
        assertThat(failedModel.closeCount(), is(1));
        assertThat(resourceReleaseCount.get(), is(1));
    }

    @Test
    void rethrowsShutdownErrorWithoutRetryingClose() {
        var cleanupError = new AssertionError("shutdown cleanup failed");
        var model = LifecycleTestModel.create(() -> {
            throw cleanupError;
        });
        LifecycleTestModel.plan("ordered-plan", () -> model);
        var lifecycle = new LifecycleTestModelFactoryLifecycle();
        var factory = new LifecycleTestModelFactory(oneLifecycleModelConfig("ordered-plan"), lifecycle);

        assertThat(factory.services(), hasSize(1));

        var first = assertThrows(AssertionError.class, lifecycle::preDestroy);
        assertThat(first, sameInstance(cleanupError));
        assertThat(model.closeCount(), is(1));

        var repeated = assertThrows(AssertionError.class, lifecycle::preDestroy);
        assertThat(repeated, sameInstance(cleanupError));
        assertThat(model.closeCount(), is(1));
    }

    @Test
    void aggregatesShutdownFailuresWithoutRetryingModels() {
        var firstFailure = new IllegalStateException("first cleanup failed");
        var secondFailure = new AssertionError("second cleanup failed");
        var firstModel = LifecycleTestModel.create(() -> {
            throw firstFailure;
        });
        var secondModel = LifecycleTestModel.create(() -> {
            throw secondFailure;
        });
        LifecycleTestModel.plan("first-plan", () -> firstModel);
        LifecycleTestModel.plan("second-plan", () -> secondModel);
        var lifecycle = new LifecycleTestModelFactoryLifecycle();
        var factory = new LifecycleTestModelFactory(twoLifecycleModelConfig(), lifecycle);

        assertThat(factory.services(), hasSize(2));

        var first = assertThrows(AssertionError.class, lifecycle::preDestroy);
        assertThat(first, sameInstance(secondFailure));
        assertThat(first.getSuppressed(), arrayContaining(firstFailure));
        assertThat(firstModel.closeCount(), is(1));
        assertThat(secondModel.closeCount(), is(1));

        var repeated = assertThrows(AssertionError.class, lifecycle::preDestroy);
        assertThat(repeated, sameInstance(secondFailure));
        assertThat(repeated.getSuppressed(), arrayContaining(firstFailure));
        assertThat(firstModel.closeCount(), is(1));
        assertThat(secondModel.closeCount(), is(1));
    }

    @Test
    void initializesOnlyOnceWithExplicitCoordination() throws Exception {
        var model = LifecycleTestModel.create();
        var constructionStarted = new CountDownLatch(1);
        var continueConstruction = new CountDownLatch(1);
        LifecycleTestModel.plan("blocking-plan", () -> {
            constructionStarted.countDown();
            await(continueConstruction);
            return model;
        });
        var lifecycle = new LifecycleTestModelFactoryLifecycle();
        var factory = new LifecycleTestModelFactory(oneLifecycleModelConfig("blocking-plan"), lifecycle);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                await(start);
                return factory.services();
            });
            var second = executor.submit(() -> {
                await(start);
                return factory.services();
            });

            start.countDown();
            assertThat(constructionStarted.await(10, TimeUnit.SECONDS), is(true));

            assertThat(LifecycleTestModel.buildCount(), is(1));
            continueConstruction.countDown();

            var firstServices = first.get(10, TimeUnit.SECONDS);
            var secondServices = second.get(10, TimeUnit.SECONDS);
            assertThat(firstServices, sameInstance(secondServices));
            assertThat(firstServices, hasSize(1));
            assertThat(LifecycleTestModel.buildCount(), is(1));
        }

        lifecycle.preDestroy();
        assertThat(model.closeCount(), is(1));
    }

    @Test
    void shutdownWakesServicesWaiterAndClosesLateModel() throws Exception {
        var model = LifecycleTestModel.create();
        var constructionStarted = new CountDownLatch(1);
        var continueConstruction = new CountDownLatch(1);
        LifecycleTestModel.plan("shutdown-race-plan", () -> {
            constructionStarted.countDown();
            await(continueConstruction);
            return model;
        });
        var lifecycle = new LifecycleTestModelFactoryLifecycle();
        var factory = new LifecycleTestModelFactory(oneLifecycleModelConfig("shutdown-race-plan"), lifecycle);
        var servicesWaiterThread = new AtomicReference<Thread>();
        var servicesWaiterStarted = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(3)) {
            try {
                var services = executor.submit(factory::services);
                assertThat(constructionStarted.await(10, TimeUnit.SECONDS), is(true));
                var waitingServices = executor.submit(() -> {
                    servicesWaiterThread.set(Thread.currentThread());
                    servicesWaiterStarted.countDown();
                    try {
                        return factory.services();
                    } finally {
                        continueConstruction.countDown();
                    }
                });

                assertThat(servicesWaiterStarted.await(10, TimeUnit.SECONDS), is(true));
                assertThat(awaitWaiting(servicesWaiterThread.get()), is(true));
                var shutdown = executor.submit(lifecycle::preDestroy);

                assertThat(waitingServices.get(10, TimeUnit.SECONDS), is(empty()));
                assertThat(services.get(10, TimeUnit.SECONDS), is(empty()));
                shutdown.get(10, TimeUnit.SECONDS);
            } finally {
                continueConstruction.countDown();
            }
        }

        assertThat(factory.services(), is(empty()));
        assertThat(model.closeCount(), is(1));
        lifecycle.preDestroy();
        assertThat(model.closeCount(), is(1));
    }

    @Test
    void lifecycleCoordinatorUsesTerminalShutdownOrder() {
        assertThat(OciGenAiChatModelFactoryLifecycle__ServiceDescriptor.INSTANCE.weight(), is(Double.MAX_VALUE));
        assertThat(OciGenAiChatModelFactoryLifecycle__ServiceDescriptor.INSTANCE.runLevel(),
                   is(Optional.of(Double.MIN_VALUE)));
    }

    @Test
    void registryShutdownClosesModelAfterOrdinaryConsumerWithoutEagerConstruction() {
        var createdModel = new AtomicReference<LifecycleTestModel>();
        LifecycleTestModel.plan("ordered-plan", () -> {
            var model = LifecycleTestModel.create();
            createdModel.set(model);
            return model;
        });
        var manager = lifecycleRegistry(oneLifecycleModelConfig("ordered-plan"));

        try {
            assertThat(LifecycleTestModel.buildCount(), is(0));
            manager.registry().get(LifecycleTestModelShutdownObserver.class);
            assertThat(LifecycleTestModel.buildCount(), is(1));

            manager.shutdown();

            assertThat(LifecycleTestModelShutdownObserver.stoppedWithOpenModel(), is(true));
            assertThat(createdModel.get().closed(), is(true));
            assertThat(createdModel.get().closeCount(), is(1));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void registryShutdownClosesModelAfterRunLevelOneConsumer() {
        assertEagerConsumerShutdownOrder(shutdownObserverDescriptor(1.0, 100.0));
    }

    @Test
    void registryShutdownClosesModelAfterSameRunLevelHigherWeightConsumer() {
        assertEagerConsumerShutdownOrder(shutdownObserverDescriptor(Double.MIN_VALUE, 200.0));
    }

    private static long closeInvocationCount(Object client) {
        return Mockito.mockingDetails(client).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("close"))
                .count();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the test latch.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the test latch.", e);
        }
    }

    private static boolean awaitWaiting(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (thread.getState() == Thread.State.WAITING) {
                return true;
            }
            Thread.onSpinWait();
        }
        return false;
    }

    private static Config twoModelConfig() {
        // language=YAML
        var yaml = """
                langchain4j:
                  models:
                    first:
                      provider: oci-gen-ai
                    second:
                      provider: oci-gen-ai
                  providers:
                    oci-gen-ai:
                      model-name: model-name
                      compartment-id: compartment-id
                      region: us-ashburn-1
                """;
        return Config.just(ConfigSources.create(yaml, MediaTypes.APPLICATION_X_YAML));
    }

    private static Config twoLifecycleModelConfig() {
        // language=YAML
        var yaml = """
                langchain4j:
                  models:
                    first:
                      provider: lifecycle-test
                      plan: first-plan
                    second:
                      provider: lifecycle-test
                      plan: second-plan
                """;
        return Config.just(ConfigSources.create(yaml, MediaTypes.APPLICATION_X_YAML));
    }

    private static Config oneLifecycleModelConfig(String plan) {
        // language=YAML
        var yaml = """
                langchain4j:
                  models:
                    ordered:
                      provider: lifecycle-test
                      plan: %s
                """.formatted(plan);
        return Config.just(ConfigSources.create(yaml, MediaTypes.APPLICATION_X_YAML));
    }

    private static Config mixedAuthAndAsyncClientConfig() {
        // language=YAML
        var yaml = """
                langchain4j:
                  models:
                    mixed:
                      provider: oci-gen-ai
                  providers:
                    oci-gen-ai:
                      model-name: model-name
                      compartment-id: compartment-id
                      region: us-ashburn-1
                      gen-ai-client-discover-services: false
                """;
        return Config.just(ConfigSources.create(yaml, MediaTypes.APPLICATION_X_YAML));
    }

    private static OciGenAiChatModelFactory chatFactory(Config config) {
        return new OciGenAiChatModelFactory(config, new OciGenAiChatModelFactoryLifecycle());
    }

    private static OciGenAiStreamingChatModelFactory streamingFactory(Config config) {
        return new OciGenAiStreamingChatModelFactory(config, new OciGenAiStreamingChatModelFactoryLifecycle());
    }

    private static ServiceRegistryManager lifecycleRegistry(Config config) {
        return lifecycleRegistry(config, LifecycleTestModelShutdownObserver__ServiceDescriptor.INSTANCE);
    }

    private static ServiceRegistryManager lifecycleRegistry(Config config, ServiceDescriptor<?> observerDescriptor) {
        var registryConfig = ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .putContractInstance(Config.class, config)
                .addServiceDescriptor(LifecycleTestModelFactory__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(LifecycleTestModelFactoryLifecycle__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(observerDescriptor)
                .build();
        return ServiceRegistryManager.start(registryConfig);
    }

    private static ServiceDescriptor<?> shutdownObserverDescriptor(double runLevel, double weight) {
        return new LifecycleTestModelShutdownObserver__ServiceDescriptor<LifecycleTestModelShutdownObserver>() {
            @Override
            public double weight() {
                return weight;
            }

            @Override
            public Optional<Double> runLevel() {
                return Optional.of(runLevel);
            }
        };
    }

    private static void assertEagerConsumerShutdownOrder(ServiceDescriptor<?> observerDescriptor) {
        var createdModel = new AtomicReference<LifecycleTestModel>();
        LifecycleTestModel.plan("ordered-plan", () -> {
            var model = LifecycleTestModel.create();
            createdModel.set(model);
            return model;
        });
        var manager = lifecycleRegistry(oneLifecycleModelConfig("ordered-plan"), observerDescriptor);

        try {
            assertThat(LifecycleTestModel.buildCount(), is(1));

            manager.shutdown();

            assertThat(LifecycleTestModelShutdownObserver.stoppedWithOpenModel(), is(true));
            assertThat(createdModel.get().closed(), is(true));
            assertThat(createdModel.get().closeCount(), is(1));
        } finally {
            manager.shutdown();
        }
    }
}
