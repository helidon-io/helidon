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

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.testing.junit5.Testing;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceAsyncClient;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import dev.langchain4j.community.model.oracle.oci.genai.OciGenAiChatModel;
import dev.langchain4j.community.model.oracle.oci.genai.OciGenAiStreamingChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test(perMethod = true)
class OciGenAiModelFactoryLifecycleTest {
    private final ServiceRegistry testRegistry;

    OciGenAiModelFactoryLifecycleTest(ServiceRegistry testRegistry) {
        this.testRegistry = testRegistry;
    }

    @BeforeEach
    void resetLifecycleTestModel() {
        LifecycleTestModel.reset();
        LifecycleTestModelShutdownObserver.reset();
    }

    @Test
    void cachesServicesAndLeavesSharedRegistryClientOpenOnShutdown(ServiceRegistry registry) {
        var client = registry.get(GenerativeAiInferenceClient.class);
        registry.get(MockGenAiUtilBean.class);
        var factory = chatFactory(twoModelConfig(), registry);

        var first = factory.services();
        var second = factory.services();
        var firstModel = first.getFirst().get();
        var secondModel = first.get(1).get();

        assertThat(first, hasSize(2));
        assertThat(second, sameInstance(first));
        assertThat(second.getFirst().get(), sameInstance(firstModel));
        assertThat(secondModel, not(sameInstance(firstModel)));
        long closesBeforeShutdown = closeInvocationCount(client);

        factory.preDestroy();
        assertThat(factory.services(), is(empty()));
        assertThat(closeInvocationCount(client), is(closesBeforeShutdown));
        assertThat(firstModel.chat("after shutdown"), is("OK"));
        assertThat(secondModel.chat("after shutdown"), is("OK"));

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
        var syncFactory = trackingChatFactory(config, registry);
        var streamingFactory = streamingFactory(config, registry);
        var syncModel = syncFactory.services().getFirst().get();
        long closesBeforeShutdown = closeInvocationCount(asyncClient);

        assertThat(streamingConfig.genAiAsyncClient().orElseThrow(), sameInstance(asyncClient));
        assertThat(streamingConfig.closeModelOnShutdown(), is(false));
        streamingFactory.services().getFirst().get();

        syncFactory.preDestroy();
        Mockito.verify(syncModel, Mockito.never()).close();
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
        var factory = new LifecycleTestModelFactory(twoLifecycleModelConfig(), testRegistry, lifecycle);

        var actual = assertThrows(IllegalArgumentException.class, () -> factory.services().getFirst().get());

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
    void retriesInitializationThroughServiceRegistry() {
        var rolledBackModel = LifecycleTestModel.create();
        var firstModel = LifecycleTestModel.create();
        var secondModel = LifecycleTestModel.create();
        var constructionFailure = new IllegalArgumentException("first construction failed");
        var firstModelAttempt = new AtomicInteger();
        var secondModelAttempt = new AtomicInteger();
        LifecycleTestModel.plan("first-plan", () -> firstModelAttempt.getAndIncrement() == 0
                ? rolledBackModel
                : firstModel);
        LifecycleTestModel.plan("second-plan", () -> {
            if (secondModelAttempt.getAndIncrement() == 0) {
                throw constructionFailure;
            }
            return secondModel;
        });
        var manager = modelRegistry(twoLifecycleModelConfig());

        try {
            var registry = manager.registry();
            assertThat(assertThrows(IllegalArgumentException.class,
                                    () -> registry.getNamed(LifecycleTestModel.class, "first")),
                       sameInstance(constructionFailure));
            assertThat(rolledBackModel.closeCount(), is(1));

            var resolved = registry.getNamed(LifecycleTestModel.class, "first");
            assertThat(resolved, sameInstance(firstModel));
            assertThat(registry.getNamed(LifecycleTestModel.class, "first"), sameInstance(firstModel));
            var allModels = registry.all(LifecycleTestModel.class);
            assertThat(allModels, hasSize(2));
            assertThat(allModels.getFirst(), sameInstance(firstModel));
            assertThat(allModels.get(1), sameInstance(secondModel));
            assertThat(LifecycleTestModel.buildCount(), is(4));
        } finally {
            manager.shutdown();
        }

        assertThat(rolledBackModel.closeCount(), is(1));
        assertThat(firstModel.closeCount(), is(1));
        assertThat(secondModel.closeCount(), is(1));
    }

    @Test
    void generatedFactoryUsesOwningRegistryForDefaultAndNamedServices() {
        var defaultModel = LifecycleTestModel.create();
        var namedModel = LifecycleTestModel.create();
        LifecycleTestModel.plan("default-plan", () -> defaultModel);
        LifecycleTestModel.plan("named-plan", () -> namedModel);
        var previousGlobal = GlobalServiceRegistry.registry();
        var wrongManager = executorOnlyRegistry();
        var isolatedManager = isolatedModelRegistry(registryServiceLifecycleModelConfig());

        try {
            var wrongRegistry = wrongManager.registry();
            GlobalServiceRegistry.registry(wrongRegistry);
            var wrongExecutor = wrongRegistry.getNamed(ExecutorService.class, "test-executor");
            var isolatedRegistry = isolatedManager.registry();
            var isolatedDefaultExecutor = isolatedRegistry.get(ExecutorService.class);
            var isolatedNamedExecutor = isolatedRegistry.getNamed(ExecutorService.class, "test-executor");

            assertThat(GlobalServiceRegistry.registry(), sameInstance(wrongRegistry));
            assertThat(isolatedRegistry, not(sameInstance(wrongRegistry)));
            assertThat(isolatedNamedExecutor, not(sameInstance(wrongExecutor)));
            assertThat(LifecycleTestModelFactory__ServiceDescriptor.INSTANCE.dependencies()
                               .stream()
                               .anyMatch(it -> it.contract().equals(ServiceRegistry.TYPE)),
                       is(true));

            assertThat(isolatedRegistry.getNamed(LifecycleTestModel.class, "default").executorService(),
                       sameInstance(isolatedDefaultExecutor));
            assertThat(isolatedRegistry.getNamed(LifecycleTestModel.class, "named").executorService(),
                       sameInstance(isolatedNamedExecutor));
            assertThat(defaultModel.executorService(), not(sameInstance(wrongExecutor)));
            assertThat(namedModel.executorService(), not(sameInstance(wrongExecutor)));
        } finally {
            GlobalServiceRegistry.registry(previousGlobal);
            isolatedManager.shutdown();
            wrongManager.shutdown();
        }

        assertThat(GlobalServiceRegistry.registry(), sameInstance(previousGlobal));
    }

    @Test
    void resolvesManyNamedModelsByIdentityInRepeatedAndReverseOrder() {
        int modelCount = 32;
        var models = new HashMap<String, LifecycleTestModel>(modelCount);
        for (int i = 0; i < modelCount; i++) {
            var modelName = "model-" + i;
            var model = LifecycleTestModel.create();
            models.put(modelName, model);
            LifecycleTestModel.plan("plan-" + i, () -> model);
        }
        var lifecycle = new LifecycleTestModelFactoryLifecycle();
        var factory = new LifecycleTestModelFactory(manyLifecycleModelConfig(modelCount), testRegistry, lifecycle);
        var references = factory.services();

        assertThat(references, hasSize(modelCount));
        for (int repetition = 0; repetition < 3; repetition++) {
            for (int i = modelCount - 1; i >= 0; i--) {
                var reference = references.get(i);
                assertThat(reference.get(), sameInstance(models.get(modelName(reference))));
            }
        }
        assertThat(LifecycleTestModel.buildCount(), is(modelCount));

        lifecycle.preDestroy();
        models.values().forEach(model -> assertThat(model.closeCount(), is(1)));
    }

    @Test
    void publishesNamedModelsAtomicallyToConcurrentReferences() throws Exception {
        int modelCount = 8;
        var models = new HashMap<String, LifecycleTestModel>(modelCount);
        var finalModelConstructionStarted = new CountDownLatch(1);
        var continueFinalModelConstruction = new CountDownLatch(1);
        for (int i = 0; i < modelCount; i++) {
            var modelName = "model-" + i;
            var model = LifecycleTestModel.create();
            models.put(modelName, model);
            if (i == modelCount - 1) {
                LifecycleTestModel.plan("plan-" + i, () -> {
                    finalModelConstructionStarted.countDown();
                    await(continueFinalModelConstruction);
                    return model;
                });
            } else {
                LifecycleTestModel.plan("plan-" + i, () -> model);
            }
        }
        var lifecycle = new LifecycleTestModelFactoryLifecycle();
        var factory = new LifecycleTestModelFactory(manyLifecycleModelConfig(modelCount), testRegistry, lifecycle);
        var references = factory.services();
        var waitingThread = new AtomicReference<Thread>();
        var waitingReferenceStarted = new CountDownLatch(1);
        var initializingModelReference = references.getFirst();
        var waitingModelReference = references.get(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var initializingReference = executor.submit(initializingModelReference::get);
                assertThat(finalModelConstructionStarted.await(10, TimeUnit.SECONDS), is(true));
                var waitingReference = executor.submit(() -> {
                    waitingThread.set(Thread.currentThread());
                    waitingReferenceStarted.countDown();
                    return waitingModelReference.get();
                });

                assertThat(waitingReferenceStarted.await(10, TimeUnit.SECONDS), is(true));
                assertThat(awaitWaiting(waitingThread.get()), is(true));
                assertThat(waitingReference.isDone(), is(false));
                continueFinalModelConstruction.countDown();

                assertThat(initializingReference.get(10, TimeUnit.SECONDS),
                           sameInstance(models.get(modelName(initializingModelReference))));
                assertThat(waitingReference.get(10, TimeUnit.SECONDS),
                           sameInstance(models.get(modelName(waitingModelReference))));
                assertThat(LifecycleTestModel.buildCount(), is(modelCount));
            } finally {
                continueFinalModelConstruction.countDown();
            }
        }

        lifecycle.preDestroy();
        models.values().forEach(model -> assertThat(model.closeCount(), is(1)));
    }

