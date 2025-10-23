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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.helidon.metrics.api.DistributionStatisticsConfig;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.Tag;
import io.helidon.service.registry.Service;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Creates metrics that follow the
 * <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/">Semantic Conventions for GenAI Metrics</a>.
 */
@Service.Singleton
@Service.Named("MetricsChatModelListener")
class MetricsChatModelListener implements ChatModelListener {

    private static final String GEN_AI_CLIENT_OPERATION_START_TIME = "GEN_AI_CLIENT_OPERATION_START_TIME";
    private static final String GEN_AI_CLIENT_TOKEN_USAGE_METRICS_NAME = "gen_ai.client.token.usage";
    private static final String GEN_AI_CLIENT_OPERATION_DURATION_METRICS_NAME = "gen_ai.client.operation.duration";
    private final MeterRegistry meterRegistry;
    private final DistributionStatisticsConfig.Builder clientTokenUsageStatisticsConfigBuilder;
    private final DistributionStatisticsConfig.Builder clientOperationDurationStatisticsConfigBuilder;
    private final Map<String, DistributionSummary> responseInputTokenUsageByModelName = new ConcurrentHashMap<>();
    private final Map<String, DistributionSummary> responseOutputTokenUsageByModelName = new ConcurrentHashMap<>();
    private final Map<String, DistributionSummary> responseOperationDurationByModelName = new ConcurrentHashMap<>();
    private final Map<String, DistributionSummary> errorOperationDurationByModelName = new ConcurrentHashMap<>();

    /**
     * Constructs a {@code MetricsChatModelListener} instance.
     */
    MetricsChatModelListener() {
        this.meterRegistry = Metrics.globalRegistry();
        // Limits set based on https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/#metric-gen_aiclienttokenusage.
        this.clientTokenUsageStatisticsConfigBuilder = DistributionStatisticsConfig.builder()
                .buckets(1, 4, 16, 64, 256, 1024, 4096, 16384, 65536, 262144, 1048576, 4194304, 16777216, 67108864);
        // Limits set based on https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/#metric-gen_aiclientoperationduration.
        this.clientOperationDurationStatisticsConfigBuilder =
                DistributionStatisticsConfig.builder()
                        .buckets(0.01, 0.02, 0.04, 0.08, 0.16, 0.32, 0.64, 1.28, 2.56, 5.12, 10.24, 20.48, 40.96, 81.92);
    }

