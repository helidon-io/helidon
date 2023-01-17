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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.PeerInfo;
import io.helidon.config.Config;
import io.helidon.nima.http2.Http2Setting;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.Router;
import io.helidon.nima.webserver.Routing;
import io.helidon.nima.webserver.spi.ServerConnectionSelector;
import io.helidon.nima.webserver.ServerContext;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.DirectHandlers;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectionConfigTest {

    // Verify that HTTP/2 connection provider is properly configured from config file
    @Test
    void testConnectionConfig()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        // This will pick up application.yaml from the classpath as default configuration file
        Config config = Config.create();

        // Builds LoomServer instance including connectionProviders list.
        WebServer.Builder wsBuilder = WebServer.builder()
                .config(config.get("server"));

        // Call wsBuilder.connectionProviders() trough reflection
        Method connectionProviders
                = WebServer.Builder.class.getDeclaredMethod("connectionProviders", (Class<?>[]) null);
        connectionProviders.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ServerConnectionSelector> providers
                = (List<ServerConnectionSelector>) connectionProviders.invoke(wsBuilder, (Object[]) null);

        // Check whether at least one Http2ConnectionProvider was found
        boolean haveHttp2Provider = false;

        for (ServerConnectionSelector provider : providers) {
            if (provider instanceof Http2ConnectionSelector) {
                haveHttp2Provider = true;
                Http2Connection conn = (Http2Connection) provider.connection(mockContext());
                // Verify values to be updated from configuration file
                assertThat(conn.config().maxFrameSize(), is(8192L));
                assertThat(conn.config().maxHeaderListSize(), is(4096L));
                // Verify Http2Settings values to be updated from configuration file
                assertThat(conn.serverSettings().value(Http2Setting.MAX_FRAME_SIZE), is(8192L));
                assertThat(conn.serverSettings().value(Http2Setting.MAX_HEADER_LIST_SIZE), is(4096L));
            }
        }
        assertThat("No Http2ConnectionProvider was found", haveHttp2Provider, is(true));
    }

    // Verify that HTTP/2 connection provider is properly configured from builder
    @Test
    void testProviderConfigBuilder() {

        Http2ConnectionSelector provider = (Http2ConnectionSelector) Http2ConnectionProvider.builder()
                .http2Config(DefaultHttp2Config.builder()
                                     .maxFrameSize(4096L)
                                     .maxHeaderListSize(2048L)
                                     .build())
                .build()
                .create(it -> Config.empty());


        Http2Connection conn = (Http2Connection) provider.connection(mockContext());
        // Verify values to be updated from configuration file
        assertThat(conn.config().maxFrameSize(), is(4096L));
        assertThat(conn.config().maxHeaderListSize(), is(2048L));
        // Verify Http2Settings values to be updated from configuration file
        assertThat(conn.serverSettings().value(Http2Setting.MAX_FRAME_SIZE), is(4096L));
        assertThat(conn.serverSettings().value(Http2Setting.MAX_HEADER_LIST_SIZE), is(2048L));
    }

    private static ConnectionContext mockContext() {
        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.router()).thenReturn(Router.empty());
        return ctx;
    }
}
