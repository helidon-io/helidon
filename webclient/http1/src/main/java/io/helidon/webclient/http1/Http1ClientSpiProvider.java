/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.http1;

import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.spi.HttpClientSpi;
import io.helidon.webclient.spi.HttpClientSpiProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation for HTTP/1.1 protocol.
 */
public class Http1ClientSpiProvider implements HttpClientSpiProvider<Http1ClientProtocolConfig> {

    /**
     * Public constructor required by {@link java.util.ServiceLoader}.
     */
    public Http1ClientSpiProvider() {
    }

    @Override
    public String protocolId() {
        return Http1Client.PROTOCOL_ID;
    }

    @Override
    public Class<Http1ClientProtocolConfig> configType() {
        return Http1ClientProtocolConfig.class;
    }

    @Override
    public Http1ClientProtocolConfig defaultConfig() {
        return Http1ClientProtocolConfig.create();
    }

    @Override
    public HttpClientSpi protocol(WebClient client, Http1ClientProtocolConfig config) {
        return new Http1ClientImpl(client,
                                   Http1ClientConfig.builder()
                                           .from(client.prototype())
                                           .protocolConfig(config)
                                           .servicesDiscoverServices(false)
                                           .buildPrototype());
    }
}
