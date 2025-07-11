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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;

class MockChatModelListener implements ChatModelListener {

    private List<String> messages = new ArrayList<>();

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        messages.add(requestContext.chatRequest()
                             .messages()
                             .stream()
                             .filter(UserMessage.class::isInstance)
                             .map(UserMessage.class::cast)
                             .map(UserMessage::singleText)
                             .collect(Collectors.joining()));
    }

    List<String> messages() {
        return Collections.unmodifiableList(messages);
    }
}
