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

package io.helidon.integrations.langchain4j.tests.agenticmcp;

import java.util.List;

import io.helidon.integrations.langchain4j.providers.mock.MockChatRule;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;

public class CliToolCallingRule implements MockChatRule {
    @Override
    public boolean matches(ChatRequest req) {
        return true;
    }

    @Override
    public ChatResponse doMock(ChatRequest req) {
        var toolExecutionResultMessages = req.messages().stream()
                .filter(m -> m instanceof ToolExecutionResultMessage)
                .map(m -> (ToolExecutionResultMessage) m)
                .toList();

        if (toolExecutionResultMessages.isEmpty()) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                                       .toolExecutionRequests(List.of(
                                               ToolExecutionRequest.builder()
                                                       .id("001")
                                                       .name("getLatestHelidonVersion")
                                                       .build()
                                       ))
                                       .build())
                    .finishReason(FinishReason.TOOL_EXECUTION)
                    .build();
        }
        if (toolExecutionResultMessages.size() == 1) {
            var version = toolExecutionResultMessages.stream()
                    .filter(m -> m.id().equals("001"))
                    .findFirst()
                    .map(ToolExecutionResultMessage::text)
                    .orElse("2.0.0");
            return ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                                       .toolExecutionRequests(List.of(
                                               ToolExecutionRequest.builder()
                                                       .id("002")
                                                       .name("getInitHelidonSeProjectWithCliCmd")
                                                       .arguments(String.format(
                                                               """
                                                                       {
                                                                       "projectName": "cli-test-project",
                                                                       "version": "%s",
                                                                       "flavor": "MP",
                                                                       "packageName": "io.helidon.cli.test"
                                                                       }
                                                                       """, version))
                                                       .build()
                                       ))
                                       .build())
                    .finishReason(FinishReason.TOOL_EXECUTION)
                    .build();
        }
        if (toolExecutionResultMessages.size() == 2) {

            var cmd = toolExecutionResultMessages.stream()
                    .filter(m -> m.id().equals("002"))
                    .findFirst()
                    .map(ToolExecutionResultMessage::text)
                    .orElse("helidon --help!");

            return ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                                       .text(String.format("""
                                                     Follow these steps:
                                                     
                                                     1. Open your command line interface.
                                                     2. Run the following Helidon CLI command:
                                                     
                                                     ```
                                                     %s
                                                     ```
                                                     
                                                     """, cmd))
                                       .build())
                    .finishReason(FinishReason.STOP)
                    .build();
        }

        return MockChatRule.super.doMock(req);
    }
}
