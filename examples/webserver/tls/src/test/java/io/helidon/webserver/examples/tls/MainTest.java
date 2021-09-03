/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver.examples.tls;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.WebClientTls;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class MainTest {
    private static WebServer configBasedServer;
    private static WebServer builderBasedServer;
    private static WebClient configBasedClient;
    private static WebClient builderBasedClient;

    @BeforeAll
    static void initClass() throws ExecutionException, InterruptedException {
        Config config = Config.create(ConfigSources.classpath("test-application.yaml"),
                                      ConfigSources.classpath("application.yaml"));

        configBasedServer = Main.startConfigBasedServer(config.get("config-based"))
                .toCompletableFuture()
                .get();
        builderBasedServer = Main.startBuilderBasedServer(config.get("builder-based"))
                .toCompletableFuture()
                .get();

        configBasedClient = WebClient.builder()
                .baseUri("https://localhost:" + configBasedServer.port())
                // trust all, as we use a self-signed certificate
                .tls(WebClientTls.builder().trustAll(true).build())
                .build();

        builderBasedClient = WebClient.builder()
                .baseUri("https://localhost:" + builderBasedServer.port())
                // trust all, as we use a self-signed certificate
                .tls(WebClientTls.builder().trustAll(true).build())
                .build();
    }

    @AfterAll
    static void destroyClass() {
        CompletionStage<WebServer> configBased;
        CompletionStage<WebServer> builderBased;

        if (null == configBasedServer) {
            configBased = CompletableFuture.completedFuture(null);
        } else {
            configBased = configBasedServer.shutdown();
        }

        if (null == builderBasedServer) {
            builderBased = CompletableFuture.completedFuture(null);
        } else {
            builderBased = builderBasedServer.shutdown();
        }

        configBased.toCompletableFuture().join();
        builderBased.toCompletableFuture().join();
    }

    static Stream<TestData> testDataSource() {
        return Stream.of(new TestData("Builder based", builderBasedClient),
                         new TestData("Config based", configBasedClient));
    }

    @ParameterizedTest
    @MethodSource("testDataSource")
    void testSsl(TestData testData) {
        String response = testData.client
                .get()
                .request(String.class)
                .await();

        assertThat(testData.type + " SSL server response.", response, is("Hello!"));
    }

    private static class TestData {
        private final String type;
        private final WebClient client;

        private TestData(String type, WebClient client) {
            this.type = type;
            this.client = client;
        }
    }
}