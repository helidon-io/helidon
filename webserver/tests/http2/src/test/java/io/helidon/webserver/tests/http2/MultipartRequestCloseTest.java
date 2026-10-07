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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.buffers.BufferData;
import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.deflate.DeflateEncoding;
import io.helidon.http.encoding.gzip.GzipEncoding;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2Ping;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.multipart.MultiPart;
import io.helidon.http.media.multipart.MultiPartSupport;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.http2.Http2TestClient;
import io.helidon.webserver.testing.junit5.http2.Http2TestConnection;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.helidon.http.Method.POST;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class MultipartRequestCloseTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String BOUNDARY = "request-close-boundary";
    private static final String CONTENT_TYPE = "multipart/form-data; boundary=" + BOUNDARY;
    private static final byte[] ENTITY = ("--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=value\r\n"
            + "Content-Type: text/plain\r\n\r\nvalue\r\n"
            + "--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII);

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.mediaContext(MediaContext.builder()
                        .mediaSupportsDiscoverServices(false)
                        .addMediaSupport(MultiPartSupport.create(Config.empty()))
                        .build())
                .contentEncoding(ContentEncodingContext.builder()
                        .addContentEncoding(GzipEncoding.create())
                        .addContentEncoding(DeflateEncoding.create())
                        .build())
                .addProtocol(Http2Config.builder().build());
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder rules) {
        rules.route(Http2Route.route(POST, "/iterate", (req, res) -> {
                    int count = 0;
                    try (MultiPart parts = req.content().as(MultiPart.class)) {
                        while (parts.hasNext()) {
                            count += parts.next().as(String.class).length();
                        }
                    }
                    res.send(Integer.toString(count));
                }))
                .route(Http2Route.route(POST, "/close", (req, res) -> {
                    int first;
                    try (MultiPart parts = req.content().as(MultiPart.class)) {
                        first = parts.next().inputStream().read();
                    }
                    res.send(Integer.toString(first));
                }));
    }

    @ParameterizedTest
    @CsvSource({
            "iterate, identity",
            "iterate, gzip",
            "iterate, deflate",
            "close, identity",
            "close, gzip",
            "close, deflate"
    })
    void multipartCleanupDoesNotWaitForRemainingHttpBody(String route,
                                                         String encoding,
                                                         Http2TestClient client) throws IOException {
        byte[] payload = encodedEntity(encoding);
        String expected = route.equals("iterate") ? "5" : Integer.toString('v');
        try (var connection = client.createConnection()) {
            completeHandshake(connection);
            var headers = WritableHeaders.create()
                    .set(HeaderNames.CONTENT_TYPE, CONTENT_TYPE)
                    .set(HeaderNames.CONTENT_ENCODING, encoding)
                    .set(HeaderNames.CONTENT_LENGTH, payload.length + 1);
            Http2Headers request = Http2Headers.create(headers);
            request.method(POST);
            request.path("/" + route);
            request.scheme(connection.clientUri().scheme());
            request.authority(connection.clientUri().authority());
            connection.writer().writeHeaders(request, 1,
                                             Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                             FlowControl.Outbound.NOOP);
            BufferData body = BufferData.create(payload);
            Http2FrameHeader dataHeader = Http2FrameHeader.create(body.available(), Http2FrameTypes.DATA,
                                                                  Http2Flag.DataFlags.create(0), 1);
            connection.writer().writeData(new Http2FrameData(dataHeader, body), FlowControl.Outbound.NOOP);

            // The remaining declared byte and END_STREAM are withheld while awaiting the response.
            Http2Headers response = connection.assertHeaders(1, TIMEOUT);
            assertThat(route + "/" + encoding + " completed before the HTTP body", response.status(), is(Status.OK_200));
            var responseBody = new ByteArrayOutputStream();
            boolean responseComplete = false;
            while (!responseComplete) {
                Http2FrameData frame = connection.assertNextFrame(Http2FrameType.DATA, TIMEOUT);
                assertThat(frame.header().streamId(), is(1));
                responseBody.writeBytes(frame.data().readBytes());
                responseComplete = frame.header().flags(Http2FrameTypes.DATA).endOfStream();
            }
            assertThat("Response completed while request body remained open",
                       responseBody.toString(StandardCharsets.US_ASCII), is(expected));
        }
    }

    private static void completeHandshake(Http2TestConnection connection) {
        connection.assertSettings(TIMEOUT);
        connection.writer().write(new Http2FrameData(Http2FrameHeader.create(0,
                                                                           Http2FrameTypes.SETTINGS,
                                                                           Http2Flag.SettingsFlags.create(Http2Flag.ACK),
                                                                           0),
                                                   BufferData.empty()));
        connection.writer().write(Http2Ping.create().toFrameData());
        Http2FrameData frame = connection.awaitNextFrame(TIMEOUT);
        assertThat("HTTP/2 startup SETTINGS ACK or WINDOW_UPDATE frame", frame, notNullValue());
        if (frame.header().type() == Http2FrameType.WINDOW_UPDATE) {
            assertThat(frame.header().streamId(), is(0));
            frame = connection.assertNextFrame(Http2FrameType.SETTINGS, TIMEOUT);
        }
        assertThat(frame.header().type(), is(Http2FrameType.SETTINGS));
        assertThat(frame.header().flags(Http2FrameTypes.SETTINGS).ack(), is(true));
        Http2FrameData pingFrame = connection.assertNextFrame(Http2FrameType.PING, TIMEOUT);
        assertThat(pingFrame.header().flags(Http2FrameTypes.PING).ack(), is(true));
    }

    private static byte[] encodedEntity(String encoding) throws IOException {
        if (encoding.equals("identity")) {
            return ENTITY;
        }
        var bytes = new ByteArrayOutputStream();
        try (OutputStream encoder = switch (encoding) {
            case "gzip" -> new GZIPOutputStream(bytes);
            case "deflate" -> new DeflaterOutputStream(bytes);
            default -> throw new IllegalArgumentException("Unexpected encoding: " + encoding);
        }) {
            encoder.write(ENTITY);
        }
        return bytes.toByteArray();
    }
}
