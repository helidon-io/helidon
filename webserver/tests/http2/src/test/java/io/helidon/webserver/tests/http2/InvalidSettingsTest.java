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

package io.helidon.webserver.tests.http2;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.tls.Tls;
import io.helidon.http.Method;
import io.helidon.http.http2.Http2ConnectionWriter;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2GoAway;
import io.helidon.http.http2.Http2Setting;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2Util;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.DefaultDnsResolver;
import io.helidon.webclient.api.DnsAddressLookup;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.TcpClientConnection;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;
import static io.netty.handler.codec.http2.Http2CodecUtil.FRAME_HEADER_LENGTH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
public class InvalidSettingsTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String INVALID_MAX_FRAME_SIZE_MESSAGE =
            "Frame size must be between 2^14 and 2^24-1, but is: 0";
    private final WebServer server;

    InvalidSettingsTest(WebServer server) {
        this.server = server;
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        router.route(Http2Route.route(Method.GET, "/", (req, res) -> res.send("pong")));
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        server.addProtocol(Http2Config.builder().sendErrorDetails(true).build());
    }

    @Test
    void invalidMaxFrameSizeReturnsProtocolGoAway() throws InterruptedException, ExecutionException, TimeoutException {
        TcpClientConnection conn = connect();
        try {
            conn.writer().writeNow(Http2Util.prefaceData());
            Http2ConnectionWriter dataWriter = new Http2ConnectionWriter(conn.helidonSocket(), conn.writer(), List.of());

            dataWriter.write(settingsFrame(16384L));
            dataWriter.write(settingsFrame(0L));

            GoAwayResult goAway = awaitGoAway(conn.reader()).get(TIMEOUT.getSeconds(), TimeUnit.SECONDS);
            assertThat(goAway.errorCode(), is(Http2ErrorCode.PROTOCOL));
            assertThat(goAway.details(), is(INVALID_MAX_FRAME_SIZE_MESSAGE));
        } finally {
            conn.closeResource();
        }
    }

    private TcpClientConnection connect() {
        ClientUri clientUri = ClientUri.create(URI.create("http://localhost:" + server.port()));
        ConnectionKey connectionKey = ConnectionKey.create(clientUri.scheme(),
                                                           clientUri.host(),
                                                           clientUri.port(),
                                                           Tls.builder().enabled(false).build(),
                                                           DefaultDnsResolver.create(),
                                                           DnsAddressLookup.defaultLookup(),
                                                           Proxy.noProxy());

        return TcpClientConnection.create(WebClient.builder()
                                                   .baseUri(clientUri)
                                                   .build(),
                                          connectionKey,
                                          List.of(),
                                          connection -> false,
                                          connection -> {
                                          })
                .connect();
    }

    private static Http2FrameData settingsFrame(long maxFrameSize) {
        Http2Settings http2Settings = Http2Settings.builder()
                .add(Http2Setting.INITIAL_WINDOW_SIZE, 65535L)
                .add(Http2Setting.MAX_FRAME_SIZE, maxFrameSize)
                .add(Http2Setting.ENABLE_PUSH, false)
                .build();
        return http2Settings.toFrameData(null, 0, Http2Flag.SettingsFlags.create(0));
    }

    private static CompletableFuture<GoAwayResult> awaitGoAway(DataReader reader) {
        CompletableFuture<GoAwayResult> gotGoAway = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            for (; ; ) {
                BufferData frameHeaderBuffer = reader.readBuffer(FRAME_HEADER_LENGTH);
                Http2FrameHeader frameHeader = Http2FrameHeader.create(frameHeaderBuffer);
                BufferData data = reader.readBuffer(frameHeader.length());
                if (frameHeader.type() == Http2FrameType.GO_AWAY) {
                    byte[] payloadBytes = data.readBytes();
                    Http2GoAway http2GoAway = Http2GoAway.create(BufferData.create(payloadBytes));
                    gotGoAway.complete(new GoAwayResult(http2GoAway.errorCode(),
                                                        new String(payloadBytes,
                                                                   Integer.BYTES * 2,
                                                                   payloadBytes.length - (Integer.BYTES * 2),
                                                                   StandardCharsets.UTF_8)));
                    break;
                }
            }
        });
        return gotGoAway;
    }

    private record GoAwayResult(Http2ErrorCode errorCode, String details) {
    }
}
