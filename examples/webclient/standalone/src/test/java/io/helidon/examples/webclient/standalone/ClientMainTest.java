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
package io.helidon.examples.webclient.standalone;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.media.jsonp.common.JsonpSupport;
import io.helidon.metrics.RegistryFactory;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientServiceRequest;
import io.helidon.webclient.spi.WebClientService;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for verification of WebClient example.
 */
public class ClientMainTest {

    private static final MetricRegistry METRIC_REGISTRY = RegistryFactory.getInstance()
            .getRegistry(MetricRegistry.Type.APPLICATION);

    private WebClient webClient;
    private Path testFile;

    @BeforeEach
    public void beforeEach() throws ExecutionException, InterruptedException {
        testFile = Paths.get("test.txt");
        ServerMain.startServer()
                .thenAccept(webServer -> createWebClient(webServer.port()))
                .toCompletableFuture()
                .get();
    }

    @AfterEach
    public void afterEach() throws IOException {
        Files.deleteIfExists(testFile);
    }

    private void createWebClient(int port, WebClientService... services) {
        Config config = Config.create();
        WebClient.Builder builder = WebClient.builder()
                .baseUri("http://localhost:" + port + "/greet")
                .config(config.get("client"))
                .addMediaSupport(JsonpSupport.create());
        for (WebClientService service : services) {
            builder.register(service);
        }
        webClient = builder.build();
    }

    @Test
    public void testPerformPutAndGetMethod() throws ExecutionException, InterruptedException {
        ClientMain.performGetMethod(webClient)
                .thenAccept(it -> assertThat(it, is("{\"message\":\"Hello World!\"}")))
                .thenCompose(it -> ClientMain.performPutMethod(webClient))
                .thenCompose(it -> ClientMain.performGetMethod(webClient))
                .thenAccept(it -> assertThat(it, is("{\"message\":\"Hola World!\"}")))
                .toCompletableFuture()
                .get();
    }

    @Test
    public void testPerformRedirect() throws ExecutionException, InterruptedException {
        createWebClient(ServerMain.getServerPort(), new RedirectClientServiceTest());
        ClientMain.followRedirects(webClient)
                .thenAccept(it -> assertThat(it, is("{\"message\":\"Hello World!\"}")))
                .toCompletableFuture()
                .get();
    }

    @Test
    public void testFileDownload() throws InterruptedException, ExecutionException {
        ClientMain.saveResponseToFile(webClient)
                .thenAccept(it -> assertThat(Files.exists(testFile), is(true)))
                .thenAccept(it -> {
                    try {
                        assertThat(Files.readString(testFile), is("{\"message\":\"Hello World!\"}"));
                    } catch (IOException e) {
                        fail(e);
                    }
                })
                .toCompletableFuture()
                .get();
    }

    @Test
    public void testMetricsExample() throws ExecutionException, InterruptedException {
        String counterName = "example.metric.GET.localhost";
        Counter counter = METRIC_REGISTRY.counter(counterName);
        assertThat("Counter " + counterName + " has not been 0", counter.getCount(), is(0L));
        ClientMain.clientMetricsExample("http://localhost:" + ServerMain.getServerPort() + "/greet", Config.create())
                .thenAccept(it -> assertThat("Counter " + counterName + " "
                                                     + "has not been 1", counter.getCount(), is(1L)))
                .toCompletableFuture()
                .get();
    }

    private static final class RedirectClientServiceTest implements WebClientService {

        private boolean redirect = false;

        @Override
        public CompletionStage<WebClientServiceRequest> request(WebClientServiceRequest request) {
            request.whenComplete()
                    .thenAccept(response -> {
                        if (response.status() == Http.Status.MOVED_PERMANENTLY_301 && redirect) {
                            fail("Received second redirect! Only one redirect expected here.");
                        } else if (response.status() == Http.Status.OK_200 && !redirect) {
                            fail("There was status 200 without status 301 before it.");
                        }
                    });
            return CompletableFuture.completedFuture(request);
        }
    }

}
