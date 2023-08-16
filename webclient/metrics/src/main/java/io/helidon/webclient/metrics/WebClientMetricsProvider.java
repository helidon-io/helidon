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

import io.helidon.common.config.Config;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webclient.spi.WebClientServiceProvider;

/**
 * Client metrics SPI provider implementation.
 *
 * @deprecated This class should only be used via {@link java.util.ServiceLoader}.
 *  Use {@link WebClientMetrics} instead
 */
@Deprecated
public class WebClientMetricsProvider implements WebClientServiceProvider {
    /**
     * Required public constructor.
     *
     * @deprecated This class should only be used via {@link java.util.ServiceLoader}.
     */
    @Deprecated
    public WebClientMetricsProvider() {
    }

    @Override
    public String configKey() {
        return "metrics";
    }

    @Override
    public WebClientService create(Config config, String name) {
        return WebClientMetrics.create(config);
    }

}
