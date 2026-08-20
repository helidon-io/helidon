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

import java.util.List;
import java.util.Optional;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.testing.junit5.Testing;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceAsyncClient;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import dev.langchain4j.community.model.oracle.oci.genai.OciGenAiChatModel;
import dev.langchain4j.community.model.oracle.oci.genai.OciGenAiStreamingChatModel;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testing.Test(perMethod = true)
class OciGenAiModelFactoryLifecycleTest {
    @Test
    void cachesServicesAndLeavesSharedRegistryClientOpenOnShutdown(ServiceRegistry registry) {
        var client = registry.get(GenerativeAiInferenceClient.class);
        registry.get(MockGenAiUtilBean.class);
        var factory = new OciGenAiChatModelFactory(twoModelConfig());

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
    void closesInternallyOwnedModelOnShutdown() {
        var factory = new OciGenAiChatModelFactory(ownedModelConfig());
        var model = factory.services().getFirst().get();

        factory.preDestroy();

        assertThat(factory.services(), is(empty()));
        assertClosed(model);
        factory.preDestroy();
        assertClosed(model);
    }

    @Test
    void preservesOwnershipForMixedAuthenticationAndClientConfigurations() {
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

        assertThat(ownedSyncConfig.closeModelOnShutdown(), is(true));
        assertThat(borrowedSyncConfig.closeModelOnShutdown(), is(false));
        assertThat(ownedStreamingConfig.closeModelOnShutdown(), is(true));
        assertThat(borrowedStreamingSyncConfig.closeModelOnShutdown(), is(false));
        assertThat(borrowedStreamingAsyncConfig.closeModelOnShutdown(), is(false));
        assertThat(ownedCohereStreamingConfig.closeModelOnShutdown(), is(true));
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
    void closesSyncModelButKeepsRegistryAsyncClientOpenForMixedProviderConfig(ServiceRegistry registry) {
        var asyncClient = registry.get(GenerativeAiInferenceAsyncClient.class);
        var config = mixedAuthAndAsyncClientConfig();
        var streamingConfig = OciGenAiStreamingChatModelConfig.builder()
                .serviceRegistry(registry)
                .config(OciGenAiConstants.create(config, OciGenAiStreamingChatModel.class, "mixed"))
                .build();
        var syncFactory = new OciGenAiChatModelFactory(config);
        var streamingFactory = new OciGenAiStreamingChatModelFactory(config);
        var syncModel = syncFactory.services().getFirst().get();
        long closesBeforeShutdown = closeInvocationCount(asyncClient);

        assertThat(streamingConfig.genAiAsyncClient().orElseThrow(), sameInstance(asyncClient));
        assertThat(streamingConfig.closeModelOnShutdown(), is(false));
        assertThat(streamingFactory.services(), hasSize(1));

        syncFactory.preDestroy();
        assertClosed(syncModel);
        assertThat(closeInvocationCount(asyncClient), is(closesBeforeShutdown));

        streamingFactory.preDestroy();
        assertThat(closeInvocationCount(asyncClient), is(closesBeforeShutdown));
    }

    @Test
    void closesEarlierOwnedModelsWhenLaterConstructionFails() {
        var model = Mockito.mock(OciGenAiChatModel.class);
        var constructionFailure = new IllegalArgumentException("model construction failed");
        var cleanupFailure = new IllegalStateException("model cleanup failed");
        doThrow(cleanupFailure).when(model).close();
        var factory = new FailingModelFactory(twoModelConfig(), model, constructionFailure);

        var actual = assertThrows(IllegalArgumentException.class, factory::services);

        assertThat(actual, sameInstance(constructionFailure));
        assertThat(actual.getSuppressed(), arrayContaining(cleanupFailure));
        verify(model, times(1)).close();

        factory.preDestroy();
        verify(model, times(1)).close();
    }

    private static long closeInvocationCount(Object client) {
        return Mockito.mockingDetails(client).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("close"))
                .count();
    }

    private static void assertClosed(OciGenAiChatModel model) {
        var failure = assertThrows(IllegalStateException.class, () -> model.chat("ignored"));
        assertThat(failure.getMessage(), is("OCI GenAI model is closed."));
    }

    private static Config ownedModelConfig() {
        // language=YAML
        var yaml = """
                langchain4j:
                  models:
                    owned:
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

    private static final class FailingModelFactory extends OciGenAiChatModelFactory {
        private final OciGenAiChatModel model;
        private final RuntimeException constructionFailure;
        private int buildCount;

        private FailingModelFactory(Config config, OciGenAiChatModel model, RuntimeException constructionFailure) {
            super(config);
            this.model = model;
            this.constructionFailure = constructionFailure;
        }

        @Override
        protected Optional<OciGenAiChatModel> buildModel(String modelName,
                                                         Config config,
                                                         List<AutoCloseable> ownedModels) {
            if (buildCount++ == 0) {
                ownedModels.add(model);
                return Optional.of(model);
            }
            throw constructionFailure;
        }
    }
}
