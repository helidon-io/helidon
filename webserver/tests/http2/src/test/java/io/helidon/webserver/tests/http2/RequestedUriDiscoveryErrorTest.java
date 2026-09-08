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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.configurable.AllowList;
import io.helidon.http.DirectHandler;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.Method;
import io.helidon.http.RequestedUriDiscoveryContext;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2GoAway;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2HuffmanDecoder;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Setting;
import io.helidon.security.Security;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.security.SecurityFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.http2.Http2TestClient;
import io.helidon.webserver.testing.junit5.http2.Http2TestConnection;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class RequestedUriDiscoveryErrorTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final HeaderName EXCEPTION_HEADER = HeaderNames.create("X-Http-Exception");
    private static final HeaderName DIRECT_HANDLER_HOST_HEADER = HeaderNames.create("X-Direct-Handler-Host");
    private final Http2Headers.DynamicTable responseDynamicTable =
            Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
    private final Http2HuffmanDecoder responseHuffman = Http2HuffmanDecoder.create();
    private final HttpClient client;
    private final URI uri;

    RequestedUriDiscoveryErrorTest(URI uri) {
        this.uri = uri;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        var requestedUriDiscovery = RequestedUriDiscoveryContext.builder()
                .enabled(true)
                .addDiscoveryType(RequestedUriDiscoveryContext.RequestedUriDiscoveryType.FORWARDED)
                .addDiscoveryType(RequestedUriDiscoveryContext.RequestedUriDiscoveryType.X_FORWARDED)
                .trustedProxies(AllowList.builder().allowAll(true).build())
                .build();
        var directHandlers = DirectHandlers.builder()
                .addHandler(DirectHandler.EventType.BAD_REQUEST,
                            (request, eventType, status, responseHeaders, message) ->
                                    DirectHandler.TransportResponse.builder()
                                    .status(Status.BAD_REQUEST_400)
                                    .header(DIRECT_HANDLER_HOST_HEADER,
                                            request.headers().first(HeaderNames.HOST).orElse("missing"))
                                    .entity("handled")
                                    .build())
                .build();
        server.featuresDiscoverServices(false)
                .requestedUriDiscoveryContext(requestedUriDiscovery)
                .directHandlers(directHandlers)
                .addProtocol(Http2Config.builder().maxConcurrentStreams(1).build())
                .addFeature(ContextFeature.create())
                .addFeature(SecurityFeature.builder()
                                    .security(Security.builder().build())
                                    .build());
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        routing.route(Method.GET, "/", (req, res) -> res.send("ok"))
                .route(Method.GET, "/http-exception", (req, res) -> {
                    throw new HttpException("Unprocessable", Status.UNPROCESSABLE_CONTENT_422)
                            .header(HeaderValues.create(EXCEPTION_HEADER, "present"));
                });
    }

    @Test
    void genericHttpExceptionReturnsConfiguredStatus() throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                                                            .timeout(Duration.ofSeconds(5))
                                                            .uri(uri.resolve("/http-exception"))
                                                            .GET()
                                                            .build(),
                                                    HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode(), is(Status.UNPROCESSABLE_CONTENT_422.code()));
        assertThat(response.headers().firstValue(EXCEPTION_HEADER.lowerCase()), is(Optional.of("present")));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void invalidXForwardedSchemeReturnsBadRequest() throws IOException, InterruptedException {
        HttpResponse<String> response = request("X-Forwarded-Proto", "not_a_scheme");

        assertThat(response.statusCode(), is(Status.BAD_REQUEST_400.code()));
        assertThat(response.headers().firstValue(DIRECT_HANDLER_HOST_HEADER.lowerCase()),
                   is(Optional.of(uri.getRawAuthority())));
        assertThat(response.body(), is("handled"));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void nonNumericXForwardedPortReturnsBadRequest() throws IOException, InterruptedException {
        HttpResponse<String> response = request("X-Forwarded-Port", "not-a-port");

        assertThat(response.statusCode(), is(Status.BAD_REQUEST_400.code()));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void incompleteQuotedForwardedValueReturnsBadRequest() throws IOException, InterruptedException {
        HttpResponse<String> response = request("Forwarded", "for=client.example;proto=https;ext=\"dangling\\");

        assertThat(response.statusCode(), is(Status.BAD_REQUEST_400.code()));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void nonNumericForwardedHostPortReturnsBadRequest() throws IOException, InterruptedException {
        HttpResponse<String> response = request("Forwarded",
                                                "for=client.example;host=\"public.example:not-a-port\";proto=https");

        assertThat(response.statusCode(), is(Status.BAD_REQUEST_400.code()));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void emptyForwardedHostPortIsAccepted() throws IOException, InterruptedException {
        HttpResponse<String> response = request("Forwarded",
                                                "for=client.example;host=\"public.example:\";proto=https");

        assertThat(response.statusCode(), is(Status.OK_200.code()));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void outOfRangeForwardedHostPortReturnsBadRequest() throws IOException, InterruptedException {
        HttpResponse<String> response = request("Forwarded",
                                                "for=client.example;host=\"public.example:65536\";proto=https");

        assertThat(response.statusCode(), is(Status.BAD_REQUEST_400.code()));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void rejectedRequestWithOpenBodyResetsStream(Http2TestClient testClient) {
        try (Http2TestConnection connection = testClient.createConnection()) {
            writeRejectedOpenBodyHeaders(connection, 1);
            assertRejectedResponseAndReset(connection, 1);

            BufferData lateData = BufferData.create("ignored");
            Http2FrameData lateDataFrame = new Http2FrameData(
                    Http2FrameHeader.create(lateData.available(),
                                            Http2FrameTypes.DATA,
                                            Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                            1),
                    lateData);
            connection.writer().writeData(lateDataFrame, FlowControl.Outbound.NOOP);

            connection.request(3, Method.GET, "/", WritableHeaders.create(), BufferData.empty());
            Http2FrameData responseHeaders = connection.assertNextFrame(Http2FrameType.HEADERS, TIMEOUT);
            assertThat(Http2Headers.create(null, responseDynamicTable, responseHuffman, responseHeaders).status(),
                       is(Status.OK_200));
            Http2FrameData responseData = connection.assertNextFrame(Http2FrameType.DATA, TIMEOUT);
            assertThat(responseData.data().readString(responseData.data().available()), is("ok"));
        }
    }

    @Test
    void locallyResetStreamOverflowClosesConnectionAfterReset(Http2TestClient testClient) {
        try (Http2TestConnection connection = testClient.createConnection()) {
            writeRejectedOpenBodyHeaders(connection, 1);
            assertRejectedResponseAndReset(connection, 1);
            writeRejectedOpenBodyHeaders(connection, 3);
            assertRejectedResponseAndReset(connection, 3);

            writeRejectedOpenBodyHeaders(connection, 5);
            Http2FrameData frame = connection.assertNextFrame(Http2FrameType.GO_AWAY, TIMEOUT);
            Http2GoAway goAway = Http2GoAway.create(frame.data());
            assertThat(goAway.errorCode(), is(Http2ErrorCode.ENHANCE_YOUR_CALM));
            assertThat(goAway.lastStreamId(), is(3));
        }
    }

    @Test
    void rejectedHeadRequestDoesNotSendData(Http2TestClient testClient) {
        try (Http2TestConnection connection = testClient.createConnection()) {
            WritableHeaders<?> requestHeaders = WritableHeaders.create()
                    .add(HeaderValues.create("X-Forwarded-For", "client.example, proxy.example"))
                    .add(HeaderValues.create("X-Forwarded-Proto", "not_a_scheme"));
            Http2Headers headers = Http2Headers.create(requestHeaders);
            headers.method(Method.HEAD);
            headers.path("/");
            headers.scheme(connection.clientUri().scheme());
            headers.authority(connection.clientUri().authority());
            connection.writer().writeHeaders(headers,
                                             1,
                                             Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS
                                                                                  | Http2Flag.END_OF_STREAM),
                                             FlowControl.Outbound.NOOP);

            Http2FrameData responseFrame;
            do {
                responseFrame = connection.awaitNextFrame(TIMEOUT);
                assertThat("Timed out waiting for HEAD response", responseFrame, notNullValue());
                assertThat("Unexpected connection shutdown",
                           responseFrame.header().type(),
                           not(Http2FrameType.GO_AWAY));
            } while (responseFrame.header().streamId() == 0);
            assertThat(responseFrame.header().streamId(), is(1));
            assertThat(responseFrame.header().type(), is(Http2FrameType.HEADERS));
            Http2Headers responseHeaders =
                    Http2Headers.create(null, responseDynamicTable, responseHuffman, responseFrame);
            assertThat(responseHeaders.status(), is(Status.BAD_REQUEST_400));
            assertThat(responseHeaders.httpHeaders().first(HeaderNames.CONTENT_LENGTH), is(Optional.of("7")));
            assertThat(responseFrame.header().flags(Http2FrameTypes.HEADERS).endOfStream(), is(true));

            connection.request(3, Method.GET, "/", WritableHeaders.create(), BufferData.empty());
            Http2FrameData nextResponse = connection.assertNextFrame(Http2FrameType.HEADERS, TIMEOUT);
            assertThat(Http2Headers.create(null, responseDynamicTable, responseHuffman, nextResponse).status(),
                       is(Status.OK_200));
        }
    }

    private void writeRejectedOpenBodyHeaders(Http2TestConnection connection, int streamId) {
        WritableHeaders<?> requestHeaders = WritableHeaders.create()
                .add(HeaderValues.create("X-Forwarded-For", "client.example, proxy.example"))
                .add(HeaderValues.create("X-Forwarded-Proto", "not_a_scheme"));
        Http2Headers headers = Http2Headers.create(requestHeaders);
        headers.method(Method.GET);
        headers.path("/");
        headers.scheme(connection.clientUri().scheme());
        headers.authority(connection.clientUri().authority());
        connection.writer().writeHeaders(headers,
                                         streamId,
                                         Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                         FlowControl.Outbound.NOOP);
    }

    private void assertRejectedResponseAndReset(Http2TestConnection connection, int streamId) {
        boolean responseHeadersSeen = false;
        boolean responseDataSeen = false;
        while (true) {
            Http2FrameData frame = connection.awaitNextFrame(TIMEOUT);
            assertThat("Timed out waiting for rejected-stream response", frame, notNullValue());
            assertThat("Unexpected connection shutdown", frame.header().type(), not(Http2FrameType.GO_AWAY));
            if (frame.header().streamId() == 0) {
                continue;
            }
            assertThat("Unexpected response stream", frame.header().streamId(), is(streamId));
            assertThat("Unexpected response frame",
                       frame.header().type(),
                       anyOf(is(Http2FrameType.HEADERS),
                             is(Http2FrameType.DATA),
                             is(Http2FrameType.RST_STREAM)));
            if (frame.header().type() == Http2FrameType.HEADERS) {
                Http2Headers responseHeaders = Http2Headers.create(null, responseDynamicTable, responseHuffman, frame);
                assertThat(responseHeaders.status(), is(Status.BAD_REQUEST_400));
                responseHeadersSeen = true;
            } else if (frame.header().type() == Http2FrameType.DATA) {
                assertThat(frame.data().readString(frame.data().available()), is("handled"));
                responseDataSeen = true;
            } else {
                assertThat(Http2RstStream.create(frame.data()).errorCode(), is(Http2ErrorCode.NO_ERROR));
                assertThat("Response headers were not sent before reset", responseHeadersSeen, is(true));
                assertThat("Response data was not sent before reset", responseDataSeen, is(true));
                return;
            }
        }
    }

    private HttpResponse<String> request(String headerName, String headerValue) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder()
                                   .timeout(Duration.ofSeconds(5))
                                   .uri(uri)
                                   .header("X-Forwarded-For", "client.example, proxy.example")
                                   .header(headerName, headerValue)
                                   .GET()
                                   .build(),
                           HttpResponse.BodyHandlers.ofString());
    }
}
