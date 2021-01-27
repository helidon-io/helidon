/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.examples.webclient.blocking;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.metrics.RegistryFactory;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientServiceRequest;
import io.helidon.webclient.blocking.BlockingWebClient;
import io.helidon.webclient.spi.WebClientService;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for verification of WebClient example.
 */
public class BlockingClientMainTest {

    private static final MetricRegistry METRIC_REGISTRY = RegistryFactory.getInstance()
            .getRegistry(MetricRegistry.Type.APPLICATION);

    private static BlockingWebClient webClient;
    private Path testFile;

    @BeforeAll
    public static void startServer() throws ExecutionException, InterruptedException {
        ServerMain.startServer()
                .thenAccept(webServer -> createWebClient(webServer.port()))
                .toCompletableFuture()
                .get();
    }

    @BeforeEach
    public void beforeEach() {
        testFile = Paths.get("test.txt");
    }

    @AfterEach
    public void afterEach() throws IOException {
        Files.deleteIfExists(testFile);
    }

    private static void createWebClient(int port, WebClientService... services) {
        Config config = Config.create();
        WebClient.Builder client = WebClient.builder()
                .baseUri("http://localhost:" + port + "/greet")
                .config(config.get("client"))
                .addMediaSupport(JsonpSupport.create());

        for (WebClientService service : services) {
            client.addService(service);
        }

        BlockingWebClient.Builder builder = BlockingWebClient.builder()
                .webClient(client.build());
        webClient = builder.build();
    }

    @Test
    public void testPerformPutAndGetMethod() {
        String getResult = BlockingClientMain.performGetMethod(webClient);
        assertThat(getResult, is("{\"message\":\"Hello World!\"}"));
        BlockingClientMain.performPutMethod(webClient);
        String changedGetResult = BlockingClientMain.performGetMethod(webClient);
        assertThat(changedGetResult, is("{\"message\":\"Hola World!\"}"));
    }

    @Test
    public void testPerformRedirect() {
        createWebClient(ServerMain.getServerPort(), new RedirectClientServiceTest());
        String result = BlockingClientMain.followRedirects(webClient);
        assertThat(result, is("{\"message\":\"Hello World!\"}"));
    }

    @Test
    public void testFileDownload() throws IOException {
        BlockingClientMain.saveResponseToFile(webClient);
        assertThat(Files.exists(testFile), is(true));

        try {
            assertThat(Files.readString(testFile), is("{\"message\":\"Hello World!\"}"));
        } catch (IOException e) {
            fail(e);
        }

    }

    @Test
    public void testMetricsExample() {
        String counterName = "example.metric.GET.localhost";
        Counter counter = METRIC_REGISTRY.counter(counterName);
        assertThat("Counter " + counterName + " has not been 0", counter.getCount(), is(0L));
        BlockingClientMain.clientMetricsExample("http://localhost:" + ServerMain.getServerPort() + "/greet", Config.create());
        assertThat("Counter " + counterName + " "
                + "has not been 1", counter.getCount(), is(1L));
    }

    private static final class RedirectClientServiceTest implements WebClientService {

        private final boolean redirect = false;

        @Override
        public Single<WebClientServiceRequest> request(WebClientServiceRequest request) {
            request.whenComplete()
                    .thenAccept(response -> {
                        if (response.status() == Http.Status.MOVED_PERMANENTLY_301 && redirect) {
                            fail("Received second redirect! Only one redirect expected here.");
                        } else if (response.status() == Http.Status.OK_200 && !redirect) {
                            fail("There was status 200 without status 301 before it.");
                        }
                    });
            return Single.just(request);
        }
    }

}