    @Override
    public void onRequest(ChatModelRequestContext chatModelRequestContext) {
        chatModelRequestContext.attributes().put(GEN_AI_CLIENT_OPERATION_START_TIME, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext chatModelResponseContext) {
        final long endTime = System.nanoTime();
        final long startTime = (Long) chatModelResponseContext.attributes().get(GEN_AI_CLIENT_OPERATION_START_TIME);
        final ChatRequest chatRequest = chatModelResponseContext.chatRequest();
        final ChatResponse chatResponse = chatModelResponseContext.chatResponse();

        var tokenUsage = Optional.of(chatResponse)
                .map(ChatResponse::tokenUsage);

        String requestModelName = chatRequest.modelName();
        String responseModelName = chatResponse.modelName();
        DistributionSummary clientInputTokenUsage = responseInputTokenUsageByModelName.computeIfAbsent(
                requestModelName,
                name -> this.meterRegistry.getOrCreate(DistributionSummary.builder(
                                GEN_AI_CLIENT_TOKEN_USAGE_METRICS_NAME,
                                this.clientTokenUsageStatisticsConfigBuilder)
                                                               .scope(Meter.Scope.VENDOR)
                                                               .baseUnit("token")
                                                               .description(
                                                                       "Measures number of input and output tokens used")
                                                               .addTag(Tag.create(
                                                                       "gen_ai_operation_name",
                                                                       "chat"))
                                                               .addTag(Tag.create(
                                                                       "gen_ai_request_model",
                                                                       requestModelName))
                                                               .addTag(Tag.create(
                                                                       "gen_ai_response_model",
                                                                       responseModelName))
                                                               .addTag(Tag.create(
                                                                       "gen_ai_token_type",
                                                                       "input"))));
        tokenUsage.map(TokenUsage::inputTokenCount)
                .ifPresent(clientInputTokenUsage::record);

        DistributionSummary clientOutputTokenUsage = responseOutputTokenUsageByModelName.computeIfAbsent(
                responseModelName,
                name -> this.meterRegistry.getOrCreate(DistributionSummary.builder(
                                GEN_AI_CLIENT_TOKEN_USAGE_METRICS_NAME,
                                this.clientTokenUsageStatisticsConfigBuilder)
                                                               .scope(Meter.Scope.VENDOR)
                                                               .baseUnit("token")
                                                               .description(
                                                                       "Measures number of input and output tokens used")
                                                               .addTag(Tag.create(
                                                                       "gen_ai_operation_name",
                                                                       "chat"))
                                                               .addTag(Tag.create(
                                                                       "gen_ai_request_model",
                                                                       requestModelName))
                                                               .addTag(Tag.create(
                                                                       "gen_ai_response_model",
                                                                       responseModelName))
                                                               .addTag(Tag.create(
                                                                       "gen_ai_token_type",
                                                                       "output"))));

        tokenUsage.map(TokenUsage::outputTokenCount)
                .ifPresent(clientOutputTokenUsage::record);

        DistributionSummary clientOperationDuration = responseOperationDurationByModelName.computeIfAbsent(
                responseModelName,
                name -> this.meterRegistry.getOrCreate(DistributionSummary.builder(
                                GEN_AI_CLIENT_OPERATION_DURATION_METRICS_NAME,
                                this.clientOperationDurationStatisticsConfigBuilder)
                                                               .scope(Meter.Scope.VENDOR)
                                                               .baseUnit(Meter.BaseUnits.SECONDS)
                                                               .description(
                                                                       "GenAI operation duration")
                                                               .addTag(Tag.create(
                                                                       "gen_ai_operation_name",
                                                                       "chat"))
                                                               .addTag(Tag.create(
                                                                       "gen_ai_request_model",
                                                                       requestModelName))
                                                               .addTag(Tag.create(
                                                                       "gen_ai_response_model",
                                                                       responseModelName))
                                                               .addTag(Tag.create(
                                                                       "error_type",
                                                                       ""))));
        clientOperationDuration.record(TimeUnit.SECONDS.convert(endTime - startTime, TimeUnit.NANOSECONDS));
    }

    @Override
    public void onError(ChatModelErrorContext chatModelErrorContext) {
        final long endTime = System.nanoTime();
        final long startTime = (Long) chatModelErrorContext.attributes().get(GEN_AI_CLIENT_OPERATION_START_TIME);
        final ChatRequest chatRequest = chatModelErrorContext.chatRequest();

        DistributionSummary errorOperationDuration = errorOperationDurationByModelName.computeIfAbsent(
                chatRequest.modelName() + ":" + chatModelErrorContext.error().getClass().getName(),
                name -> this.meterRegistry
                        .getOrCreate(DistributionSummary.builder(
                                        GEN_AI_CLIENT_OPERATION_DURATION_METRICS_NAME,
                                        this.clientOperationDurationStatisticsConfigBuilder)
                                             .scope(Meter.Scope.VENDOR)
                                             .baseUnit(Meter.BaseUnits.SECONDS)
                                             .description(
                                                     "GenAI operation duration")
                                             .addTag(Tag.create(
                                                     "gen_ai_operation_name",
                                                     "chat"))
                                             .addTag(Tag.create(
                                                     "gen_ai_request_model",
                                                     chatRequest.modelName()))
                                             .addTag(Tag.create(
                                                     "gen_ai_response_model",
                                                     ""))
                                             .addTag(Tag.create(
                                                     "error_type",
                                                     chatModelErrorContext.error().getClass().getName()))));
        errorOperationDuration.record(TimeUnit.SECONDS.convert(endTime - startTime, TimeUnit.NANOSECONDS));
    }
}
