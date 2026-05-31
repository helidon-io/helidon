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

package io.helidon.openapi.v31;

import io.helidon.common.Api;
import io.helidon.common.Weight;
import io.helidon.config.Config;
import io.helidon.openapi.spi.OpenApiVersion;
import io.helidon.openapi.spi.OpenApiVersionProvider;
import io.helidon.service.registry.Service;

/**
 * OpenAPI 3.1 version provider.
 */
@Service.Singleton
@Weight(3100)
public class OpenApi31VersionProvider implements OpenApiVersionProvider {
    /**
     * Required public constructor.
     */
    @Api.Internal
    public OpenApi31VersionProvider() {
    }

    @Override
    public String configKey() {
        return OpenApi31Version.TYPE;
    }

    @Override
    public OpenApiVersion create(Config config, String name) {
        return OpenApi31VersionConfig.builder()
                .config(config)
                .name(name)
                .build();
    }
}
