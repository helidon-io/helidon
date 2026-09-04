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

package io.helidon.webclient.http3;

import io.helidon.common.Api;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.spi.HttpClientSpi;
import io.helidon.webclient.spi.HttpClientSpiProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation for HTTP/3 protocol.
 */
@Api.Internal
public class Http3ClientSpiProvider implements HttpClientSpiProvider<Http3ClientProtocolConfig> {
    /**
     * Default constructor used by {@link java.util.ServiceLoader}.
     */
    @Api.Internal
    public Http3ClientSpiProvider() {
    }

    @Override
    public String protocolId() {
        return Http3Client.PROTOCOL_ID;
    }

    @Override
    public Class<Http3ClientProtocolConfig> configType() {
        return Http3ClientProtocolConfig.class;
    }

    @Override
    public Http3ClientProtocolConfig defaultConfig() {
        return Http3ClientProtocolConfig.create();
    }

    @Override
    public HttpClientSpi protocol(WebClient client, Http3ClientProtocolConfig config) {
        return new Http3ClientImpl(client,
                                   Http3ClientConfig.builder()
                                           .from(client.prototype())
                                           .protocolConfig(config)
                                           .servicesDiscoverServices(false)
                                           .buildPrototype(),
                                   true,
                                   false);
    }
}
