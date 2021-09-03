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
package io.helidon.examples.webclient.standalone;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.metrics.RegistryFactory;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientServiceRequest;
import io.helidon.webclient.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webserver.WebServer;

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
    public void beforeEach() {
        testFile = Paths.get("test.txt");
        WebServer server = ServerMain.startServer().await();
        createWebClient(server.port());
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
            builder.addService(service);
        }
        webClient = builder.build();
    }

    @Test
    public void testPerformPutAndGetMethod() {
        ClientMain.performGetMethod(webClient)
                .thenAccept(it -> assertThat(it, is("{\"message\":\"Hello World!\"}")))
                .thenCompose(it -> ClientMain.performPutMethod(webClient))
                .thenCompose(it -> ClientMain.performGetMethod(webClient))
                .thenAccept(it -> assertThat(it, is("{\"message\":\"Hola World!\"}")))
                .await();
    }

    @Test
    public void testPerformRedirect() {
        createWebClient(ServerMain.getServerPort(), new RedirectClientServiceTest());
        ClientMain.followRedirects(webClient)
                .thenAccept(it -> assertThat(it, is("{\"message\":\"Hello World!\"}")))
                .await();
    }

    @Test
    public void testFileDownload() {
        ClientMain.saveResponseToFile(webClient)
                .thenAccept(it -> assertThat(Files.exists(testFile), is(true)))
                .thenAccept(it -> {
                    try {
                        assertThat(Files.readString(testFile), is("{\"message\":\"Hello World!\"}"));
                    } catch (IOException e) {
                        fail(e);
                    }
                })
                .await();
    }

    @Test
    public void testMetricsExample() {
        String counterName = "example.metric.GET.localhost";
        Counter counter = METRIC_REGISTRY.counter(counterName);
        assertThat("Counter " + counterName + " has not been 0", counter.getCount(), is(0L));
        ClientMain.clientMetricsExample("http://localhost:" + ServerMain.getServerPort() + "/greet", Config.create())
                .thenAccept(it -> assertThat("Counter " + counterName + " "
                                                     + "has not been 1", counter.getCount(), is(1L)))
                .await();
    }

    private static final class RedirectClientServiceTest implements WebClientService {

        private volatile boolean redirect = true;

        @Override
        public Single<WebClientServiceResponse> response(WebClientRequestBuilder.ClientRequest request,
                                                         WebClientServiceResponse response) {

            if (response.status() == Http.Status.MOVED_PERMANENTLY_301 && redirect) {
                fail("Received second redirect! Only one redirect expected here.");
            } else if (response.status() == Http.Status.OK_200 && !redirect) {
                fail("There was status 200 without status 301 before it.");
            }
            // not used for now, this test must be refactored
            //redirect = !redirect;
            return Single.just(response);
        }

        @Override
        public Single<WebClientServiceRequest> request(WebClientServiceRequest request) {
            return Single.just(request);
        }
    }

}
