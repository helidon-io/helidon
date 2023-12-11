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

package io.helidon.webclient.http2;

import io.helidon.common.config.Config;
import io.helidon.webclient.spi.ProtocolConfigProvider;

/**
 * Implementation of protocol config provider.
 */
public class Http2ProtocolConfigProvider implements ProtocolConfigProvider<Http2ClientProtocolConfig> {
    /**
     * Required to be used by {@link java.util.ServiceLoader}.
     * @deprecated do not use directly, use Http1ClientProtocol
     */
    public Http2ProtocolConfigProvider() {
    }

    @Override
    public String configKey() {
        return Http2ProtocolProvider.CONFIG_KEY;
    }

    @Override
    public Http2ClientProtocolConfig create(Config config, String name) {
        return Http2ClientProtocolConfig.builder()
                .config(config)
                .name(name)
                .build();
    }
}
