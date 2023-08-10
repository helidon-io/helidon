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

package io.helidon.webserver.http2;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.webserver.ProtocolConfigs;
import io.helidon.webserver.http2.spi.Http2SubProtocolProvider;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import io.helidon.webserver.spi.ProtocolConfig;
import io.helidon.webserver.spi.ServerConnectionSelector;
import io.helidon.webserver.spi.ServerConnectionSelectorProvider;

/**
 * {@link io.helidon.webserver.spi.ServerConnectionSelectorProvider} implementation for HTTP/2 server connection provider.
 */
public class Http2ConnectionProvider implements ServerConnectionSelectorProvider<Http2Config> {
    /**
     * HTTP/2 server connection provider configuration node name.
     */
    static final String CONFIG_NAME = "http_2";

    private final List<Http2SubProtocolProvider> subProtocolProviders = HelidonServiceLoader.create(
                    ServiceLoader.load(Http2SubProtocolProvider.class))
            .asList();

    /**
     * Creates an instance of HTTP/2 server connection provider.
     *
     * @deprecated to be used solely by {@link java.util.ServiceLoader}
     */
    @Deprecated
    public Http2ConnectionProvider() {
    }

    @Override
    public Class<Http2Config> protocolConfigType() {
        return Http2Config.class;
    }

    @Override
    public String protocolType() {
        return CONFIG_NAME;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public ServerConnectionSelector create(String listenerName, Http2Config config, ProtocolConfigs configs) {

        var subProtocolSelectors = new ArrayList<Http2SubProtocolSelector>();
        for (Http2SubProtocolProvider subProtocolProvider : subProtocolProviders) {
            List<ProtocolConfig> providerConfigs = configs.config(subProtocolProvider.protocolType(),
                                                                  subProtocolProvider.protocolConfigType());
            for (ProtocolConfig providerConfig : providerConfigs) {
                subProtocolSelectors.add(subProtocolProvider.create(providerConfig, configs));
            }
        }

        return new Http2ConnectionSelector(config, subProtocolSelectors);
    }
}
