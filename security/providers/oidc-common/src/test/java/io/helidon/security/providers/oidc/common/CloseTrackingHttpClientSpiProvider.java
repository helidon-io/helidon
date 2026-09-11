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

package io.helidon.security.providers.oidc.common;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.spi.HttpClientSpi;
import io.helidon.webclient.spi.HttpClientSpiProvider;
import io.helidon.webclient.spi.ProtocolConfig;

/**
 * Test HTTP client protocol provider that tracks resource closure.
 */
public final class CloseTrackingHttpClientSpiProvider implements HttpClientSpiProvider<ProtocolConfig> {
    static final String PROTOCOL_ID = "close-tracking";

    private static final AtomicInteger CLOSE_COUNT = new AtomicInteger();

    static void reset() {
        CLOSE_COUNT.set(0);
    }

    static int closeCount() {
        return CLOSE_COUNT.get();
    }

    @Override
    public String protocolId() {
        return PROTOCOL_ID;
    }

    @Override
    public Class<ProtocolConfig> configType() {
        return ProtocolConfig.class;
    }

    @Override
    public ProtocolConfig defaultConfig() {
        return new ProtocolConfig() {
            @Override
            public String name() {
                return PROTOCOL_ID;
            }

            @Override
            public String type() {
                return PROTOCOL_ID;
            }
        };
    }

    @Override
    public HttpClientSpi protocol(WebClient client, ProtocolConfig config) {
        return new HttpClientSpi() {
            @Override
            public SupportLevel supports(FullClientRequest<?> clientRequest, ClientUri clientUri) {
                return SupportLevel.NOT_SUPPORTED;
            }

            @Override
            public ClientRequest<?> clientRequest(FullClientRequest<?> clientRequest, ClientUri clientUri) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isTcp() {
                return false;
            }

            @Override
            public void closeResource() {
                CLOSE_COUNT.incrementAndGet();
            }
        };
    }
}
