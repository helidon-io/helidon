/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.jersey.connector;

import java.time.Duration;

import io.helidon.common.socket.SocketOptions;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.testing.junit5.ServerTest;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import org.junit.jupiter.api.Test;

import static io.helidon.jersey.connector.HelidonProperties.CONFIG;
import static io.helidon.jersey.connector.HelidonProperties.SHARE_CONNECTION_CACHE;
import static org.glassfish.jersey.client.ClientProperties.READ_TIMEOUT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.glassfish.jersey.client.ClientProperties.CONNECT_TIMEOUT;

/**
 * Shows configuration of {@link io.helidon.jersey.connector.HelidonConnector} using
 * server's config read from {@code application.yaml}.
 */
@ServerTest
class ConnectorConfigTest {

    @Test
    void testGlobalConfig() {
        Client client = ClientBuilder.newClient();
        HelidonConnector connector = new HelidonConnector(client, client.getConfiguration());
        WebClient webClient = connector.client();
        assertThat(webClient.prototype().baseUri().orElseThrow().toString(), is("http://foo.bar:8090/"));
        assertThat(webClient.prototype().sendExpectContinue(), is(false));
        assertThat(webClient.prototype().relativeUris(), is(true));
        assertThat(webClient.prototype().shareConnectionCache(), is(false));
        SocketOptions socketOptions = webClient.prototype().socketOptions();
        assertThat(socketOptions.readTimeout(), is(Duration.ofMinutes(1)));
        assertThat(socketOptions.connectTimeout(), is(Duration.ofMinutes(1)));
        assertThat(socketOptions.socketReceiveBufferSize().orElseThrow(), is(1024));
        assertThat(socketOptions.socketSendBufferSize().orElseThrow(), is(1024));
        assertThat(socketOptions.tcpNoDelay(), is(false));
    }

    @Test
    void testGlobalConfigWithOverrides() {
        Client client = ClientBuilder.newClient();
        client.property(CONNECT_TIMEOUT, 50000);              // override
        client.property(READ_TIMEOUT, 50000);                 // override
        client.property(SHARE_CONNECTION_CACHE, Boolean.TRUE);      // override
        HelidonConnector connector = new HelidonConnector(client, client.getConfiguration());
        WebClient webClient = connector.client();
        assertThat(webClient.prototype().shareConnectionCache(), is(true));
        SocketOptions socketOptions = webClient.prototype().socketOptions();
        assertThat(socketOptions.readTimeout(), is(Duration.ofMillis(50000)));
        assertThat(socketOptions.connectTimeout(), is(Duration.ofMillis(50000)));
        assertThat(socketOptions.socketReceiveBufferSize().orElseThrow(), is(1024));
        assertThat(socketOptions.socketSendBufferSize().orElseThrow(), is(1024));
        assertThat(socketOptions.tcpNoDelay(), is(false));
    }

    @Test
    void testGlobalConfigWithBadOverrides() {
        Client client = ClientBuilder.newClient();
        client.property(CONNECT_TIMEOUT, Duration.ofMillis(50000));         // bad type must ignore
        client.property(READ_TIMEOUT, Duration.ofMillis(50000));            // bad type must ignore
        client.property(SHARE_CONNECTION_CACHE, 1);                   // bad type must ignore
        HelidonConnector connector = new HelidonConnector(client, client.getConfiguration());
        WebClient webClient = connector.client();
        assertThat(webClient.prototype().shareConnectionCache(), is(false));
        SocketOptions socketOptions = webClient.prototype().socketOptions();
        assertThat(socketOptions.readTimeout(), is(Duration.ofMinutes(1)));
        assertThat(socketOptions.connectTimeout(), is(Duration.ofMinutes(1)));
    }

    @Test
    void testCustomConfig() {
        Config custom = Config.builder(() -> ConfigSources.classpath("custom.yaml").build())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        Client client = ClientBuilder.newClient();
        client.property(CONFIG, custom.get("connector"));        // overrides
        HelidonConnector connector = new HelidonConnector(client, client.getConfiguration());
        WebClient webClient = connector.client();
        assertThat(webClient.prototype().baseUri().orElseThrow().toString(), is("http://foo2.bar2:8090/"));
        assertThat(webClient.prototype().sendExpectContinue(), is(true));
        assertThat(webClient.prototype().relativeUris(), is(false));
        assertThat(webClient.prototype().shareConnectionCache(), is(true));
        SocketOptions socketOptions = webClient.prototype().socketOptions();
        assertThat(socketOptions.readTimeout(), is(Duration.ofMinutes(10)));
        assertThat(socketOptions.connectTimeout(), is(Duration.ofMinutes(10)));
        assertThat(socketOptions.socketReceiveBufferSize().orElseThrow(), is(10240));
        assertThat(socketOptions.socketSendBufferSize().orElseThrow(), is(10240));
        assertThat(socketOptions.tcpNoDelay(), is(true));
    }
}
