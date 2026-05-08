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

package io.helidon.webserver.hsts;

import java.time.Duration;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class HstsFeatureTest {
    private static final String HSTS_VALUE = "max-age=2592000; includeSubDomains; preload";

    private final Http1Client http1Client;
    private final int tlsPort;
    private final Tls clientTls;

    HstsFeatureTest(WebServer server, Http1Client http1Client) {
        this.http1Client = http1Client;
        this.tlsPort = server.port("https");
        this.clientTls = clientTls();
    }

    @SetUpServer
    static void server(WebServerConfig.Builder builder) {
        builder.featuresDiscoverServices(false)
                .clearFeatures()
                .addFeature(HstsFeature.create(it -> it.maxAge(Duration.ofDays(30))
                        .includeSubDomains(true)
                        .preload(true)));
        builder.putSocket("https", socketBuilder -> socketBuilder.tls(serverTls()));
    }

    @SetUpRoute
    static void plainRoutes(HttpRules rules) {
        routing(rules);
    }

    @SetUpRoute("https")
    static void secureRoutes(HttpRules rules) {
        routing(rules);
    }

    @Test
    void plainResponseDoesNotAddHsts() {
        try (Http1ClientResponse response = http1Client.get("/").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers().contains(HeaderNames.STRICT_TRANSPORT_SECURITY), is(false));
        }
    }

    @Test
    void httpsResponseAddsHsts() {
        Http1Client secureClient = secureClient();
        try {
            try (Http1ClientResponse response = secureClient.get("/").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.headers().get(HeaderNames.STRICT_TRANSPORT_SECURITY).get(), is(HSTS_VALUE));
            }
        } finally {
            secureClient.closeResource();
        }
    }

    @Test
    void httpsResponsePreservesCustomHeader() {
        Http1Client secureClient = secureClient();
        try {
            try (Http1ClientResponse response = secureClient.get("/override").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.headers().get(HeaderNames.STRICT_TRANSPORT_SECURITY).get(), is("max-age=1"));
            }
        } finally {
            secureClient.closeResource();
        }
    }

    @Test
    void httpsNotFoundResponseAddsHsts() {
        Http1Client secureClient = secureClient();
        try {
            try (Http1ClientResponse response = secureClient.get("/missing").request()) {
                assertThat(response.status(), is(Status.NOT_FOUND_404));
                assertThat(response.headers().get(HeaderNames.STRICT_TRANSPORT_SECURITY).get(), is(HSTS_VALUE));
            }
        } finally {
            secureClient.closeResource();
        }
    }

    @Test
    void httpsRedirectAddsHsts() {
        Http1Client secureClient = secureClient();
        try {
            try (Http1ClientResponse response = secureClient.get("/redirect")
                    .followRedirects(false)
                    .request()) {
                assertThat(response.status(), is(Status.MOVED_PERMANENTLY_301));
                assertThat(response.headers().get(HeaderNames.STRICT_TRANSPORT_SECURITY).get(), is(HSTS_VALUE));
            }
        } finally {
            secureClient.closeResource();
        }
    }

    @Test
    void httpsErrorAddsHsts() {
        Http1Client secureClient = secureClient();
        try {
            try (Http1ClientResponse response = secureClient.get("/fail").request()) {
                assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
                assertThat(response.headers().get(HeaderNames.STRICT_TRANSPORT_SECURITY).get(), is(HSTS_VALUE));
            }
        } finally {
            secureClient.closeResource();
        }
    }

    private static void routing(HttpRules rules) {
        rules.get("/", (req, res) -> res.send("ok"))
                .get("/override", (req, res) -> res.header(HeaderNames.STRICT_TRANSPORT_SECURITY, "max-age=1").send("ok"))
                .get("/redirect", (req, res) -> res.status(Status.MOVED_PERMANENTLY_301)
                        .header(HeaderNames.LOCATION, "/")
                        .send())
                .get("/fail", (req, res) -> {
                    throw new IllegalStateException("boom");
                });
    }

    private Http1Client secureClient() {
        return Http1Client.builder()
                .baseUri("https://localhost:" + tlsPort)
                .tls(clientTls)
                .build();
    }

    private static Tls serverTls() {
        Keys privateKeyConfig = Keys.builder()
                .keystore(store -> store
                        .passphrase("password")
                        .keystore(Resource.create("server.p12")))
                .build();

        return Tls.builder()
                .privateKey(privateKeyConfig)
                .privateKeyCertChain(privateKeyConfig)
                .build();
    }

    private static Tls clientTls() {
        return Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
    }
}
