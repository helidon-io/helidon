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

public class MetricsTest {
    private static final String MODEL_NAME = "heli-model";
    private static final String METER_NAME = "gen_ai.client.token.usage";
    private static final Tag TAG_REQ_MODEL = Tag.create("gen_ai_request_model", MODEL_NAME);
    private static final Tag TAG_RES_MODEL = Tag.create("gen_ai_response_model", MODEL_NAME);
    private static final Tag TAG_INPUT = Tag.create("gen_ai_token_type", "input");
    private static final Tag TAG_OUTPUT = Tag.create("gen_ai_token_type", "output");
    private static final List<Tag> INPUT_TAGS = List.of(TAG_REQ_MODEL, TAG_RES_MODEL, TAG_INPUT);
    private static final List<Tag> OUTPUT_TAGS = List.of(TAG_REQ_MODEL, TAG_RES_MODEL, TAG_OUTPUT);
    private static final ChatRequest CHAT_REQ = ChatRequest.builder()
            .modelName(MODEL_NAME)
            .messages(List.of(UserMessage.from("test")))
            .build();

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
        listener.onRequest(new ChatModelRequestContext(CHAT_REQ, null, ctx));
        listener.onResponse(createResponseCtx(111, 333, ctx));

        assertEquals(111, meterRegistry.meter(DistributionSummary.class, METER_NAME, INPUT_TAGS)
                .orElseThrow()
                .totalAmount());
        assertEquals(333, meterRegistry.meter(DistributionSummary.class, METER_NAME, OUTPUT_TAGS)
                .orElseThrow()
                .totalAmount());
    }

    @Test
    void testMetricsListenerNoTokens() {
        MetricsChatModelListener listener = new MetricsChatModelListener();

        Map<Object, Object> ctx = new HashMap<>();
        listener.onRequest(new ChatModelRequestContext(CHAT_REQ, null, ctx));
        listener.onResponse(createResponseCtx(null, null, ctx));

        assertEquals(0, meterRegistry.meter(DistributionSummary.class, METER_NAME, INPUT_TAGS)
                .orElseThrow()
                .totalAmount());
        assertEquals(0, meterRegistry.meter(DistributionSummary.class, METER_NAME, OUTPUT_TAGS)
                .orElseThrow()
                .totalAmount());
    }

    private static ChatModelResponseContext createResponseCtx(Integer inputTokenCount,
                                                              Integer outputTokenCount,
                                                              Map<Object, Object> ctx) {
        return new ChatModelResponseContext(ChatResponse.builder()
                                                    .aiMessage(AiMessage.builder().build())
                                                    .modelName(MODEL_NAME)
                                                    .tokenUsage(new TokenUsage(inputTokenCount, outputTokenCount))
                                                    .build(), CHAT_REQ, null, ctx);
    }
}
