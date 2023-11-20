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
package io.helidon.webserver.tests;

import java.util.HexFormat;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webserver.ProxyProtocolData;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class ProxyProtocolTest {

    static final String V2_PREFIX = "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A";

    private final static HexFormat hexFormat = HexFormat.of().withUpperCase().withDelimiter(":");
    
    private final SocketHttpClient socketHttpClient;

    ProxyProtocolTest(SocketHttpClient socketHttpClient) {
        this.socketHttpClient = socketHttpClient;
    }

    @SetUpServer
    static void setupServer(WebServerConfig.Builder builder) {
        builder.enableProxyProtocol(true);
    }

    @SetUpRoute
    static void routing(HttpRules routing) {
        routing.get("/", (req, res) -> {
            ProxyProtocolData data = req.proxyProtocolData().orElse(null);
            if (data != null
                    && data.family() == ProxyProtocolData.Family.IPv4
                    && data.protocol() == ProxyProtocolData.Protocol.TCP
                    && data.sourceAddress().equals("192.168.0.1")
                    && data.destAddress().equals("192.168.0.11")
                    && data.sourcePort() == 56324
                    && data.destPort() == 443
                    && "192.168.0.1".equals(req.headers().first(HeaderNames.X_FORWARDED_FOR).orElse(null))
                    && "56324".equals(req.headers().first(HeaderNames.X_FORWARDED_PORT).orElse(null))) {
                res.status(Status.OK_200).send();
                return;
            }
            res.status(Status.INTERNAL_SERVER_ERROR_500).send();
        });
    }

    /**
     * V1 encoding in this test was manually verified with Wireshark.
     */
    @Test
    void testProxyProtocolV1IPv4() {
        socketHttpClient.writeProxyHeader("PROXY TCP4 192.168.0.1 192.168.0.11 56324 443\r\n".getBytes(US_ASCII));
        String s = socketHttpClient.sendAndReceive(Method.GET, "");
        assertThat(s, startsWith("HTTP/1.1 200 OK"));
    }

    /**
     * V2 encoding in this test was manually verified with Wireshark.
     */
    @Test
    void testProxyProtocolV2IPv4() {
        String header = V2_PREFIX
                + ":20:11:00:0C"    // version, family/protocol, length
                + ":C0:A8:00:01"    // 192.168.0.1
                + ":C0:A8:00:0B"    // 192.168.0.11
                + ":DC:04"          // 56324
                + ":01:BB";         // 443
        socketHttpClient.writeProxyHeader(hexFormat.parseHex(header));
        String s = socketHttpClient.sendAndReceive(Method.GET, "");
        assertThat(s, startsWith("HTTP/1.1 200 OK"));
    }
}
