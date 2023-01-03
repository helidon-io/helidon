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
package io.helidon.webserver;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import io.helidon.common.http.Http;
import io.helidon.common.http.UriInfo;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.WebClientRequestBuilder;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


class RequestedUriConfigTest extends BaseServerTest {

    private static Config config;

    private static final Map<String, List<String>> TEST_HEADERS = Map.of(
            "Host", List.of("theHost.com"),
            "Forwarded", List.of("by=untrustedProxy.com;for=theClient.com",
                                     "by=myLB.com;for=otherProxy.com;host=myHost.com;proto=https"),
            "X-Forwarded-For", List.of("xClient.com,xUntrustedProxy.com,xLB.com"),
            "X-Forwarded-Host", List.of("myXHost.com"),
            "X-Forwarded-Proto", List.of("http"));

    @BeforeAll
    static void initConfig() {
        config = Config.just(ConfigSources.classpath("/requestUriDiscovery.yaml"));
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
    void checkSpecifiedHostDiscovery() throws Exception {
        runTest("test-enabled-choose-host", "http", "localhost");
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

    @ParameterizedTest
    @MethodSource
    void checkConfiguredForwarding(TestData testData)
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        // Scenarios:
        // Forwarded case: client requests https://myHost.com -> untrustedProxy.com -> myLB.com (trusted) -> myHost.com
        // X-Forwarded case: xClient requests https://xHost.com -> xUntrustedProxy.com -> xLB.com (trusted) -> myXHost.com
        runTest(testData.configKey, TEST_HEADERS, testData.protocol, testData.host);
    }

    static Stream<TestData> checkConfiguredForwarding() {
        return Stream.of(td("test-x-forwarded-only", "http", "myXHost.com"),
                      td("test-forwarded-only", "https", "myHost.com"),
                      td("test-both-x-forwarded-first", "http", "myXHost.com"),
                      td("test-both-forwarded-first", "https", "myHost.com"),
                      td("test-enabled-choose-host", "http", "theHost.com"),
                      td("test-defaulted-discovery-type", "http",  "theHost.com"));

    }

    @Test
    void checkEnum() {
        String v = "x-forwarded";
        Class<?> eClass = SocketConfiguration.RequestedUriDiscoveryType.class;
        if (eClass.isEnum()) {
            SocketConfiguration.RequestedUriDiscoveryType type = null;
            for (Object o : eClass.getEnumConstants()) {
                if (((Enum<?>) o).name().equalsIgnoreCase((v.replace('-', '_')))) {
                    type = (SocketConfiguration.RequestedUriDiscoveryType) o;
                    break;
                }
            }
            assertThat("Mapped string to discovery type",
                       type,
                       allOf(notNullValue(),is(SocketConfiguration.RequestedUriDiscoveryType.X_FORWARDED)));
        }
    }

    private void runTest(String configGroupKey, String expectedProtocol, String expectedHost)
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        runTest(configGroupKey, null, expectedProtocol, expectedHost);
    }

    private void runTest(String configGroupKey, Map<String, List<String>> headers, String expectedProtocol, String expectedHost)
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        Config c = config.get(configGroupKey).get("server");
        Routing.Builder routingBuilder = Routing.builder()
                .get("/test", this::echoHandler);
        startServer(0, routingBuilder.build(), c);

        try {
            WebClientRequestBuilder webClientRequestBuilder = webClient().get()
                    .path("/test");
            if (headers != null) {
                headers.forEach(webClientRequestBuilder::addHeader);
            }
            String answer = webClientRequestBuilder.submit()
                    .get(10, TimeUnit.SECONDS)
                    .content()
                    .as(String.class)
                    .await(10, TimeUnit.SECONDS);

            Properties props = new Properties();
            props.load(new StringReader(answer));

            assertThat("For configKey " + configGroupKey + " UriInfo host", props.getProperty("host"), is(expectedHost));
            assertThat("For configKey " + configGroupKey + " UriInfo scheme", props.getProperty("scheme"), is(expectedProtocol));
        } finally {
            webServer().shutdown().await(10, TimeUnit.SECONDS);
        }
    }

    private void echoHandler(ServerRequest request, ServerResponse response) {
        UriInfo uriInfo = request.requestedUri();
        Properties props = new Properties();
        props.setProperty("host", uriInfo.host());
        props.setProperty("scheme", uriInfo.scheme());
        props.setProperty("authority", uriInfo.authority());
        props.setProperty("path", uriInfo.path());

        StringWriter sw = new StringWriter();
        try {
            props.store(sw, "From server");
            response.send(sw.toString());
        } catch (IOException e) {
            response.status(Http.Status.INTERNAL_SERVER_ERROR_500)
                    .send(e.getMessage());
        }
    }

    private record TestData(String configKey, String protocol, String host) {}

    private static TestData td(String configKey, String protocol, String host) {
        return new TestData(configKey, protocol, host);
    }
}
