/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
import java.util.ListIterator;

import io.helidon.common.config.Config;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

/**
 * Container object for all metrics created by the config.
 */
public class WebClientMetrics implements WebClientService {

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
        config.asNodeList().ifPresent(configs ->
                configs.forEach(metricConfig ->
                        builder.register(processClientMetric(metricConfig))));
        return builder.build();
    }

    private static WebClientMetric processClientMetric(Config metricConfig) {
        String type = metricConfig.get("type").asString().orElse("COUNTER");
        return switch (type) {
            case "COUNTER" -> counter().config(metricConfig).build();
            case "METER" -> meter().config(metricConfig).build();
            case "TIMER" -> timer().config(metricConfig).build();
            case "GAUGE_IN_PROGRESS" -> gaugeInProgress().config(metricConfig).build();
            default -> throw new IllegalStateException(String.format(
                    "Metrics type %s is not supported through service loader",
                    type));
        };
    }

    @Override
    public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
        Chain last = chain;
        ListIterator<WebClientMetric> serviceIterator = metrics.listIterator(metrics.size());
        while (serviceIterator.hasPrevious()) {
            Chain next = last;
            WebClientMetric service = serviceIterator.previous();
            last = clientRequest -> service.handle(next, clientRequest);
        }
        return last.proceed(request);
    }

    private static final class Builder implements io.helidon.common.Builder<Builder, WebClientMetrics> {

        private final List<WebClientMetric> metrics = new ArrayList<>();

        private Builder() {
        }

        private void register(WebClientMetric clientMetric) {
            metrics.add(clientMetric);
        }

        @Override
        public WebClientMetrics build() {
            return new WebClientMetrics(this);
        }
    }
}
