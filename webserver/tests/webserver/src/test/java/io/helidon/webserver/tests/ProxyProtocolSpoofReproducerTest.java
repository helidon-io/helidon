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
package io.helidon.webserver.tests;

import java.io.UncheckedIOException;
import java.net.SocketException;
import java.util.List;

import io.helidon.common.configurable.AllowList;
import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.webserver.ProxyProtocolData;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class ProxyProtocolSpoofReproducerTest {
    private static final String SPOOFED_PROXY_SOURCE = "198.51.100.77";
    private static final String TRUSTED_PROXY_SOURCE = "192.0.2.200";

    private final SocketHttpClient socketHttpClient;

    ProxyProtocolSpoofReproducerTest(SocketHttpClient socketHttpClient) {
        this.socketHttpClient = socketHttpClient;
    }

    @SetUpServer
    static void setupServer(WebServerConfig.Builder builder) {
        builder.proxyProtocol(it -> it.trustedProxies(AllowList.builder()
                                                           .addAllowed(TRUSTED_PROXY_SOURCE)
                                                           .build()));
    }

    @SetUpRoute
    static void routing(HttpRules routing) {
        routing.get("/admin-by-proxy-source", (req, res) -> {
            ProxyProtocolData data = req.proxyProtocolData().orElse(null);
            if (data != null && SPOOFED_PROXY_SOURCE.equals(data.sourceAddress())) {
                res.send("proxy-data-source=" + data.sourceAddress() + "\n");
                return;
            }

            res.send("proxy-data-missing\n");
        });

        routing.get("/xff-first", (req, res) -> {
            List<String> allXForwardedFor = req.headers().all(HeaderNames.X_FORWARDED_FOR, List::of);
            String firstXForwardedFor = req.headers().first(HeaderNames.X_FORWARDED_FOR).orElse("<missing>");
            String proxySource = req.proxyProtocolData()
                    .map(ProxyProtocolData::sourceAddress)
                    .orElse("<missing>");

            res.send("first-x-forwarded-for=" + firstXForwardedFor + "\n"
                             + "all-x-forwarded-for=" + String.join("|", allXForwardedFor) + "\n"
                             + "proxy-data-source=" + proxySource + "\n");
        });
    }

    @Test
    void forgedProxyLineFromDirectClientIsRejected() {
        assertRejected("/admin-by-proxy-source", List.of());
    }

    @Test
    void forgedProxyLineWithXForwardedForFromDirectClientIsRejected() {
        assertRejected("/xff-first", List.of("X-Forwarded-For: 10.0.0.5"));
    }

    private void assertRejected(String path, Iterable<String> headers) {
        socketHttpClient.writeProxyHeader(("PROXY TCP4 " + SPOOFED_PROXY_SOURCE + " 192.0.2.10 4242 443\r\n")
                                                  .getBytes(US_ASCII));

        try {
            assertThat(socketHttpClient.sendAndReceive(Method.GET, path, null, headers), is(""));
        } catch (UncheckedIOException e) {
            if (!(e.getCause() instanceof SocketException)) {
                throw e;
            }
        }
    }
}