    @Test
    void retainedReferencesFailAfterShutdown() {
        var firstModel = LifecycleTestModel.create();
        var secondModel = LifecycleTestModel.create();
        LifecycleTestModel.plan("first-plan", () -> firstModel);
        LifecycleTestModel.plan("second-plan", () -> secondModel);
        var lifecycle = new LifecycleTestModelFactoryLifecycle();
        var factory = new LifecycleTestModelFactory(twoLifecycleModelConfig(), testRegistry, lifecycle);
        var references = factory.services();

        assertThat(references.getFirst().get(), sameInstance(firstModel));
        assertThat(references.get(1).get(), sameInstance(secondModel));

        lifecycle.preDestroy();

        references.forEach(reference -> assertThrows(IllegalStateException.class, reference::get));
        assertThat(firstModel.closeCount(), is(1));
        assertThat(secondModel.closeCount(), is(1));
    }

    @Test
    void preservesInterruptionDuringRollbackCleanup() {
        var constructionFailure = new IllegalArgumentException("model construction failed");
        var interruption = new InterruptedException("model cleanup interrupted");
        var model = LifecycleTestModel.create(() -> {
            throw interruption;
        });
        LifecycleTestModel.plan("first-plan", () -> model);
        LifecycleTestModel.plan("second-plan", () -> {
            throw constructionFailure;
        });
        var lifecycle = new LifecycleTestModelFactoryLifecycle();
        var factory = new LifecycleTestModelFactory(twoLifecycleModelConfig(), testRegistry, lifecycle);

        assertThat(Thread.currentThread().isInterrupted(), is(false));
        try {
            var actual = assertThrows(IllegalArgumentException.class,
                                      () -> factory.services().getFirst().get());

            assertThat(actual, sameInstance(constructionFailure));
            assertThat(actual.getSuppressed(), arrayContaining(interruption));
            assertThat(model.closeCount(), is(1));
            assertThat(Thread.currentThread().isInterrupted(), is(true));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void disabledModelIsNotAdvertisedByServiceRegistry() {
        var manager = modelRegistry(disabledLifecycleModelConfig());

        try {
            var registry = manager.registry();
            assertThat(registry.firstNamed(LifecycleTestModel.class, "disabled"), is(Optional.empty()));
            assertThat(registry.all(LifecycleTestModel.class), is(empty()));
            assertThat(LifecycleTestModel.buildCount(), is(0));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void closesRepeatedOwnedModelOnlyOnce() {
        var model = LifecycleTestModel.create();
        LifecycleTestModel.plan("first-plan", () -> model);
        LifecycleTestModel.plan("second-plan", () -> model);
        var lifecycle = new LifecycleTestModelFactoryLifecycle();
        var factory = new LifecycleTestModelFactory(twoLifecycleModelConfig(), testRegistry, lifecycle);

        factory.services().getFirst().get();
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
        var factory = new LifecycleTestModelFactory(twoLifecycleModelConfig(), testRegistry, lifecycle);

        factory.services().getFirst().get();
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
    void preservesInterruptionAndClosesRemainingModelsOnShutdown() {
        var interruption = new InterruptedException("model cleanup interrupted");
        var interruptedModel = LifecycleTestModel.create(() -> {
            throw interruption;
        });
        var closedModel = LifecycleTestModel.create();
        LifecycleTestModel.plan("first-plan", () -> interruptedModel);
        LifecycleTestModel.plan("second-plan", () -> closedModel);
        var lifecycle = new LifecycleTestModelFactoryLifecycle();
        var factory = new LifecycleTestModelFactory(twoLifecycleModelConfig(), testRegistry, lifecycle);
        factory.services().getFirst().get();

        assertThat(Thread.currentThread().isInterrupted(), is(false));
        try {
            var actual = assertThrows(IllegalStateException.class, lifecycle::preDestroy);

            assertThat(actual.getCause(), sameInstance(interruption));
            assertThat(interruptedModel.closeCount(), is(1));
            assertThat(closedModel.closeCount(), is(1));
            assertThat(closedModel.closed(), is(true));
            assertThat(Thread.currentThread().isInterrupted(), is(true));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void rethrowsShutdownErrorWithoutRetryingClose() {
        var cleanupError = new AssertionError("shutdown cleanup failed");
        var model = LifecycleTestModel.create(() -> {
            throw cleanupError;
        });
        LifecycleTestModel.plan("ordered-plan", () -> model);
        var lifecycle = new LifecycleTestModelFactoryLifecycle();
        var factory = new LifecycleTestModelFactory(oneLifecycleModelConfig("ordered-plan"), testRegistry, lifecycle);

        factory.services().getFirst().get();
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
        var factory = new LifecycleTestModelFactory(twoLifecycleModelConfig(), testRegistry, lifecycle);

        factory.services().getFirst().get();
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
        var factory = new LifecycleTestModelFactory(oneLifecycleModelConfig("blocking-plan"),
                                                    testRegistry,
                                                    lifecycle);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                await(start);
                return factory.services().getFirst().get();
            });
            var second = executor.submit(() -> {
                await(start);
                return factory.services().getFirst().get();
            });

            start.countDown();
            assertThat(constructionStarted.await(10, TimeUnit.SECONDS), is(true));

            assertThat(LifecycleTestModel.buildCount(), is(1));
            continueConstruction.countDown();

            var firstModel = first.get(10, TimeUnit.SECONDS);
            var secondModel = second.get(10, TimeUnit.SECONDS);
            assertThat(firstModel, sameInstance(secondModel));
            assertThat(firstModel, sameInstance(model));
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
        var factory = new LifecycleTestModelFactory(oneLifecycleModelConfig("shutdown-race-plan"),
                                                    testRegistry,
                                                    lifecycle);
        var servicesWaiterThread = new AtomicReference<Thread>();
        var servicesWaiterStarted = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(3)) {
            try {
                var services = executor.submit(() -> factory.services().getFirst().get());
                assertThat(constructionStarted.await(10, TimeUnit.SECONDS), is(true));
                var waitingServices = executor.submit(() -> {
                    servicesWaiterThread.set(Thread.currentThread());
                    servicesWaiterStarted.countDown();
                    try {
                        return factory.services().getFirst().get();
                    } finally {
                        continueConstruction.countDown();
                    }
                });

                assertThat(servicesWaiterStarted.await(10, TimeUnit.SECONDS), is(true));
                assertThat(awaitWaiting(servicesWaiterThread.get()), is(true));
                var shutdown = executor.submit(lifecycle::preDestroy);

                var waitingFailure = assertThrows(ExecutionException.class,
                                                  () -> waitingServices.get(10, TimeUnit.SECONDS));
                var servicesFailure = assertThrows(ExecutionException.class,
                                                   () -> services.get(10, TimeUnit.SECONDS));
                assertThat(waitingFailure.getCause(), instanceOf(IllegalStateException.class));
                assertThat(servicesFailure.getCause(), instanceOf(IllegalStateException.class));
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
                   is(Optional.of(-Double.MAX_VALUE)));
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
    void registryShutdownClosesModelAfterRunLevelZeroConsumer() {
        assertEagerConsumerShutdownOrder(shutdownObserverDescriptor(0.0, 100.0));
    }

    @Test
    void registryShutdownClosesModelAfterSameRunLevelHigherWeightConsumer() {
        assertEagerConsumerShutdownOrder(shutdownObserverDescriptor(-Double.MAX_VALUE, 200.0));
    }

    private static long closeInvocationCount(Object client) {
        return Mockito.mockingDetails(client).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("close"))
                .count();
    }

    private static String modelName(Service.QualifiedInstance<?> reference) {
        return reference.qualifiers().iterator().next().value().orElseThrow();
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

    private static Config manyLifecycleModelConfig(int modelCount) {
        var yaml = new StringBuilder("""
                langchain4j:
                  models:
                """);
        for (int i = 0; i < modelCount; i++) {
            yaml.append("""
                    model-%d:
                      provider: lifecycle-test
                      plan: plan-%d
                """.formatted(i, i));
        }
        return Config.just(ConfigSources.create(yaml.toString(), MediaTypes.APPLICATION_X_YAML));
    }

    private static Config registryServiceLifecycleModelConfig() {
        // language=YAML
        var yaml = """
                langchain4j:
                  models:
                    default:
                      provider: lifecycle-test
                      plan: default-plan
                    named:
                      provider: lifecycle-test
                      plan: named-plan
                      executor-service.service-registry.named: test-executor
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

    private static Config disabledLifecycleModelConfig() {
        // language=YAML
        var yaml = """
                langchain4j:
                  models:
                    disabled:
                      provider: lifecycle-test
                      enabled: false
                      plan: unused-plan
                """;
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

    private static OciGenAiChatModelFactory chatFactory(Config config, ServiceRegistry registry) {
        return new OciGenAiChatModelFactory(config, registry, new OciGenAiChatModelFactoryLifecycle());
    }

    private static OciGenAiChatModelFactory trackingChatFactory(Config config, ServiceRegistry registry) {
        return new OciGenAiChatModelFactory(config, registry, new OciGenAiChatModelFactoryLifecycle()) {
            @Override
            protected Optional<OciGenAiChatModel> buildModel(String modelName,
                                                             Config config,
                                                             List<AutoCloseable> ownedModels) {
                int firstNewOwnedModel = ownedModels.size();
                return super.buildModel(modelName, config, ownedModels)
                        .map(model -> {
                            var trackedModel = Mockito.spy(model);
                            for (int i = firstNewOwnedModel; i < ownedModels.size(); i++) {
                                if (ownedModels.get(i) == model) {
                                    ownedModels.set(i, trackedModel);
                                }
                            }
                            return trackedModel;
                        });
            }
        };
    }

    private static OciGenAiStreamingChatModelFactory streamingFactory(Config config, ServiceRegistry registry) {
        return new OciGenAiStreamingChatModelFactory(config,
                                                     registry,
                                                     new OciGenAiStreamingChatModelFactoryLifecycle());
    }

    private static ServiceRegistryManager modelRegistry(Config config) {
        var registryConfig = ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .putContractInstance(Config.class, config)
                .addServiceDescriptor(LifecycleTestModelFactory__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(LifecycleTestModelFactoryLifecycle__ServiceDescriptor.INSTANCE)
                .build();
        return ServiceRegistryManager.start(registryConfig);
    }

    private static ServiceRegistryManager isolatedModelRegistry(Config config) {
        var registryConfig = ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .putContractInstance(Config.class, config)
                .addServiceDescriptor(LifecycleTestModelFactory__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(LifecycleTestModelFactoryLifecycle__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(MockExecutorServiceFactory__ServiceDescriptor.INSTANCE)
                .build();
        return ServiceRegistryManager.create(registryConfig);
    }

    private static ServiceRegistryManager executorOnlyRegistry() {
        var registryConfig = ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .addServiceDescriptor(MockExecutorServiceFactory__ServiceDescriptor.INSTANCE)
                .build();
        return ServiceRegistryManager.create(registryConfig);
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
