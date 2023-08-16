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

package io.helidon.webserver.http1;

import io.helidon.common.config.Config;
import io.helidon.webserver.spi.ProtocolConfigProvider;

/**
 * Implementation of a service provider interface to create HTTP/1.1 protocol configuration.
 */
public class Http1ProtocolConfigProvider implements ProtocolConfigProvider<Http1Config> {
    /**
     * Required default constructor for {@link java.util.ServiceLoader}.
     */
    public Http1ProtocolConfigProvider() {
    }

    @Override
    public String configKey() {
        return Http1ConnectionProvider.CONFIG_NAME;
    }

    @Override
    public Http1Config create(Config config, String name) {
        return Http1Config.builder()
                .config(config)
                .name(name)
                .build();
    }
}
