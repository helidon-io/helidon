/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.grpc;

import java.util.Map;

import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.MergedConfig;
import io.helidon.webserver.spi.ProtocolConfigProvider;

/**
 * Implementation of a service provider interface to create grpc protocol configuration.
 */
public class GrpcProtocolConfigProvider implements ProtocolConfigProvider<GrpcConfig> {
    private static final Config DISCOVERY_DISABLED_CONFIG = Config.just(ConfigSources.create(
            Map.of("grpc-services-discover-services", "false")));

    /**
     * Required public constructor for {@link java.util.ServiceLoader}.
     */
    @Api.Internal
    public GrpcProtocolConfigProvider() {
    }

    @Override
    public String configKey() {
        return GrpcProtocolProvider.CONFIG_NAME;
    }

    @Override
    public GrpcConfig create(Config config, String name) {
        return GrpcConfig.builder()
                .config(MergedConfig.create(DISCOVERY_DISABLED_CONFIG, config))
                .name(name)
                .build();
    }
}
