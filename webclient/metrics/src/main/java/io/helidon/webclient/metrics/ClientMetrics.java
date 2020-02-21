/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.webclient.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.config.Config;
import io.helidon.webclient.ClientException;
import io.helidon.webclient.ClientRequestBuilder;
import io.helidon.webclient.ClientServiceRequest;
import io.helidon.webclient.ClientServiceResponse;
import io.helidon.webclient.spi.ClientService;

/**
 * Container object for all metrics created by the config.
 */
public class ClientMetrics implements ClientService {

    static {
        HelidonFeatures.register(HelidonFlavor.SE, "WebClient", "Metrics");
    }

    private final List<ClientMetric> metrics;

    private ClientMetrics(Builder builder) {
        metrics = builder.metrics;
    }

    /**
     * Creates new timer client metric.
     *
     * @return client metric builder
     */
    public static ClientMetric.Builder timer() {
        return ClientMetric.builder(ClientMetricType.TIMER);
    }

    /**
     * Creates new counter client metric.
     *
     * @return client metric builder
     */
    public static ClientMetric.Builder counter() {
        return ClientMetric.builder(ClientMetricType.COUNTER);
    }

    /**
     * Creates new meter client metric.
     *
     * @return client metric builder
     */
    public static ClientMetric.Builder meter() {
        return ClientMetric.builder(ClientMetricType.METER);
    }

    /**
     * Creates new gauge in progress client metric.
     *
     * @return client metric builder
     */
    public static ClientMetric.Builder gaugeInProgress() {
        return ClientMetric.builder(ClientMetricType.GAUGE_IN_PROGRESS);
    }

    /**
     * Creates new client metrics based on config.
     *
     * @param config config
     * @return client metrics instance
     */
    public static ClientMetrics create(Config config) {
        ClientMetrics.Builder builder = new Builder();
        config.asNodeList().ifPresent(configs -> {
            configs.forEach(metricConfig -> builder.register(processClientMetric(metricConfig)));
        });
        return builder.build();
    }

    private static ClientMetric processClientMetric(Config metricConfig) {
        String type = metricConfig.get("type").asString().orElse("COUNTER");
        switch (type) {
        case "COUNTER":
            return counter().config(metricConfig).build();
        case "METER":
            return meter().config(metricConfig).build();
        case "TIMER":
            return timer().config(metricConfig).build();
        case "GAUGE_IN_PROGRESS":
            return gaugeInProgress().config(metricConfig).build();
        default:
            throw new ClientException("Metrics type " + type + " is not supported through service loader");
        }
    }

    @Override
    public CompletionStage<ClientServiceRequest> request(ClientServiceRequest request) {
        metrics.forEach(clientMetric -> clientMetric.request(request));

        return CompletableFuture.completedFuture(request);
    }

    @Override
    public CompletionStage<ClientServiceResponse> response(ClientRequestBuilder.ClientRequest request,
                                                           ClientServiceResponse response) {
        metrics.forEach(clientMetric -> clientMetric.response(request, response));

        return CompletableFuture.completedFuture(response);
    }

    private static final class Builder implements io.helidon.common.Builder<ClientMetrics> {

        private final List<ClientMetric> metrics = new ArrayList<>();

        private Builder() {
        }

        private Builder register(ClientMetric clientMetric) {
            metrics.add(clientMetric);
            return this;
        }

        @Override
        public ClientMetrics build() {
            return new ClientMetrics(this);
        }
    }
}
