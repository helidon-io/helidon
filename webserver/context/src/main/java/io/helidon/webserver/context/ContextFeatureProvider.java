/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.context;

import io.helidon.common.Weight;
import io.helidon.common.config.Config;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation for context feature for {@link io.helidon.webserver.WebServer}.
 */
@Weight(ContextFeature.WEIGHT)
public class ContextFeatureProvider implements ServerFeatureProvider<ContextFeature> {
    /**
     * Required for {@link java.util.ServiceLoader}.
     *
     * @deprecated only for {@link java.util.ServiceLoader}
     */
    @Deprecated
    public ContextFeatureProvider() {
    }

    @Override
    public String configKey() {
        return ContextFeature.CONTEXT_ID;
    }

    @Override
    public ContextFeature create(Config config, String name) {
        return ContextFeature.builder()
                .config(config)
                .name(name)
                .build();
    }
}
