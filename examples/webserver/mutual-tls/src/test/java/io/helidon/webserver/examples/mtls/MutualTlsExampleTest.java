/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.webserver.examples.mtls;

import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test of mutual TLS example.
 */
public class MutualTlsExampleTest {

    private WebServer webServer;

    @AfterEach
    public void killServer() {
        if (webServer != null) {
            webServer.shutdown()
                    .await(10, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testConfigAccessSuccessful() {
        Config config = Config.just(() -> ConfigSources.classpath("application-test.yaml").build());
        webServer = ServerConfigMain.startServer(config.get("server")).await();
        WebClient webClient = WebClient.create(config.get("client"));

        assertThat(ClientConfigMain.callUnsecured(webClient, webServer.port()), is("Hello world unsecured!"));
        assertThat(ClientConfigMain.callSecured(webClient, webServer.port("secured")), is("Hello Helidon-client!"));
    }

    @Test
    public void testBuilderAccessSuccessful() {
        webServer = ServerBuilderMain.startServer(-1, -1).await();
        WebClient webClient = ClientBuilderMain.createWebClient();

        assertThat(ClientBuilderMain.callUnsecured(webClient, webServer.port()), is("Hello world unsecured!"));
        assertThat(ClientBuilderMain.callSecured(webClient, webServer.port("secured")), is("Hello Helidon-client!"));
    }
}