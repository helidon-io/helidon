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

import dev.langchain4j.model.chat.request.ChatRequest;

class MockChatRuleImpl implements MockChatRule {
    private final MockChatRuleConfig config;

    MockChatRuleImpl(MockChatRuleConfig config) {
        this.config = config;
    }

    @Override
    public boolean matches(ChatRequest req) {
        return config.pattern().asMatchPredicate().test(concatMessages(req.messages()));
    }

    @Override
    public MockChatRuleConfig prototype() {
        return config;
    }

    @Override
    public String mock(String concatenatedReq) {
        if (config.response().isPresent()) {
            return config.response().get();
        }
        if (config.template().isPresent()) {
            return concatenatedReq.replaceFirst(config.pattern().pattern(),
                                                config.template().get());
        }
        return MockChatRule.super.mock(concatenatedReq);
    }
}
