/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.metrics;

import io.helidon.common.config.Config;
import io.helidon.webserver.observe.spi.ObserveProvider;
import io.helidon.webserver.observe.spi.Observer;

/**
 * {@link java.util.ServiceLoader} provider implementation for metrics observe provider.
 *
 * @deprecated only for {@link java.util.ServiceLoader}
 */
@Deprecated
public class MetricsObserveProvider implements ObserveProvider {
    /**
     * Default constructor required by {@link java.util.ServiceLoader}. Do not use.
     *
     * @deprecated only for {@link java.util.ServiceLoader}
     */
    @Deprecated
    public MetricsObserveProvider() {
    }

    @Override
    public String configKey() {
        return "metrics";
    }

    @Override
    public Observer create(Config config, String name) {
        return MetricsObserver.builder()
                .config(config)
                .name(name)
                .build();
    }
}
