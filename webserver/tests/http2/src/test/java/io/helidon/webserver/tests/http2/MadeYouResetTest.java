/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.io.UncheckedIOException;
import java.net.SocketException;
import java.net.URI;
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
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2ConnectionWriter;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2GoAway;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2Setting;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2Util;
import io.helidon.http.http2.Http2WindowUpdate;
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
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static io.helidon.http.Method.GET;
import static io.netty.handler.codec.http2.Http2CodecUtil.FRAME_HEADER_LENGTH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

@ServerTest
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "https://github.com/helidon-io/helidon/issues/8510")
class MadeYouResetTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private final WebServer server;

    public MadeYouResetTest(WebServer server) {
        this.server = server;
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        router.route(Http2Route.route(GET, "/", (req, res) -> res.send("pong")));
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        server.addProtocol(Http2Config.builder().sendErrorDetails(true).build());
    }

    @Test
    void zeroWindowAttack() throws InterruptedException, ExecutionException, TimeoutException {
        ClientUri clientUri = ClientUri.create(URI.create("http://localhost:" + server.port()));
        ConnectionKey connectionKey = ConnectionKey.create(clientUri.scheme(),
                                                           clientUri.host(),
                                                           clientUri.port(),
                                                           Tls.builder().enabled(false).build(),
                                                           DefaultDnsResolver.create(),
                                                           DnsAddressLookup.defaultLookup(),
                                                           Proxy.noProxy());

        TcpClientConnection conn = TcpClientConnection.create(WebClient.builder()
                                                                      .baseUri(clientUri)
                                                                      .build(),
                                                              connectionKey,
                                                              List.of(),
                                                              connection -> false,
                                                              connection -> {
                                                              })
                .connect();

        BufferData prefaceData = Http2Util.prefaceData();
        conn.writer().writeNow(prefaceData);
        Http2ConnectionWriter dataWriter = new Http2ConnectionWriter(conn.helidonSocket(), conn.writer(), List.of());

        Http2Settings http2Settings = Http2Settings.builder()
                .add(Http2Setting.INITIAL_WINDOW_SIZE, 65535L)
                .add(Http2Setting.MAX_FRAME_SIZE, 16384L)
                .add(Http2Setting.ENABLE_PUSH, false)
                .build();
        Http2Flag.SettingsFlags flags = Http2Flag.SettingsFlags.create(0);
        Http2FrameData frameData = http2Settings.toFrameData(null, 0, flags);
        dataWriter.write(frameData);

        CompletableFuture<String> gotGoAway = new CompletableFuture<>();

        Thread.ofVirtual().start(() -> {
            DataReader reader = conn.reader();
            for (; ; ) {
                BufferData frameHeaderBuffer = reader.readBuffer(FRAME_HEADER_LENGTH);
                Http2FrameHeader frameHeader = Http2FrameHeader.create(frameHeaderBuffer);
                BufferData data = reader.readBuffer(frameHeader.length());
                if (frameHeader.type() == Http2FrameType.GO_AWAY) {
                    Http2GoAway http2GoAway = Http2GoAway.create(data);
                    gotGoAway.complete(http2GoAway.errorCode().name() + " - " + new String(data.readBytes()));
                    break;
                }
            }
        });

        for (int streamId = 1; streamId < 1000; streamId +=2) {
            try {

                WritableHeaders<?> headers = WritableHeaders.create();
                Http2Headers h2Headers = Http2Headers.create(headers);
                h2Headers.method(Method.GET);
                h2Headers.path(clientUri.path().path());
                h2Headers.scheme(clientUri.scheme());

                dataWriter.writeHeaders(h2Headers,
                                        streamId,
                                        Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                        FlowControl.Outbound.NOOP);

                Http2WindowUpdate windowUpdate = new Http2WindowUpdate(0);
                dataWriter.writeData(windowUpdate.toFrameData(http2Settings, streamId, Http2Flag.NoFlags.create()),
                                     FlowControl.Outbound.NOOP);

            } catch (UncheckedIOException ex) {
                assertThat(ex.getCause(), instanceOf(SocketException.class));
            }
        }
        String http2GoAway = gotGoAway.get(TIMEOUT.getSeconds(), TimeUnit.SECONDS);
        assertThat(http2GoAway, is("ENHANCE_YOUR_CALM - MadeYouReset attack detected!"));

        conn.closeResource();
    }

    @Test
    void overflowWindowAttack() throws InterruptedException, ExecutionException, TimeoutException {
        ClientUri clientUri = ClientUri.create(URI.create("http://localhost:" + server.port()));
        ConnectionKey connectionKey = ConnectionKey.create(clientUri.scheme(),
                                                           clientUri.host(),
                                                           clientUri.port(),
                                                           Tls.builder().enabled(false).build(),
                                                           DefaultDnsResolver.create(),
                                                           DnsAddressLookup.defaultLookup(),
                                                           Proxy.noProxy());

        TcpClientConnection conn = TcpClientConnection.create(WebClient.builder()
                                                                      .baseUri(clientUri)
                                                                      .build(),
                                                              connectionKey,
                                                              List.of(),
                                                              connection -> false,
                                                              connection -> {
                                                              })
                .connect();

        BufferData prefaceData = Http2Util.prefaceData();
        conn.writer().writeNow(prefaceData);
        Http2ConnectionWriter dataWriter = new Http2ConnectionWriter(conn.helidonSocket(), conn.writer(), List.of());

        Http2Settings http2Settings = Http2Settings.builder()
                .add(Http2Setting.INITIAL_WINDOW_SIZE, 65535L)
                .add(Http2Setting.MAX_FRAME_SIZE, 16384L)
                .add(Http2Setting.ENABLE_PUSH, false)
                .build();
        Http2Flag.SettingsFlags flags = Http2Flag.SettingsFlags.create(0);
        Http2FrameData frameData = http2Settings.toFrameData(null, 0, flags);
        dataWriter.write(frameData);

        CompletableFuture<String> gotGoAway = new CompletableFuture<>();

        Thread.ofVirtual().start(() -> {
            DataReader reader = conn.reader();
            for (; ; ) {
                BufferData frameHeaderBuffer = reader.readBuffer(FRAME_HEADER_LENGTH);
                Http2FrameHeader frameHeader = Http2FrameHeader.create(frameHeaderBuffer);
                BufferData data = reader.readBuffer(frameHeader.length());
                if (frameHeader.type() == Http2FrameType.GO_AWAY) {
                    Http2GoAway http2GoAway = Http2GoAway.create(data);
                    gotGoAway.complete(http2GoAway.errorCode().name() + " - " + new String(data.readBytes()));
                    break;
                }
            }
        });

        for (int streamId = 1; streamId < 1000; streamId +=2) {
            try {

                WritableHeaders<?> headers = WritableHeaders.create();
                Http2Headers h2Headers = Http2Headers.create(headers);
                h2Headers.method(Method.GET);
                h2Headers.path(clientUri.path().path());
                h2Headers.scheme(clientUri.scheme());

                dataWriter.writeHeaders(h2Headers,
                                        streamId,
                                        Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                        FlowControl.Outbound.NOOP);

                Http2WindowUpdate windowUpdate = new Http2WindowUpdate(2147483646);
                dataWriter.writeData(windowUpdate.toFrameData(http2Settings, streamId, Http2Flag.NoFlags.create()),
                                     FlowControl.Outbound.NOOP);

            } catch (UncheckedIOException ex) {
                assertThat(ex.getCause(), instanceOf(SocketException.class));
            }
        }
        String http2GoAway = gotGoAway.get(TIMEOUT.getSeconds(), TimeUnit.SECONDS);
        assertThat(http2GoAway, is("ENHANCE_YOUR_CALM - MadeYouReset attack detected!"));

        conn.closeResource();
    }
}
