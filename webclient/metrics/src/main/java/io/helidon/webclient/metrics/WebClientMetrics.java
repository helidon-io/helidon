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

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.webclient.WebClientException;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientServiceRequest;
import io.helidon.webclient.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

/**
 * Container object for all metrics created by the config.
 */
public class WebClientMetrics implements WebClientService {

    static {
        HelidonFeatures.register(HelidonFlavor.SE, "WebClient", "Metrics");
    }

    private final List<WebClientMetric> metrics;

    private WebClientMetrics(Builder builder) {
        metrics = builder.metrics;
    }

    /**
     * Creates new timer client metric.
     *
     * @return client metric builder
     */
    public static WebClientMetric.Builder timer() {
        return WebClientMetric.builder(WebClientMetricType.TIMER);
    }

    /**
     * Creates new counter client metric.
     *
     * @return client metric builder
     */
    public static WebClientMetric.Builder counter() {
        return WebClientMetric.builder(WebClientMetricType.COUNTER);
    }

    /**
     * Creates new meter client metric.
     *
     * @return client metric builder
     */
    public static WebClientMetric.Builder meter() {
        return WebClientMetric.builder(WebClientMetricType.METER);
    }

    /**
     * Creates new gauge in progress client metric.
     *
     * @return client metric builder
     */
    public static WebClientMetric.Builder gaugeInProgress() {
        return WebClientMetric.builder(WebClientMetricType.GAUGE_IN_PROGRESS);
    }

    /**
     * Creates new client metrics based on config.
     *
     * @param config config
     * @return client metrics instance
     */
    public static WebClientMetrics create(Config config) {
        WebClientMetrics.Builder builder = new Builder();
        config.asNodeList().ifPresent(configs -> {
            configs.forEach(metricConfig -> builder.register(processClientMetric(metricConfig)));
        });
        return builder.build();
    }

    private static WebClientMetric processClientMetric(Config metricConfig) {
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
            throw new WebClientException("Metrics type " + type + " is not supported through service loader");
        }
    }

    @Override
    public Single<WebClientServiceRequest> request(WebClientServiceRequest request) {
        metrics.forEach(clientMetric -> clientMetric.request(request));

        return Single.just(request);
    }

    @Override
    public Single<WebClientServiceResponse> response(WebClientRequestBuilder.ClientRequest request,
                                                              WebClientServiceResponse response) {
        metrics.forEach(clientMetric -> clientMetric.response(request, response));

        return Single.just(response);
    }

    private static final class Builder implements io.helidon.common.Builder<WebClientMetrics> {

        private final List<WebClientMetric> metrics = new ArrayList<>();

        private Builder() {
        }

        private Builder register(WebClientMetric clientMetric) {
            metrics.add(clientMetric);
            return this;
        }

        @Override
        public WebClientMetrics build() {
            return new WebClientMetrics(this);
        }
    }
}
