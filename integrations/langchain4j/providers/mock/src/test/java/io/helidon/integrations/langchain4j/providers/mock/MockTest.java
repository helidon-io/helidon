/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.integrations.langchain4j.providers.mock;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.integrations.langchain4j.Ai;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.SystemMessage;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Testing.Test(perMethod = true)
class MockTest {

    @Test
    void defaultMockResponse(HelidonSupportService aiService) {
        assertThat(aiService.chat("How can I create a new Helidon project?"), is("Mock response!"));
    }

    @Test
    void plainTextMockResponse(HelidonSupportService aiService) {
        assertThat(aiService.chat("Helidon is cool!"), is("Cool Hello!"));
    }

    @Test
    void templateMockResponse(HelidonSupportService aiService) {
        assertThat(aiService.chat("Return this message: 'message from request'."), is("The message is: message from request"));
    }

    @Test
    void customMockResponse(HelidonSupportService aiService) {
        assertThat(aiService.chat("Return this message: 'test-message'"), is("The message is: test-message"));
    }

    @Test
    void customRuleAsBeanMockResponse(HelidonSupportService aiService) {
        assertThat(aiService.chat("Custom bean rule match"), is("Custom rule as bean mock response"));
    }

    @Test
    void customManuallyAddedRuleMockResponse() {
        // language=YAML
        var overrideConfig = """
                langchain4j:
                  services:
                   support-service:
                     chat-model: second-mock-model
                """;
        Services.set(Config.class,
                     Config.builder()
                             .addSource(ConfigSources.create(overrideConfig, MediaTypes.APPLICATION_X_YAML))
                             .addSource(ConfigSources.create(Config.create()))
                             .build());

        var aiService = Services.get(HelidonSupportService.class);
        assertThat(aiService.chat("What is your name"), is("second-mock-model"));

        var model = Services.getNamed(MockChatModel.class, "second-mock-model");
        try {
            model.activeRules().clear();
            model.activeRules().add(new MockChatRule() {
                @Override
                public boolean matches(ChatRequest req) {
                    return true;
                }

                @Override
                public String mock(String concatenatedReq) {
                    return "Custom manually added response!";
                }
            });
            assertThat(aiService.chat("Custom rule match"), is("Custom manually added response!"));
        } finally {
            model.resetRules();
        }
    }

    @Ai.Service("support-service")
    @Ai.ChatModel("first-mock-model")
    public interface HelidonSupportService {

        @SystemMessage("You are a Helidon expert!")
        String chat(String prompt);
    }

    @Service.Singleton
    static class CustomGeneralMockChatRule implements MockChatRule {

        @Override
        public boolean matches(ChatRequest req) {
            return req.messages().stream()
                    .filter(m -> ChatMessageType.USER.equals(m.type()))
                    .map(UserMessage.class::cast)
                    .anyMatch(m -> m.singleText().equals("Custom bean rule match"));
        }

        @Override
        public String mock(String unused) {
            return "Custom rule as bean mock response";
        }
    }
}
