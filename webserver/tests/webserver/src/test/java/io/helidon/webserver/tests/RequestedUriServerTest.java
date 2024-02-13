/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.webserver.tests;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import io.helidon.common.uri.UriInfo;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HeaderNames;
import io.helidon.http.RequestedUriDiscoveryContext.UnsafeRequestedUriSettingsException;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Positive tests that use multiple sockets, each configured with different requested URI discovery settings, and negative
 * tests in which a mis-configured server correctly fails to start.
 */
@ServerTest
class RequestedUriServerTest {

    private static final Map<String, List<String>> TEST_HEADERS = Map.of(
            "Host", List.of("theHost.com"),
            "Forwarded", List.of("by=untrustedProxy.com;for=theClient.com",
                                 "by=myLB.com;for=otherProxy.com;host=myHost.com;proto=https"),
            "X-Forwarded-For", List.of("xClient.com,xUntrustedProxy.com,xLB.com"),
            "X-Forwarded-Host", List.of("myXHost.com"),
            "X-Forwarded-Proto", List.of("http"));

    private static final Map<String, Integer> socketToPort = new HashMap<>();

    private static Config config;

    @BeforeAll
    static void initConfig() {
        config = Config.just(ConfigSources.classpath("/requestUriDiscovery.yaml"));
    }


    @SetUpServer
    static void prepareServer(WebServerConfig.Builder builder) {
        Config config = Config.just(ConfigSources.classpath("requestUriDiscovery.yaml")).get("valid.server");
        builder.config(config);
        builder.sockets()
                .forEach((socketName, socketListener) -> builder.routing(socketName,
                                                                         HttpRouting.builder()
                                                                                 .get("/test",
                                                                                      RequestedUriServerTest::echoHandler)));
    }

    RequestedUriServerTest(WebServer webServer) {
        webServer.prototype().sockets()
                .forEach((socketName, listenerConfig) -> socketToPort.put(socketName, webServer.port(socketName)));
    }

    @Test
    void checkUnsafeDetectionOnEnable() {
        Config c = config.get("test-enabled-no-details.server");
        assertThrows(UnsafeRequestedUriSettingsException.class, () ->
                             WebServer.builder()
                                     .config(c)
                                     .build(),
                     "defaulted non-HOST discovery type with no proxy settings");
    }

    @Test
    void checkExplicitTypesNoDetails() {
        Config c = config.get("test-explicit-types-no-details.server");
        assertThrows(UnsafeRequestedUriSettingsException.class, () ->
                             WebServer.builder()
                                     .config(c)
                                     .build(),
                     "explicit non-HOST discovery types with no proxy settings");
    }

    @Test
    void checkSpecifiedHostDiscovery() throws IOException {
        // Run with no headers at all, especially no forwarding headers.
        runTest("test-enabled-choose-host", "http", "localhost");
    }

    @ParameterizedTest
    @MethodSource
    void checkConfiguredForwarding(TestData testData)
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        // Scenarios:
        // Forwarded case: client requests https://myHost.com -> untrustedProxy.com -> myLB.com (trusted) -> myHost.com
        // X-Forwarded case: xClient requests https://xHost.com -> xUntrustedProxy.com -> xLB.com (trusted) -> myXHost.com
        runTest(testData.socketName, TEST_HEADERS, testData.protocol, testData.host);
    }

    static Stream<TestData> checkConfiguredForwarding() {
        return Stream.of(td("test-x-forwarded-only", "http", "myXHost.com"),
                         td("test-forwarded-only", "https", "myHost.com"),
                         td("test-both-x-forwarded-first", "http", "myXHost.com"),
                         td("test-both-forwarded-first", "https", "myHost.com"),
                         td("test-enabled-choose-host", "http", "theHost.com"),
                         td("test-defaulted-discovery-type", "http",  "theHost.com"));

    }

    private void runTest(String socketName, String expectedProtocol, String expectedHost) throws IOException {
        runTest(socketName, null, expectedProtocol, expectedHost);
    }

    private void runTest(String socketName, Map<String, List<String>> headers, String expectedProtocol, String expectedHost)
            throws IOException {
        Http1Client testClient = Http1Client.builder()
                .readTimeout(Duration.ofSeconds(10))
                .baseUri("http://localhost:" + socketToPort.get(socketName) + "/test")
                .build();

        try {
            Http1ClientRequest clientRequest = testClient.get();
            if (headers != null) {
                headers.forEach((name, values) -> clientRequest.header(HeaderNames.create(name), values));
            }

            try (Http1ClientResponse response = clientRequest.request()) {
                assertThat("Response status", response.status(), is(equalTo(Status.OK_200)));
                String answer = response.entity().as(String.class);
                Properties props = new Properties();
                props.load(new StringReader(answer));

                assertThat("For test scenario " + socketName + " UriInfo host", props.getProperty("host"), is(expectedHost));
                assertThat("For test scenario " + socketName + " UriInfo scheme",
                           props.getProperty("scheme"),
                           is(expectedProtocol));
            }
        } finally {
            testClient.closeResource();
        }
    }

    private static void echoHandler(ServerRequest request, ServerResponse response) {
        UriInfo uriInfo = request.requestedUri();
        Properties props = new Properties();
        props.setProperty("host", uriInfo.host());
        props.setProperty("scheme", uriInfo.scheme());
        props.setProperty("authority", uriInfo.authority());
        props.setProperty("path", uriInfo.path().path());

        StringWriter sw = new StringWriter();
        try {
            props.store(sw, "From server");
            response.send(sw.toString());
        } catch (IOException e) {
            response.status(Status.INTERNAL_SERVER_ERROR_500)
                    .send(e.getMessage());
        }
    }

    private record TestData(String socketName, String protocol, String host) {}

    private static TestData td(String socketName, String protocol, String host) {
        return new TestData(socketName, protocol, host);
    }
}
