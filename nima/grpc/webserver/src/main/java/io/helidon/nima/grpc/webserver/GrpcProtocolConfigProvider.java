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

package io.helidon.nima.grpc.webserver;

import io.helidon.common.config.Config;
import io.helidon.nima.webserver.spi.ProtocolConfigProvider;

/**
 * Implementation of a service provider interface to create grpc protocol configuration.
 */
public class GrpcProtocolConfigProvider implements ProtocolConfigProvider<GrpcConfig> {
    @Override
    public String configKey() {
        return GrpcProtocolProvider.CONFIG_NAME;
    }

    @Override
    public GrpcConfig create(Config config, String name) {
        return GrpcConfig.builder()
                .config(config)
                .name(name)
                .build();
    }
}
