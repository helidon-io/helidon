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

import java.util.concurrent.TimeUnit;

import io.helidon.metrics.api.DistributionStatisticsConfig;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.Tag;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Creates metrics that follow the
 * <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/">Semantic Conventions for GenAI Metrics</a>.
 */
public class MetricsChatModelListener implements ChatModelListener {

    private static final String GEN_AI_CLIENT_OPERATION_START_TIME = "GEN_AI_CLIENT_OPERATION_START_TIME";
    private static final String GEN_AI_CLIENT_TOKEN_USAGE_METRICS_NAME = "gen_ai.client.token.usage";
    private static final String GEN_AI_CLIENT_OPERATION_DURATION_METRICS_NAME = "gen_ai.client.operation.duration";

    private final MeterRegistry meterRegistry;
    private DistributionSummary clientTokenUsage;
    private final DistributionSummary.Builder clientTokenUsageBuilder;
    private DistributionSummary clientOperationDuration;
    private final DistributionSummary.Builder clientOperationDurationBuilder;

    /**
     * Constructs a {@code MetricsChatModelListener} instance.
     */
    public MetricsChatModelListener() {
        System.out.println("*** MetricsChatModelListener ***");
        this.meterRegistry = Metrics.globalRegistry();

        // Limits set based on https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/#metric-gen_aiclienttokenusage.
        DistributionStatisticsConfig.Builder clientTokenUsageStatisticsConfigBuilder = DistributionStatisticsConfig.builder()
                .buckets(1, 4, 16, 64, 256, 1024, 4096, 16384, 65536, 262144, 1048576, 4194304, 16777216, 67108864);
        this.clientTokenUsageBuilder = DistributionSummary.builder(GEN_AI_CLIENT_TOKEN_USAGE_METRICS_NAME,
                                                                   clientTokenUsageStatisticsConfigBuilder)
                .scope(Meter.Scope.VENDOR)
                .baseUnit("token")
                .description("Measures number of input and output tokens used");

        // Limits set based on https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/#metric-gen_aiclientoperationduration.
        DistributionStatisticsConfig.Builder clientOperationDurationStatisticsConfigBuilder =
                DistributionStatisticsConfig.builder()
                        .buckets(0.01, 0.02, 0.04, 0.08, 0.16, 0.32, 0.64, 1.28, 2.56, 5.12, 10.24, 20.48, 40.96, 81.92);
        this.clientOperationDurationBuilder = DistributionSummary.builder(GEN_AI_CLIENT_OPERATION_DURATION_METRICS_NAME,
                                                                          clientOperationDurationStatisticsConfigBuilder)
                .scope(Meter.Scope.VENDOR)
                .baseUnit(Meter.BaseUnits.SECONDS)
                .description("GenAI operation duration");
    }

    @Override
    public void onRequest(ChatModelRequestContext chatModelRequestContext) {
        System.out.println("*** MetricsChatModelListener.onRequest ***");
        chatModelRequestContext.attributes().put(GEN_AI_CLIENT_OPERATION_START_TIME, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext chatModelResponseContext) {
        System.out.println("*** MetricsChatModelListener.onResponse ***");
        final long endTime = System.nanoTime();
        final long startTime = (Long) chatModelResponseContext.attributes().get(GEN_AI_CLIENT_OPERATION_START_TIME);

        final ChatRequest chatRequest = chatModelResponseContext.chatRequest();
        final ChatResponse chatResponse = chatModelResponseContext.chatResponse();

        DistributionSummary.Builder inputClientTokenUsageBuilder = this.clientTokenUsageBuilder
                .addTag(Tag.create("gen_ai.operation.name", "chat"))
                .addTag(Tag.create("gen_ai.request.model", chatRequest.modelName()))
                .addTag(Tag.create("gen_ai.token.type", "input"));
        this.clientTokenUsage = this.meterRegistry.getOrCreate(inputClientTokenUsageBuilder);
        this.clientTokenUsage.record(chatResponse.tokenUsage().inputTokenCount());

        DistributionSummary.Builder outputClientTokenUsageBuilder = this.clientTokenUsageBuilder
                .addTag(Tag.create("gen_ai.operation.name", "chat"))
                .addTag(Tag.create("gen_ai.response.model", chatResponse.modelName()))
                .addTag(Tag.create("gen_ai.token.type", "output"));
        this.clientTokenUsage = this.meterRegistry.getOrCreate(outputClientTokenUsageBuilder);
        this.clientTokenUsage.record(chatResponse.tokenUsage().outputTokenCount());

        DistributionSummary.Builder responseClientOperationDurationBuilder = this.clientOperationDurationBuilder
                .addTag(Tag.create("gen_ai.operation.name", "chat"))
                .addTag(Tag.create("gen_ai.request.model", chatRequest.parameters().modelName()))
                .addTag(Tag.create("gen_ai.response.model", chatResponse.modelName()));
        this.clientOperationDuration = this.meterRegistry.getOrCreate(responseClientOperationDurationBuilder);
        this.clientOperationDuration.record(TimeUnit.SECONDS.convert(endTime - startTime, TimeUnit.NANOSECONDS));
    }

    @Override
    public void onError(ChatModelErrorContext chatModelErrorContext) {
        System.out.println("*** MetricsChatModelListener.onError ***");
        final long endTime = System.nanoTime();
        final long startTime = (Long) chatModelErrorContext.attributes().get(GEN_AI_CLIENT_OPERATION_START_TIME);

        final ChatRequest chatRequest = chatModelErrorContext.chatRequest();

        String sb = chatModelErrorContext.error().getClass().getName()
                + ";" + chatModelErrorContext.error().getLocalizedMessage();

        DistributionSummary.Builder errorClientOperationDurationBuilder = this.clientOperationDurationBuilder
                .addTag(Tag.create("gen_ai.operation.name", "chat"))
                .addTag(Tag.create("gen_ai.request.model", chatRequest.modelName()))
                .addTag(Tag.create("error.type", sb));
        this.clientOperationDuration = this.meterRegistry.getOrCreate(errorClientOperationDurationBuilder);
        this.clientOperationDuration.record(TimeUnit.SECONDS.convert(endTime - startTime, TimeUnit.NANOSECONDS));
    }
}
