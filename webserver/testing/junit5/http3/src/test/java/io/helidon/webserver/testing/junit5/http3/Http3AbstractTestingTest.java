/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5.http3;

import java.nio.charset.StandardCharsets;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http3.Http3Config;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

@Http3ClientTls(resource = "client-truststore.p12")
abstract class Http3AbstractTestingTest {
    private static final HeaderName QPACK_USER = HeaderNames.create("x-qpack-user");
    private static final HeaderName QPACK_ENV = HeaderNames.create("x-qpack-env");
    private static final HeaderName QPACK_CLUSTER = HeaderNames.create("x-qpack-cluster");
    private static final String USER_VALUE = "alpha-user-1234567890";
    private static final String ENV_VALUE = "dev-eu-central-1";
    private static final String CLUSTER_VALUE = "shared-http3-connection";

    private final Http3Client http3Client;
    private final Http3LowLevelClient lowLevelHttp3Client;

    Http3AbstractTestingTest(Http3Client http3Client, Http3LowLevelClient lowLevelHttp3Client) {
        this.http3Client = http3Client;
        this.lowLevelHttp3Client = lowLevelHttp3Client;
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder serverBuilder) {
        serverBuilder.protocolsDiscoverServices(false);
    }

    @SetUpRoute
    static void routing(HttpRules rules, ListenerConfig.Builder listenerBuilder) {
        configureListener(listenerBuilder, Http3Config.create());
        addGreetingRoutes(rules, "hello");
    }

    static void assertGreeting(Http3Client http3Client, String expectedEntity) {
        try (Http3ClientResponse response = http3Client.get("/greet").request()) {
            assertThat(response.status().code(), is(200));
            assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            assertThat(response.as(String.class), is(expectedEntity));
        }
    }

    static void addDynamicQpackRoute(HttpRules rules) {
        rules.get("/dynamic-qpack", (req, res) -> {
            res.header(QPACK_USER, USER_VALUE);
            res.header(QPACK_ENV, ENV_VALUE);
            res.header(QPACK_CLUSTER, CLUSTER_VALUE);
            res.send("dynamic");
        });
    }

    static void addGreetingRoutes(HttpRules rules, String greeting) {
        rules.get("/greet", (req, res) -> res.send(greeting));
        addDynamicQpackRoute(rules);
    }

    static void configureListener(ListenerConfig.Builder listenerBuilder, Http3Config http3Config) {
        listenerBuilder.protocolsDiscoverServices(false)
                .tls(serverTls())
                .addProtocol(Http1Config.create())
                .addProtocol(http3Config);
    }

    static void assertDynamicQpackReuse(Http3LowLevelClient lowLevelHttp3Client, String expectedWarmupEntity) {
        Http3LowLevelClient.DecodedResponse warmupResponse = lowLevelHttp3Client.get("/greet");
        Http3LowLevelClient.DecodedResponse firstResponse = lowLevelHttp3Client.get("/dynamic-qpack");
        Http3LowLevelClient.DecodedResponse secondResponse = lowLevelHttp3Client.get("/dynamic-qpack");
        Http3LowLevelClient.DecodedResponse thirdResponse = lowLevelHttp3Client.get("/dynamic-qpack");

        assertThat(warmupResponse.status(), equalTo(200));
        assertThat(new String(warmupResponse.body(), StandardCharsets.UTF_8), equalTo(expectedWarmupEntity));

        assertThat(firstResponse.status(), equalTo(200));
        assertThat(firstResponse.headers().first(QPACK_USER).orElseThrow(), equalTo(USER_VALUE));
        assertThat(firstResponse.headers().first(QPACK_ENV).orElseThrow(), equalTo(ENV_VALUE));
        assertThat(firstResponse.headers().first(QPACK_CLUSTER).orElseThrow(), equalTo(CLUSTER_VALUE));
        assertThat(new String(firstResponse.body(), StandardCharsets.UTF_8), equalTo("dynamic"));

        assertThat(secondResponse.status(), equalTo(200));
        assertThat(secondResponse.headers().first(QPACK_USER).orElseThrow(), equalTo(USER_VALUE));
        assertThat(new String(secondResponse.body(), StandardCharsets.UTF_8), equalTo("dynamic"));

        assertThat(thirdResponse.status(), equalTo(200));
        assertThat(thirdResponse.headers().first(QPACK_CLUSTER).orElseThrow(), equalTo(CLUSTER_VALUE));
        assertThat(new String(thirdResponse.body(), StandardCharsets.UTF_8), equalTo("dynamic"));

        assertThat(Math.min(secondResponse.headersPayloadLength(), thirdResponse.headersPayloadLength()),
                   lessThan(firstResponse.headersPayloadLength()));
    }

    static Tls serverTls() {
        try {
            Keys keys = Keys.builder()
                    .keystore(store -> store
                            .passphrase("changeit")
                            .keyAlias("server")
                            .certChainAlias("server")
                            .keystore(Resource.create("server-keystore.p12")))
                    .build();

            return Tls.builder()
                    .privateKey(keys.privateKey().orElseThrow())
                    .privateKeyCertChain(keys.certChain())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize HTTP/3 test server TLS", e);
        }
    }

    @Test
    void testDefaultSocket() {
        assertGreeting(http3Client, "hello");
    }

    @Test
    void testDynamicQpackReuse() {
        assertDynamicQpackReuse(lowLevelHttp3Client, "hello");
    }
}
