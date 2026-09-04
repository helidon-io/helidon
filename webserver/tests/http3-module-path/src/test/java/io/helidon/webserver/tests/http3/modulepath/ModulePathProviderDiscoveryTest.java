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

package io.helidon.webserver.tests.http3.modulepath;

import io.helidon.webserver.http.spi.SinkProvider;
import io.helidon.webserver.http3.Http3Config;
import io.helidon.webserver.quic.QuicTransportConfig;
import io.helidon.webserver.quic.spi.QuicSubProtocolProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

class ModulePathProviderDiscoveryTest {
    private static final String TEST_MODULE = "io.helidon.webserver.tests.http3.modulepath.test";
    private static final String HTTP3_MODULE = "io.helidon.webserver.http3";
    private static final String HTTP3_PROVIDER = "io.helidon.webserver.http3.Http3QuicProtocolProvider";
    private static final String SSE_MODULE = "io.helidon.webserver.sse";
    private static final String SSE_PROVIDER = "io.helidon.webserver.sse.SseSinkProvider";

    @Test
    void discoversProvidersFromNamedModulesThroughGeneratedPrototypes() {
        Module testModule = getClass().getModule();

        assertThat(testModule.isNamed(), is(true));
        assertThat(testModule.getName(), is(TEST_MODULE));

        Object http3Provider = QuicTransportConfig.create()
                .subProtocolProviders()
                .stream()
                .filter(provider -> provider.getClass().getName().equals(HTTP3_PROVIDER))
                .findFirst()
                .orElseThrow();
        Module http3Module = http3Provider.getClass().getModule();

        assertThat(http3Module.isNamed(), is(true));
        assertThat(http3Module.getName(), is(HTTP3_MODULE));
        assertThat(QuicTransportConfig.class.getModule().getDescriptor().uses(),
                   hasItem(QuicSubProtocolProvider.class.getName()));

        Object sseProvider = Http3Config.create()
                .sinkProviders()
                .stream()
                .filter(provider -> provider.getClass().getName().equals(SSE_PROVIDER))
                .findFirst()
                .orElseThrow();
        Module sseModule = sseProvider.getClass().getModule();

        assertThat(sseModule.isNamed(), is(true));
        assertThat(sseModule.getName(), is(SSE_MODULE));
        assertThat(Http3Config.class.getModule().getDescriptor().uses(),
                   hasItem(SinkProvider.class.getName()));
    }
}
