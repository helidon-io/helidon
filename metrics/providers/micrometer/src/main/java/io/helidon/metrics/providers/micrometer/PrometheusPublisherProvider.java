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

package io.helidon.metrics.providers.micrometer;

import io.helidon.common.config.Config;
import io.helidon.metrics.api.MetricsPublisher;
import io.helidon.metrics.spi.MetricsPublisherProvider;

/**
 * Provider for a Prometheus publisher.
 */
public class PrometheusPublisherProvider implements MetricsPublisherProvider {

    static final String CONFIG_KEY = "micrometer-prometheus";
    static final String TYPE = "micrometer-prometheus";

    /**
     * Creates a new provider for service loading.
     */
    public PrometheusPublisherProvider() {
    }

    @Override
    public String configKey() {
        return CONFIG_KEY;
    }

    @Override
    public MetricsPublisher create(Config config, String name) {

        return PrometheusPublisherConfig.builder()
                .config(config)
                .build();
    }

}
