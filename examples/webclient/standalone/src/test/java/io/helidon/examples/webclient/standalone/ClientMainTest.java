/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
import io.helidon.config.Config;
import io.helidon.metrics.api.Registry;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.WebClientServiceRequest;
import io.helidon.nima.webclient.WebClientServiceResponse;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.spi.WebClientService;

import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.WebServerConfig;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for verification of WebClient example.
 */
@ServerTest
public class ClientMainTest {

    private static final MetricRegistry METRIC_REGISTRY =
            RegistryFactory.getInstance().getRegistry(Registry.APPLICATION_SCOPE);

    private final WebServer server;
    private final Path testFile;

    public ClientMainTest(WebServer server) {
        this.server = server;
        server.context().register(server);
        this.testFile = Paths.get("test.txt");
    }

    @SetUpServer
    public static void setup(WebServerConfig.Builder server) {
        ServerMain.setup(server, Config.empty());
    }

    @AfterEach
    public void afterEach() throws IOException {
        Files.deleteIfExists(testFile);
    }

    private Http1Client client(WebClientService... services) {
        Config config = Config.create();
        Http1Client.Http1ClientBuilder builder = Http1Client.builder()
                .baseUri("http://localhost:" + server.port() + "/greet")
                .config(config.get("client"));

        for (WebClientService service : services) {
            builder.addService(service);
        }
        return builder.build();
    }

    @Test
    public void testPerformPutAndGetMethod() {
        Http1Client client = client();
        String greeting = ClientMain.performGetMethod(client);
        assertThat(greeting, is("{\"message\":\"Hello World!\"}"));
        ClientMain.performPutMethod(client);
        greeting = ClientMain.performGetMethod(client);
        assertThat(greeting, is("{\"message\":\"Hola World!\"}"));
    }

    @Test
    public void testPerformRedirect() {
        Http1Client client = client(new RedirectClientServiceTest());
        String greeting = ClientMain.followRedirects(client);
        assertThat(greeting, is("{\"message\":\"Hello World!\"}"));
    }

    @Test
    public void testFileDownload() throws IOException {
        Http1Client client = client();
        ClientMain.saveResponseToFile(client);
        assertThat(Files.exists(testFile), is(true));
        assertThat(Files.readString(testFile), is("{\"message\":\"Hello World!\"}"));
    }

    @Test
    public void testMetricsExample() {
        String counterName = "example.metric.GET.localhost";
        Counter counter = METRIC_REGISTRY.counter(counterName);
        assertThat("Counter " + counterName + " has not been 0", counter.getCount(), is(0L));
        ClientMain.clientMetricsExample("http://localhost:" + server.port() + "/greet", Config.create());
        assertThat("Counter " + counterName + " " + "has not been 1", counter.getCount(), is(1L));
    }

    private static final class RedirectClientServiceTest implements WebClientService {

        private volatile boolean redirect = true;

        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest clientRequest) {
            WebClientServiceResponse response = chain.proceed(clientRequest);
            boolean redirect = this.redirect;
            if (response.status() == Http.Status.MOVED_PERMANENTLY_301 && redirect) {
                fail("Received second redirect! Only one redirect expected here.");
            } else if (response.status() == Http.Status.OK_200 && !redirect) {
                fail("There was status 200 without status 301 before it.");
            }
            this.redirect = !redirect;
            return response;
        }
    }

}
