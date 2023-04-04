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

package io.helidon.nima.http2.webserver;

import java.util.List;
import java.util.function.Function;

import io.helidon.config.Config;
import io.helidon.nima.http2.Http2Setting;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.ListenerContext;
import io.helidon.nima.webserver.Router;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.spi.ServerConnectionProvider;
import io.helidon.nima.webserver.spi.ServerConnectionSelector;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectionConfigTest {

    private static ConnectionContext mockContext() {
        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.router()).thenReturn(Router.empty());
        when(ctx.listenerContext()).thenReturn(mock(ListenerContext.class));
        return ctx;
    }

    // Verify that HTTP/2 connection provider is properly configured from config file
    @Test
    void testConnectionConfig() {
        // This will pick up application.yaml from the classpath as default configuration file
        TestProvider provider = new TestProvider();
        WebServer.builder().addConnectionProvider(provider).build();
        assertThat(provider.isConfig(), is(true));
        Http2Config http2Config = provider.config();
        assertThat(http2Config.maxFrameSize(), is(8192));
        assertThat(http2Config.maxHeaderListSize(), is(4096L));
    }

    // Verify that HTTP/2 connection provider is properly configured from builder
    @Test
    void testProviderConfigBuilder() {

        Http2ConnectionSelector provider = (Http2ConnectionSelector) Http2ConnectionProvider.builder()
                .http2Config(DefaultHttp2Config.builder()
                                     .maxFrameSize(4096)
                                     .maxHeaderListSize(2048L)
                                     .build())
                .build()
                .create(it -> Config.empty());

        Http2Connection conn = (Http2Connection) provider.connection(mockContext());
        // Verify values to be updated from configuration file
        assertThat(conn.config().maxFrameSize(), is(4096));
        assertThat(conn.config().maxHeaderListSize(), is(2048L));
        // Verify Http2Settings values to be updated from configuration file
        assertThat(conn.serverSettings().value(Http2Setting.MAX_FRAME_SIZE), is(4096L));
        assertThat(conn.serverSettings().value(Http2Setting.MAX_HEADER_LIST_SIZE), is(2048L));
    }

    // Verify that HTTP/2 MAX_CONCURRENT_STREAMS is properly configured from builder
    @Test
    void testConfigMaxConcurrentStreams() {
        // This will pick up application.yaml from the classpath as default configuration file
        TestProvider provider = new TestProvider();
        WebServer.builder().addConnectionProvider(provider).build();
        assertThat(provider.isConfig(), is(true));
        Http2Config http2Config = provider.config();
        assertThat(http2Config.maxConcurrentStreams(), is(16384L));
    }

    // Verify that HTTP/2 validatePath is properly configured from builder
    @Test
    void testConfigValidatePath() {
        // This will pick up application.yaml from the classpath as default configuration file
        TestProvider provider = new TestProvider();
        WebServer.builder().addConnectionProvider(provider).build();
        assertThat(provider.isConfig(), is(true));
        Http2Config http2Config = provider.config();
        assertThat(http2Config.validatePath(), is(false));
    }

    // Verify that HTTP/2 maximum connection-level window size is properly configured from configuration file
    @Test
    void testInitialWindowSize() {
        // This will pick up application.yaml from the classpath as default configuration file
        TestProvider provider = new TestProvider();
        WebServer.builder().addConnectionProvider(provider).build();
        assertThat(provider.isConfig(), is(true));
        Http2Config http2Config = provider.config();
        assertThat(http2Config.initialWindowSize(), is(8192));
    }

    @Test
    void maxFrameSize() {
        // This will pick up application.yaml from the classpath as default configuration file
        TestProvider provider = new TestProvider();
        WebServer.builder().addConnectionProvider(provider).build();
        assertThat(provider.isConfig(), is(true));
        Http2Config http2Config = provider.config();
        assertThat(http2Config.maxFrameSize(), is(8192));
    }

    @Test
    void flowControlTimeout() {
        // This will pick up application.yaml from the classpath as default configuration file
        TestProvider provider = new TestProvider();
        WebServer.builder().addConnectionProvider(provider).build();
        assertThat(provider.isConfig(), is(true));
        Http2Config http2Config = provider.config();
        assertThat(http2Config.flowControlTimeout(), is(1000L));
    }

    private static class TestProvider implements ServerConnectionProvider {

        private Http2Config http2Config = null;

        @Override
        public Iterable<String> configKeys() {
            return List.of("http_2");
        }

        @Override
        public ServerConnectionSelector create(Function<String, Config> configs) {
            Config config = configs.apply("http_2");
            http2Config = DefaultHttp2Config.toBuilder(config).build();
            return mock(ServerConnectionSelector.class);
        }

        private Http2Config config() {
            return http2Config;
        }

        private boolean isConfig() {
            return http2Config != null;
        }

    }

}
