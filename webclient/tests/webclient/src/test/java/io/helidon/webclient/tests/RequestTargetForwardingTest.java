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

package io.helidon.webclient.tests;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

class RequestTargetForwardingTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("requestTargets")
    @Timeout(30)
    void forwardsRawPathAndQuery(String protocol, String inboundTarget, String path, boolean hasQuery, String query)
            throws Exception {
        WebServer upstream = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .protocolsDiscoverServices(false)
                .addConnectionSelector(Http1ConnectionSelector.builder().config(Http1Config.create()).build())
                .addConnectionSelector(Http2ConnectionSelector.builder().http2Config(Http2Config.create()).build())
                .routing(routing -> routing.any((request, response) -> {
                    var prologue = request.prologue();
                    response.send(prologue.uriPath().rawPath() + "\n"
                                          + prologue.hasQuery() + "\n"
                                          + prologue.query().rawValue());
                }))
                .build()
                .start();
        try {
            WebClient client = WebClient.builder()
                    .baseUri("http://127.0.0.1:" + upstream.port() + "/unrelated/base?discard=me")
                    .proxy(Proxy.noProxy())
                    .shareConnectionCache(false)
                    .connectTimeout(TIMEOUT)
                    .readTimeout(TIMEOUT)
                    .protocolPreference(List.of(protocol))
                    .addProtocolConfig(Http2ClientProtocolConfig.builder().priorKnowledge(true).build())
                    .build();
            try {
                WebServer proxy = WebServer.builder()
                        .host("127.0.0.1")
                        .port(0)
                        .routing(routing -> routing.any((request, response) -> {
                            try (var forwarded = client.get().requestTarget(request.prologue()).request()) {
                                String body = forwarded.protocolId() + "\n" + forwarded.as(String.class);
                                response.contentLength(body.getBytes(StandardCharsets.US_ASCII).length);
                                response.send(body);
                            }
                        }))
                        .build()
                        .start();
                try (var socket = new Socket()) {
                    socket.connect(new InetSocketAddress("127.0.0.1", proxy.port()), (int) TIMEOUT.toMillis());
                    socket.setSoTimeout((int) TIMEOUT.toMillis());
                    // Send bytes directly so absolute-form and a bare '?' reach the inbound parser unchanged.
                    socket.getOutputStream().write(("GET " + inboundTarget + " HTTP/1.1\r\n"
                                                            + "Host: inbound.example.invalid\r\n"
                                                            + "Connection: close\r\n\r\n")
                                                           .getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    String received = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
                    String[] response = received.split("\r\n\r\n", 2);
                    assertThat("proxy response status", response[0], startsWith("HTTP/1.1 200 "));
                    assertThat("proxy response includes a body", response.length, is(2));
                    assertThat("upstream protocol, raw path, query delimiter, and raw query", response[1],
                               is(protocol + "\n" + path + "\n" + hasQuery + "\n" + query));
                } finally {
                    proxy.stop();
                }
            } finally {
                client.closeResource();
            }
        } finally {
            upstream.stop();
        }
    }

    private static Stream<Arguments> requestTargets() {
        return Stream.of(Http1Client.PROTOCOL_ID, Http2Client.PROTOCOL_ID)
                .flatMap(protocol -> Stream.of(
                        Arguments.of(protocol, "/proxy/a%2Fb%2fc/%7e%7E", "/proxy/a%2Fb%2fc/%7e%7E", false, ""),
                        Arguments.of(protocol, "/proxy;a=1/b;c=2/./d/../e", "/proxy;a=1/b;c=2/./d/../e", false, ""),
                        Arguments.of(protocol, "/proxy?b=2&a=1&b=3&&flag&empty=&=value&", "/proxy", true,
                                     "b=2&a=1&b=3&&flag&empty=&=value&"),
                        Arguments.of(protocol, "/proxy?value=%2f%2F%7e%7E%25%3f%3F&space=+%20", "/proxy", true,
                                     "value=%2f%2F%7e%7E%25%3f%3F&space=+%20"),
                        Arguments.of(protocol, "/proxy", "/proxy", false, ""),
                        Arguments.of(protocol, "/proxy?", "/proxy", true, ""),
                        Arguments.of(protocol, "//other.example.invalid/a%2Fb?x=1&&", "//other.example.invalid/a%2Fb", true,
                                     "x=1&&"),
                        Arguments.of(protocol, "http://source.example.invalid:8123/absolute/a%2fb?x=%2F&x=", "/absolute/a%2fb",
                                     true, "x=%2F&x="),
                        Arguments.of(protocol, "https://secure.example.invalid:9443/absolute;a=1/./b?", "/absolute;a=1/./b",
                                     true, ""),
                        Arguments.of(protocol, "https://source.example.invalid", "/", false, "")));
    }
}
