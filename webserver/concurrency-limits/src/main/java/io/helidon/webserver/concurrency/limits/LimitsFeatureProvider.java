/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.concurrency.limits;

import io.helidon.common.Weight;
import io.helidon.common.config.Config;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation to automatically register this service.
 * <p>
 * The required configuration (disabled by default):
 * <pre>
 * server:
 *   features:
 *     limits:
 *       enabled: true
 *       limit:
 *         bulkhead:
 *         limit: 10
 *         queue: 100
 * </pre>
 */
@Weight(LimitsFeature.WEIGHT)
public class LimitsFeatureProvider implements ServerFeatureProvider<LimitsFeature> {
    /**
     * Public constructor required by {@link java.util.ServiceLoader}.
     */
    public LimitsFeatureProvider() {
    }

    @Override
    public String configKey() {
        return LimitsFeature.ID;
    }

    @Override
    public LimitsFeature create(Config config, String name) {
        return LimitsFeature.builder()
                .config(config)
                .name(name)
                .build();
    }
}
