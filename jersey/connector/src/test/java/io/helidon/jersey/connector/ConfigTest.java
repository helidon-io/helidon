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

package io.helidon.jersey.connector;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.nima.webclient.http1.Http1ClientRequest;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

public class ConfigTest {

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
        Http1ClientRequest request = connector.client().get();
        assertThat(request.followRedirects(), is(true));
    }

    @Test
    void testConfigPropertyOverride() {
        Client client = ClientBuilder.newBuilder()
                .property(HelidonProperties.CONFIG, config.get("client"))
                .property(ClientProperties.FOLLOW_REDIRECTS, false)     // override
                .build();
        HelidonConnector connector = new HelidonConnector(client, client.getConfiguration());
        Http1ClientRequest request = connector.client().get();
        assertThat(request.followRedirects(), is(false));
    }
}
