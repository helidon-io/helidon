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

package io.helidon.webclient.context;

import io.helidon.common.config.Config;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webclient.spi.WebClientServiceProvider;

/**
 * Client Context Propagation service provider implementation.
 */
public class WebClientContextServiceProvider implements WebClientServiceProvider {
    /**
     * Default constructor required by {@link java.util.ServiceLoader}.
     */
    public WebClientContextServiceProvider() {
    }

    @Override
    public String configKey() {
        return "context";
    }

    @Override
    public WebClientService create(Config config, String name) {
        return WebClientContextService.builder()
                .config(config)
                .name(name)
                .build();
    }
}
