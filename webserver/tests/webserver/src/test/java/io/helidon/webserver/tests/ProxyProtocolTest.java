/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.configurable.AllowList;
import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ProxyProtocolData;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.http1.Http1ConnectionListener;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class ProxyProtocolTest {

    static final String V2_PREFIX = "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A";
    private static final String V1_IPV4_HEADER = "PROXY TCP4 192.168.0.1 192.168.0.11 56324 443\r\n";
    private static final String V1_UNKNOWN_HEADER = "PROXY UNKNOWN ignored fields\r\n";

    private final static HexFormat hexFormat = HexFormat.of().withUpperCase().withDelimiter(":");
    private static final AtomicReference<List<String>> LISTENER_X_FORWARDED_FOR = new AtomicReference<>();
    private static final AtomicReference<List<String>> LISTENER_X_FORWARDED_PORT = new AtomicReference<>();
    
    private final SocketHttpClient socketHttpClient;

    ProxyProtocolTest(SocketHttpClient socketHttpClient) {
        this.socketHttpClient = socketHttpClient;
    }

    @BeforeEach
    void resetSocket() {
        socketHttpClient.disconnect();
        LISTENER_X_FORWARDED_FOR.set(null);
        LISTENER_X_FORWARDED_PORT.set(null);
    }

    @SetUpServer
    static void setupServer(WebServerConfig.Builder builder) {
        builder.proxyProtocol(it -> it.trustedProxies(AllowList.builder()
                                                           .allowAll(true)
                                                           .build()));
        builder.addConnectionSelector(Http1ConnectionSelector.builder()
                                              .config(Http1Config.builder()
                                                              .addReceiveListener(new Http1ConnectionListener() {
                                                                  @Override
                                                                  public void headers(ConnectionContext ctx, Headers headers) {
                                                                      LISTENER_X_FORWARDED_FOR.set(
                                                                              headers.all(HeaderNames.X_FORWARDED_FOR, List::of));
                                                                      LISTENER_X_FORWARDED_PORT.set(
                                                                              headers.all(HeaderNames.X_FORWARDED_PORT, List::of));
                                                                  }
                                                              })
                                                              .build())
                                              .build());
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
                    && "443".equals(req.headers().first(HeaderNames.X_FORWARDED_PORT).orElse(null))
                    && req.headers().all(HeaderNames.X_FORWARDED_FOR, List::of).equals(List.of("192.168.0.1"))
                    && req.headers().all(HeaderNames.X_FORWARDED_PORT, List::of).equals(List.of("443"))) {
                res.status(Status.OK_200).send();
                return;
            }
            res.status(Status.INTERNAL_SERVER_ERROR_500).send();
        });
        routing.get("/unknown", (req, res) -> {
            ProxyProtocolData data = req.proxyProtocolData().orElse(null);
            if (data != null
                    && data.family() == ProxyProtocolData.Family.UNKNOWN
                    && data.protocol() == ProxyProtocolData.Protocol.UNKNOWN
                    && data.sourceAddress().isEmpty()
                    && data.destAddress().isEmpty()
                    && data.sourcePort() == -1
                    && data.destPort() == -1
                    && req.headers().all(HeaderNames.X_FORWARDED_FOR, List::of).isEmpty()
                    && req.headers().all(HeaderNames.X_FORWARDED_PORT, List::of).isEmpty()) {
                res.status(Status.OK_200).send();
                return;
            }
            res.status(Status.INTERNAL_SERVER_ERROR_500).send();
        });
        routing.get("/unix", (req, res) -> {
            ProxyProtocolData data = req.proxyProtocolData().orElse(null);
            if (data != null
                    && data.family() == ProxyProtocolData.Family.UNIX
                    && data.protocol() == ProxyProtocolData.Protocol.TCP
                    && req.headers().all(HeaderNames.X_FORWARDED_FOR, List::of).isEmpty()
                    && req.headers().all(HeaderNames.X_FORWARDED_PORT, List::of).isEmpty()) {
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
        socketHttpClient.writeProxyHeader(V1_IPV4_HEADER.getBytes(US_ASCII));
        String s = socketHttpClient.sendAndReceive(Method.GET, "");
        assertThat(s, startsWith("HTTP/1.1 200 OK"));
    }

    @Test
    void testProxyProtocolV1IPv4ReplacesForgedForwardedHeaders() {
        socketHttpClient.writeProxyHeader(V1_IPV4_HEADER.getBytes(US_ASCII));
        String s = socketHttpClient.sendAndReceive(Method.GET, "/",
                                                   null,
                                                   List.of("X-Forwarded-For: 10.0.0.5",
                                                           "X-Forwarded-Port: 1234"));
        assertThat(s, startsWith("HTTP/1.1 200 OK"));
        assertThat(LISTENER_X_FORWARDED_FOR.get(), is(List.of("192.168.0.1")));
        assertThat(LISTENER_X_FORWARDED_PORT.get(), is(List.of("443")));
    }

    @Test
    void testProxyProtocolUnknownRemovesForgedForwardedHeaders() {
        socketHttpClient.writeProxyHeader(V1_UNKNOWN_HEADER.getBytes(US_ASCII));
        String s = socketHttpClient.sendAndReceive(Method.GET, "/unknown",
                                                   null,
                                                   List.of("X-Forwarded-For: 10.0.0.5",
                                                           "X-Forwarded-Port: 1234"));
        assertThat(s, startsWith("HTTP/1.1 200 OK"));
        assertThat(LISTENER_X_FORWARDED_FOR.get(), is(List.<String>of()));
        assertThat(LISTENER_X_FORWARDED_PORT.get(), is(List.<String>of()));
    }

    /**
     * V2 encoding in this test was manually verified with Wireshark.
     */
    @Test
    void testProxyProtocolV2IPv4() {
        String header = V2_PREFIX
                + ":21:11:00:0C"    // version, family/protocol, length
                + ":C0:A8:00:01"    // 192.168.0.1
                + ":C0:A8:00:0B"    // 192.168.0.11
                + ":DC:04"          // 56324
                + ":01:BB";         // 443
        socketHttpClient.writeProxyHeader(hexFormat.parseHex(header));
        String s = socketHttpClient.sendAndReceive(Method.GET, "");
        assertThat(s, startsWith("HTTP/1.1 200 OK"));
    }

    @Test
    void testProxyProtocolV2UnixDoesNotCreateForwardedHeaders() {
        assertUnixDoesNotCreateForwardedHeaders("/tmp/source");
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Windows paths reject CR, LF, and colon characters")
    void testProxyProtocolV2UnixPathDoesNotInjectForwardedHeaders() {
        assertUnixDoesNotCreateForwardedHeaders("/tmp/source\r\nx-forwarded-for: attacker");
    }

    private void assertUnixDoesNotCreateForwardedHeaders(String source) {
        byte[] header = new byte[16 + 216];
        byte[] prefix = hexFormat.parseHex(V2_PREFIX);
        System.arraycopy(prefix, 0, header, 0, prefix.length);
        header[12] = 0x21;            // version 2, command PROXY
        header[13] = 0x31;            // UNIX family, stream protocol
        header[14] = 0x00;
        header[15] = (byte) 0xD8;     // two 108-byte UNIX paths
        byte[] sourcePath = source.getBytes(US_ASCII);
        System.arraycopy(sourcePath, 0, header, 16, sourcePath.length);
        byte[] destPath = "/tmp/destination".getBytes(US_ASCII);
        System.arraycopy(destPath, 0, header, 16 + 108, destPath.length);

        socketHttpClient.writeProxyHeader(header);
        String s = socketHttpClient.sendAndReceive(Method.GET,
                                                   "/unix",
                                                   null,
                                                   List.of("X-Forwarded-For: 10.0.0.5",
                                                           "X-Forwarded-Port: 1234"));
        assertThat(s, startsWith("HTTP/1.1 200 OK"));
        assertThat(LISTENER_X_FORWARDED_FOR.get(), is(List.<String>of()));
        assertThat(LISTENER_X_FORWARDED_PORT.get(), is(List.<String>of()));
    }
}
