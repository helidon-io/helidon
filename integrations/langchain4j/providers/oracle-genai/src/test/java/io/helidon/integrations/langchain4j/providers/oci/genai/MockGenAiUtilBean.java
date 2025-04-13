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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.helidon.service.registry.Service;

import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.generativeaiinference.model.AssistantMessage;
import com.oracle.bmc.generativeaiinference.model.ChatChoice;
import com.oracle.bmc.generativeaiinference.model.ChatResult;
import com.oracle.bmc.generativeaiinference.model.CohereChatBotMessage;
import com.oracle.bmc.generativeaiinference.model.CohereChatRequest;
import com.oracle.bmc.generativeaiinference.model.CohereChatResponse;
import com.oracle.bmc.generativeaiinference.model.CohereMessage;
import com.oracle.bmc.generativeaiinference.model.GenericChatRequest;
import com.oracle.bmc.generativeaiinference.model.GenericChatResponse;
import com.oracle.bmc.generativeaiinference.model.Message;
import com.oracle.bmc.generativeaiinference.model.TextContent;
import com.oracle.bmc.generativeaiinference.requests.ChatRequest;
import com.oracle.bmc.generativeaiinference.responses.ChatResponse;
import org.mockito.Mockito;

@Service.Singleton
public class MockGenAiUtilBean {

    private final ConcurrentLinkedQueue<ChatRequest> interceptedRequests = new ConcurrentLinkedQueue<>();

    public MockGenAiUtilBean(GenerativeAiInferenceClient genAiClientMock) {
        Mockito.when(genAiClientMock.chat(Mockito.any())).thenAnswer(invocation -> {
            com.oracle.bmc.generativeaiinference.requests.ChatRequest req = invocation.getArgument(0);
            interceptedRequests.add(req);

            if (req.getChatDetails().getChatRequest() instanceof GenericChatRequest genericChatRequest) {
                List<Message> messages = new ArrayList<>();
                messages.add(AssistantMessage.builder()
                                     .content(List.of(
                                             TextContent
                                                     .builder()
                                                     .text("OK")
                                                     .build()))
                                     .build());
                messages.addAll(genericChatRequest.getMessages());

                return ChatResponse.builder()
                        .chatResult(ChatResult.builder()
                                            .chatResponse(GenericChatResponse.builder()
                                                                  .choices(messages.stream()
                                                                                   .map(m -> ChatChoice
                                                                                           .builder()
                                                                                           .message(m)
                                                                                           .build())
                                                                                   .toList())
                                                                  .build())
                                            .build())
                        .build();
            } else if (req.getChatDetails().getChatRequest() instanceof CohereChatRequest cohereChatRequest) {
                List<CohereMessage> messages = new ArrayList<>();
                messages.add(CohereChatBotMessage.builder()
                                     .message("OK")
                                     .build());
                messages.addAll(cohereChatRequest.getChatHistory());
                return ChatResponse.builder()
                        .chatResult(ChatResult.builder()
                                            .chatResponse(CohereChatResponse.builder()
                                                                  .chatHistory(messages)
                                                                  .text("OK")
                                                                  .build())
                                            .build())
                        .build();
            }

            throw new UnsupportedOperationException("Unsupported chat request type: " + req.getChatDetails().getChatRequest());
        });
    }

    public ConcurrentLinkedQueue<ChatRequest> getInterceptedRequests() {
        return interceptedRequests;
    }
}
