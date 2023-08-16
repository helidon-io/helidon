/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.http.http2.Http2Setting;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectionConfigTest {

    // Verify that HTTP/2 connection provider is properly configured from config file
    @Test
    void testConnectionConfig() {
        TestConnectionSelectorProvider.reset();
        // This will pick up application.yaml from the classpath as default configuration file
        WebServer.builder().config(Config.create().get("server")).build();

        Map<String, Http2Config> http2Configs = TestConnectionSelectorProvider.config();
        assertThat(http2Configs, hasKey("@default"));

        Http2Config http2Config = http2Configs.get("@default");

        assertAll(
                () -> assertThat("maxFrameSize", http2Config.maxFrameSize(), is(8192)),
                () -> assertThat("maxHeaderListSize", http2Config.maxHeaderListSize(), is(4096L)),
                () -> assertThat("maxConcurrentStreams", http2Config.maxConcurrentStreams(), is(16384L)),
                () -> assertThat("validatePath", http2Config.validatePath(), is(false)),
                () -> assertThat("initialWindowSize", http2Config.initialWindowSize(), is(8192)),
                () -> assertThat("flowControlTimeout", http2Config.flowControlTimeout(), is(Duration.ofMillis(700)))
        );
    }

    // Verify that HTTP/2 connection provider is properly configured from builder
    @Test
    void testProviderConfigBuilder() {

        Http2ConnectionSelector selector = Http2ConnectionSelector.builder()
                .http2Config(Http2Config.builder()
                                     .name("@default")
                                     .maxFrameSize(4096)
                                     .maxHeaderListSize(2048L)
                                     .build())
                .build();

        Http2Connection conn = (Http2Connection) selector.connection(mockContext());
        // Verify values to be updated from configuration file
        assertThat(conn.config().maxFrameSize(), is(4096));
        assertThat(conn.config().maxHeaderListSize(), is(2048L));
        // Verify Http2Settings values to be updated from configuration file
        assertThat(conn.serverSettings().value(Http2Setting.MAX_FRAME_SIZE), is(4096L));
        assertThat(conn.serverSettings().value(Http2Setting.MAX_HEADER_LIST_SIZE), is(2048L));
    }

    private static ConnectionContext mockContext() {
        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.router()).thenReturn(Router.empty());
        when(ctx.listenerContext()).thenReturn(mock(ListenerContext.class));
        return ctx;
    }
}
