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

import io.helidon.common.testing.http.junit5.SocketHttpClient;
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
import static io.helidon.common.testing.junit5.HexStringDecoder.decodeHexString;

@ServerTest
class ProxyProtocolTest {

    static final String V2_PREFIX = "\0x0D\0x0A\0x0D\0x0A\0x00\0x0D\0x0A\0x51\0x55\0x49\0x54\0x0A";

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
                    && data.destPort() == 443) {
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
                + "\0x20\0x11\0x00\0x0C"    // version, family/protocol, length
                + "\0xC0\0xA8\0x00\0x01"    // 192.168.0.1
                + "\0xC0\0xA8\0x00\0x0B"    // 192.168.0.11
                + "\0xDC\0x04"              // 56324
                + "\0x01\0xBB";             // 443
        socketHttpClient.writeProxyHeader(decodeHexString(header));
        String s = socketHttpClient.sendAndReceive(Method.GET, "");
        assertThat(s, startsWith("HTTP/1.1 200 OK"));
    }
}
