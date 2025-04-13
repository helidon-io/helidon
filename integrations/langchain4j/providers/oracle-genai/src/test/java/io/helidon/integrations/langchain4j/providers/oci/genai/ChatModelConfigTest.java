/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
import java.util.Map;
import java.util.Optional;

import io.helidon.common.Errors;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.testing.junit5.Testing;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.model.ChatDetails;
import com.oracle.bmc.generativeaiinference.model.DedicatedServingMode;
import com.oracle.bmc.generativeaiinference.model.GenericChatRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Testing.Test
class ChatModelConfigTest {

    @Test
    void testDefaultRoot(ServiceRegistry registry) {
        BasicAuthenticationDetailsProvider authProv2 = registry.get(Lookup.builder().named("mockNamed2").build());
        assertThat(authProv2.getKeyId(), is("mockNamed2"));
        assertThat(registry.first(BasicAuthenticationDetailsProvider.class).map(BasicAuthenticationDetailsProvider::getKeyId),
                   is(Optional.of("mockDefault")));

        var config = OciGenAiChatModelConfig.builder()
                .serviceRegistry(registry)
                .config(Config.just(ConfigSources.classpath("application.yaml"))
                                .get(OciGenAiChatModelConfig.CONFIG_ROOT))
                .build();

        assertThat(config, is(notNullValue()));
        assertThat(config.temperature(), is(Optional.of(36.6)));
        assertThat(config.topP(), is(Optional.of(10.0)));
        assertThat(config.stop(), hasItems("stop1", "stop2", "stop2"));
        assertThat(config.maxTokens(), is(Optional.of(15)));
        assertThat(config.presencePenalty(), is(Optional.of(0.1)));
        assertThat(config.frequencyPenalty(), is(Optional.of(0.2)));
        assertThat(config.seed(), is(Optional.of(100)));
        assertThat(config.logitBias(), is(Map.of("2255", 1, "365", 2, "test1", 1, "test2", 2)));
        assertThat(config.authProvider().map(BasicAuthenticationDetailsProvider::getKeyId), is(Optional.of("mockDefault")));
        assertThat(config.region(), is(Region.US_ASHBURN_1));

    }

    @Test
    void testNamedAuthProvider(ServiceRegistry registry) {

        var yaml = """
                langchain4j.oci-gen-ai:
                  chat-model:
                    auth-provider.service-registry.named: "mockNamed2"
                    api-key: api-key
                    region: eu-frankfurt-2
                    compartment-id: ""
                    model-name: ""
                """;

        var config = OciGenAiChatModelConfig.builder()
                .serviceRegistry(registry)
                .config(Config.just(ConfigSources.create(yaml, MediaTypes.APPLICATION_X_YAML))
                                .get(OciGenAiChatModelConfig.CONFIG_ROOT))
                .build();

        assertThat(config.authProvider().map(BasicAuthenticationDetailsProvider::getKeyId), is(Optional.of("mockNamed2")));
        assertThat(config.region(), is(Region.EU_FRANKFURT_2));
    }

    @Test
    void testNamedRegion(ServiceRegistry registry) {
        var yaml = """
                langchain4j.oci-gen-ai:
                  chat-model:
                    auth-provider.service-registry.named: "mockNamed2"
                    api-key: api-key
                    region.service-registry.named: "region2"
                    compartment-id: ""
                    model-name: ""
                """;

        var config = OciGenAiChatModelConfig.builder()
                .serviceRegistry(registry)
                .config(Config.just(ConfigSources.create(yaml, MediaTypes.APPLICATION_X_YAML))
                                .get(OciGenAiChatModelConfig.CONFIG_ROOT))
                .build();

        assertThat(config.authProvider().map(BasicAuthenticationDetailsProvider::getKeyId), is(Optional.of("mockNamed2")));
        assertThat(config.region(), is(Region.AP_TOKYO_1));
    }

    @Test
    void testMissingChatModelName(ServiceRegistry registry) {
        var yaml = """
                langchain4j.oci-gen-ai:
                  chat-model:
                    auth-provider.service-registry.named: "mockNamed2"
                    api-key: api-key
                    region.service-registry.named: "region2"
                    compartment-id: ""
                """;

        var exception = Assertions.assertThrows(Errors.ErrorMessagesException.class, () ->
                OciGenAiChatModelConfig.builder()
                        .serviceRegistry(registry)
                        .config(Config.just(ConfigSources.create(yaml, MediaTypes.APPLICATION_X_YAML))
                                        .get(OciGenAiChatModelConfig.CONFIG_ROOT))
                        .build());
        assertThat(exception.getMessage(), containsString("\"model-name\" must not be null"));
    }

    @Test
    void testMissingCompartmentId(ServiceRegistry registry) {
        var yaml = """
                langchain4j.oci-gen-ai:
                  chat-model:
                    auth-provider.service-registry.named: "mockNamed2"
                    api-key: api-key
                    region.service-registry.named: "region2"
                    model-name: ""
                """;

        var exception = Assertions.assertThrows(Errors.ErrorMessagesException.class, () ->
                OciGenAiChatModelConfig.builder()
                        .serviceRegistry(registry)
                        .config(Config.just(ConfigSources.create(yaml, MediaTypes.APPLICATION_X_YAML))
                                        .get(OciGenAiChatModelConfig.CONFIG_ROOT))
                        .build());
        assertThat(exception.getMessage(), containsString("\"compartment-id\" must not be null"));
    }

    @Test
    void testE2E(OciGenAiTestChatService svc, MockGenAiUtilBean mockGenAiUtilBean) {
        svc.chat("test-user-message");

        var interceptedBmcRequest = mockGenAiUtilBean.getInterceptedRequests().poll();
        assertThat(interceptedBmcRequest, is(notNullValue()));
        assertThat(interceptedBmcRequest.getChatDetails(), is(notNullValue()));

        ChatDetails chatDetails = interceptedBmcRequest.getChatDetails();
        GenericChatRequest genericChatRequest = (GenericChatRequest) chatDetails.getChatRequest();

        assertThat(genericChatRequest.getLogitBias(), is(Map.of("2255", 1, "365", 2, "test1", 1, "test2", 2)));
        assertThat(genericChatRequest.getMaxTokens(), is(15));
        assertThat(genericChatRequest.getFrequencyPenalty(), is(0.2));
        assertThat(genericChatRequest.getLogProbs(), is(17));
        assertThat(genericChatRequest.getTemperature(), is(36.6));
        assertThat(genericChatRequest.getSeed(), is(100));
        assertThat(genericChatRequest.getStop(), is(List.of("stop1", "stop2", "stop3")));
        assertThat(genericChatRequest.getTopK(), is(33));
        assertThat(genericChatRequest.getTopP(), is(10.0));
        assertThat(genericChatRequest.getNumGenerations(), is(66));
        assertThat(chatDetails.getServingMode(), instanceOf(DedicatedServingMode.class));
    }
}
