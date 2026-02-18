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

package io.helidon.integrations.langchain4j.tests.agentic;

import java.util.List;

import io.helidon.config.Config;
import io.helidon.service.registry.Service;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

@Service.Singleton
public class ForbiddenWordsInputGuardrail implements InputGuardrail {

    private final List<String> forbidden;

    @Service.Inject
    ForbiddenWordsInputGuardrail(Config config) {
        this.forbidden = config.get("app.guardrails.forbidden").asList(String.class).orElse(List.of());
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        for (String forbiddenWord : forbidden) {
            if (userMessage.singleText().contains(forbiddenWord)) {
                return this.fatal("Inappropriate Helidon question! Prompt containing: " + forbiddenWord);
            }
        }
        return this.success();
    }
}
