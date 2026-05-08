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
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class HstsHttp2Test {
    private final int tlsPort;
    private final Tls clientTls;

    HstsHttp2Test(WebServer server) {
        this.tlsPort = server.port("https");
        this.clientTls = clientTls();
    }

    @SetUpServer
    static void server(WebServerConfig.Builder builder) {
        builder.featuresDiscoverServices(false)
                .clearFeatures()
                .addFeature(HstsFeature.create(it -> it.maxAge(Duration.ofHours(1))));
        builder.putSocket("https", socketBuilder -> socketBuilder.tls(serverTls()));
    }

    @SetUpRoute("https")
    static void routing(HttpRouting.Builder routing) {
        routing.route(Http2Route.route(Method.GET, "/", (req, res) -> res.send("ok")));
    }

    @Test
    void httpsHttp2AddsHsts() {
        Http2Client secureClient = Http2Client.builder()
                .baseUri("https://localhost:" + tlsPort + "/")
                .shareConnectionCache(false)
                .tls(clientTls)
                .build();
        try (Http2ClientResponse response = secureClient.get("/").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("ok"));
            assertThat(response.headers().get(HeaderNames.STRICT_TRANSPORT_SECURITY).get(), is("max-age=3600"));
        } finally {
            secureClient.closeResource();
        }
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
