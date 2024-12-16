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

package io.helidon.jersey.connector;

import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.api.HttpClientRequest;

import io.helidon.webclient.api.Proxy;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link HelidonConnector} configuration.
 */
class ConfigTest {

    private static Config config;

    @BeforeAll
    static void init() {
        config = Config.builder(
                        () -> ConfigSources.classpath("application.yaml").build())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
    }

    @Test
    void testConfig() {
        Client client = ClientBuilder.newBuilder()
                .property(HelidonProperties.CONFIG, config.get("client"))
                .build();
        HelidonConnector connector = new HelidonConnector(client, client.getConfiguration());
        HttpClientRequest request = connector.client().get();
        assertThat(request.followRedirects(), is(true));
    }

    @Test
    void testConfigPropertyOverride() {
        Client client = ClientBuilder.newBuilder()
                .property(HelidonProperties.CONFIG, config.get("client"))
                .property(ClientProperties.FOLLOW_REDIRECTS, false)     // override
                .build();
        HelidonConnector connector = new HelidonConnector(client, client.getConfiguration());
        HttpClientRequest request = connector.client().get();
        assertThat(request.followRedirects(), is(false));
    }

    @Test
    void testConfigDefault() {
        Client client = ClientBuilder.newClient();
        HelidonConnector connector = new HelidonConnector(client, client.getConfiguration());
        assertThat(connector.proxy(), is(Proxy.create()));
    }

    @Test
    void testConfigProxy() {
        Client client = ClientBuilder.newBuilder()
                .property(ClientProperties.PROXY_URI, "http://localhost:8080")
                .property(ClientProperties.PROXY_USERNAME, "user")
                .property(ClientProperties.PROXY_PASSWORD, "pass")
                .build();
        HelidonConnector connector = new HelidonConnector(client, client.getConfiguration());
        assertThat(connector.proxy().host(), is("localhost"));
        assertThat(connector.proxy().port(), is(8080));
        assertThat(connector.proxy().type(), is(Proxy.ProxyType.HTTP));
        assertThat(connector.proxy().username(), is(Optional.of("user")));
        assertThat(connector.proxy().password(), notNullValue());
    }

    @Test
    void testConfig100ContinueDefault() {
        Client client = ClientBuilder.newBuilder().build();
        HelidonConnector connector = new HelidonConnector(client, client.getConfiguration());
        assertThat(connector.client().prototype().sendExpectContinue(), is(true));
    }

    @Test
    void testConfig100ContinueOverride() {
        Client client = ClientBuilder.newBuilder()
                .property(ClientProperties.EXPECT_100_CONTINUE, "false")
                .build();
        HelidonConnector connector = new HelidonConnector(client, client.getConfiguration());
        assertThat(connector.client().prototype().sendExpectContinue(), is(false));
    }
}
