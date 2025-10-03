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

package io.helidon.integrations.langchain4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.Tag;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MetricsTest {
    private final MeterRegistry meterRegistry = Metrics.globalRegistry();

    @BeforeEach
    void clearAllRegistry() {
        List<Meter> meters = meterRegistry.meters();
        meters.forEach(meterRegistry::remove);
    }

    @Test
    void testMetricsListener() {
        MetricsChatModelListener listener = new MetricsChatModelListener();

        Map<Object, Object> ctx = new HashMap<>();
        listener.onRequest(new ChatModelRequestContext(
                ChatRequest.builder()
                        .messages(List.of(UserMessage.from("test"))).build(),
                null,
                ctx));

        listener.onResponse(new ChatModelResponseContext(
                ChatResponse.builder()
                        .aiMessage(AiMessage.builder().build())
                        .modelName("test")
                        .tokenUsage(new TokenUsage(111, 333))
                        .build(),
                ChatRequest.builder().modelName("test")
                        .messages(UserMessage.from("test")).build(),
                null, ctx));

        DistributionSummary promptCounter = (DistributionSummary) meterRegistry
                .meters()
                .stream()
                .filter(meter -> meter.id().name().equals("gen_ai.client.token.usage"))
                .findFirst()
                .orElseThrow();

        assertNotNull(promptCounter);
        assertEquals(111,
                     meterRegistry.meter(DistributionSummary.class,
                                         "gen_ai.client.token.usage",
                                         List.of(Tag.create("gen_ai_token_type", "input"))).orElseThrow().totalAmount());
        assertEquals(333,
                     meterRegistry.meter(DistributionSummary.class,
                                         "gen_ai.client.token.usage",
                                         List.of(Tag.create("gen_ai_token_type", "output"))).orElseThrow().totalAmount());
    }

    @Test
    void testMetricsListenerNoTokens() {
        MetricsChatModelListener listener = new MetricsChatModelListener();

        Map<Object, Object> ctx = new HashMap<>();
        listener.onRequest(new ChatModelRequestContext(
                ChatRequest.builder()
                        .messages(List.of(UserMessage.from("test"))).build(),
                null,
                ctx));

        listener.onResponse(new ChatModelResponseContext(
                ChatResponse.builder()
                        .aiMessage(AiMessage.builder().build())
                        .modelName("test")
                        .tokenUsage(new TokenUsage(null, null))
                        .build(),
                ChatRequest.builder().modelName("test")
                        .messages(UserMessage.from("test")).build(),
                null, ctx));

        DistributionSummary promptCounter = (DistributionSummary) meterRegistry
                .meters()
                .stream()
                .filter(meter -> meter.id().name().equals("gen_ai.client.token.usage"))
                .findFirst()
                .orElseThrow();

        assertNotNull(promptCounter);
        assertEquals(0,
                     meterRegistry.meter(DistributionSummary.class,
                                         "gen_ai.client.token.usage",
                                         List.of(Tag.create("gen_ai_token_type", "input"))).orElseThrow().totalAmount());
        assertEquals(0,
                     meterRegistry.meter(DistributionSummary.class,
                                         "gen_ai.client.token.usage",
                                         List.of(Tag.create("gen_ai_token_type", "output"))).orElseThrow().totalAmount());
    }
}
