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

package io.helidon.integrations.langchain4j.providers.ollama;

import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import dev.langchain4j.model.ollama.OllamaChatModel;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

@Testing.Test
public class ListenerTest {

    private static final String TEST_PROMPT = "Test prompt";

    @Test
    void requestInterception(OllamaChatModel model) {
        assertThat(model.chat(TEST_PROMPT), is(MockHttpClientProvider.MOCK_RESPONSE));
        var modelListeners = Services.all(MockChatModelListener.class);

        assertThat(modelListeners.size(), is(2));
        assertThat(modelListeners.get(0).messages(), contains(TEST_PROMPT));
        assertThat(modelListeners.get(1).messages(), contains(TEST_PROMPT));
    }

}
