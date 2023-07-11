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

package io.helidon.nima.webclient.http1;

import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http1HeadersParser;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.http.media.EntityReader;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.http.media.MediaContextConfig;
import io.helidon.nima.webclient.api.ClientConnection;
import io.helidon.nima.webclient.api.HttpClientResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ClientRequestImplTest {
    public static final String VALID_HEADER_VALUE = "Valid-Header-Value";
    public static final String VALID_HEADER_NAME = "Valid-Header-Name";
    public static final String BAD_HEADER_PATH = "/badHeader";
    public static final String HEADER_NAME_VALUE_DELIMETER = "->";
    private static final Http.HeaderValue REQ_CHUNKED_HEADER = Http.Header.create(
            Http.Header.create("X-Req-Chunked"), "true");
    private static final Http.HeaderValue REQ_EXPECT_100_HEADER_NAME = Http.Header.create(
            Http.Header.create("X-Req-Expect100"), "true");
    private static final Http.HeaderName REQ_CONTENT_LENGTH_HEADER_NAME = Http.Header.create("X-Req-ContentLength");
    private static final Http.HeaderName BAD_HEADER_NAME = Http.Header.create("Bad-Header");
    private static final long NO_CONTENT_LENGTH = -1L;
    private static final Http1Client client = Http1Client.create();
    private static final int dummyPort = 1234;

    @Test
    void testMaxHeaderSizeFail() {
        Http1Client client = Http1Client.create(builder -> builder.protocolConfig(pc -> pc.maxHeaderSize(15)));

        validateFailedResponse(client, new FakeHttp1ClientConnection(), "Header size exceeded");
    }

    @Test
    void testMaxHeaderSizeSuccess() {
        Http1Client client = Http1Client.builder()
                .protocolConfig(pc -> pc.maxHeaderSize(500))
                .build();

        validateSuccessfulResponse(client, new FakeHttp1ClientConnection());
    }

    @Test
    void testMaxStatusLineLengthFail() {
        Http1Client client = Http1Client.builder()
                .protocolConfig(it -> it.maxStatusLineLength(1))
                .build();
        validateFailedResponse(client, new FakeHttp1ClientConnection(), "HTTP Response did not contain HTTP status line");
    }

    @Test
    void testMaxHeaderLineLengthSuccess() {
        Http1Client client = Http1Client.builder()
                .protocolConfig(it -> it.maxStatusLineLength(20))
                .build();
        validateSuccessfulResponse(client, new FakeHttp1ClientConnection());
    }

    @Test
    void testMediaContext() {
        Http1Client client = Http1Client.builder()
                .mediaContext(new CustomizedMediaContext())
                .build();
        validateSuccessfulResponse(client, new FakeHttp1ClientConnection());
    }

    @Test
    void testChunk() {
        String[] requestEntityParts = {"First", "Second", "Third"};

        Http1ClientRequest request = getHttp1ClientRequest(Http.Method.PUT, "/test");
        request.connection(new FakeHttp1ClientConnection());
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, String.join("", requestEntityParts));
    }

    @Test
    void testNoChunk() {
        String[] requestEntityParts = {"First"};
        long contentLength = requestEntityParts[0].length();

        Http1ClientRequest request = getHttp1ClientRequest(Http.Method.PUT, "/test")
                .header(Http.Header.CONTENT_LENGTH, String.valueOf(contentLength));
        request.connection(new FakeHttp1ClientConnection());
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, false, contentLength, requestEntityParts[0]);
    }

    @Test
    void testForcedChunkNoContentLength() {
        String[] requestEntityParts = {"First"};

        Http1ClientRequest request = getHttp1ClientRequest(Http.Method.PUT, "/test");
        request.connection(new FakeHttp1ClientConnection());
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, requestEntityParts[0]);
    }

    @Test
    void testForcedChunkTransferEncodingChunked() {
        String[] requestEntityParts = {"First"};

        Http1ClientRequest request = getHttp1ClientRequest(Http.Method.PUT, "/test")
                .header(Http.HeaderValues.TRANSFER_ENCODING_CHUNKED);
        request.connection(new FakeHttp1ClientConnection());
        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, requestEntityParts[0]);
    }

    @Test
    void testExpect100() {
        String[] requestEntityParts = {"First", "Second", "Third"};

        Http1Client client = Http1Client.builder()
                .sendExpect100Continue(true)
                .build();
        Http1ClientRequest request = client.put("http://localhost:" + dummyPort + "/test");
        request.connection(new FakeHttp1ClientConnection());

        Http1ClientResponse response = getHttp1ClientResponseFromOutputStream(request, requestEntityParts);

        validateChunkTransfer(response, true, NO_CONTENT_LENGTH, String.join("", requestEntityParts));
        assertThat(response.headers(), hasHeader(REQ_EXPECT_100_HEADER_NAME));
    }

    // validates that HEAD is not allowed with entity payload
    @Test
    void testHeadMethod() {
        String url = "http://localhost:" + dummyPort + "/test";
        ClientConnection http1ClientConnection = new FakeHttp1ClientConnection();
        assertThrows(IllegalArgumentException.class, () ->
                client.head(url).connection(http1ClientConnection).submit("Foo Bar"));
        assertThrows(IllegalArgumentException.class, () ->
                client.head(url).connection(http1ClientConnection).outputStream(it -> {
                    it.write("Foo Bar".getBytes(StandardCharsets.UTF_8));
                    it.close();
                }));
        client.head(url).connection(http1ClientConnection).request();
        http1ClientConnection.close();
    }

    @Test
    void testSkipUrlEncoding() {
        //Fill with chars which should be encoded
        Http1ClientRequest request = getHttp1ClientRequest(Http.Method.PUT, "/ěščžř")
                .queryParam("specialChar+", "someValue,").fragment("someFragment,");
        URI uri = request.resolvedUri().toUri();
        assertThat(uri.getRawPath(), is("/%C4%9B%C5%A1%C4%8D%C5%BE%C5%99"));
        assertThat(uri.getRawQuery(), is("specialChar%2B=someValue%2C"));
        assertThat(uri.getRawFragment(), is("someFragment%2C"));

        request = request.skipUriEncoding();
        uri = request.resolvedUri().toUri();
        assertThat(uri.getRawPath(), is("/ěščžř"));
        assertThat(uri.getRawQuery(), is("specialChar+=someValue,"));
        assertThat(uri.getRawFragment(), is("someFragment,"));
    }

    @ParameterizedTest
    @MethodSource("relativeUris")
    void testRelativeUris(boolean relativeUris, boolean outputStream, String requestUri, String expectedUriStart) {
        Http1Client client = Http1Client.builder().relativeUris(relativeUris).build();
        FakeHttp1ClientConnection connection = new FakeHttp1ClientConnection();
        Http1ClientRequest request = client.put(requestUri);
        request.connection(connection);
        HttpClientResponse response;
        if (outputStream) {
            response = getHttp1ClientResponseFromOutputStream(request, new String[] {"Sending Something"});
        } else {
            response = request.submit("Sending Something");
        }

        assertThat(response.status(), is(Http.Status.OK_200));
        StringTokenizer st = new StringTokenizer(connection.getPrologue(), " ");
        // skip method part
        st.nextToken();
        // Validate URI part
        assertThat(st.nextToken(), startsWith(expectedUriStart));
    }

    @ParameterizedTest
    @MethodSource("headerValues")
    void testHeaderValues(List<String> headerValues, boolean expectsValid) {
        Http1ClientRequest request = client.get("http://localhost:" + dummyPort + "/test");
        request.header(Http.Header.create(Http.Header.create("HeaderName"), headerValues));
        request.connection(new FakeHttp1ClientConnection());
        if (expectsValid) {
            HttpClientResponse response = request.request();
            assertThat(response.status(), is(Http.Status.OK_200));
        } else {
            assertThrows(IllegalArgumentException.class, () -> request.request());
        }
    }

    @ParameterizedTest
    @MethodSource("headers")
    void testHeaders(Http.HeaderValue header, boolean expectsValid) {
        Http1ClientRequest request = client.get("http://localhost:" + dummyPort + "/test");
        request.connection(new FakeHttp1ClientConnection());
        request.header(header);
        if (expectsValid) {
            HttpClientResponse response = request.request();
            assertThat(response.status(), is(Http.Status.OK_200));
        } else {
            assertThrows(IllegalArgumentException.class, () -> request.request());
        }
    }

    @ParameterizedTest
    @MethodSource("headers")
    void testDisableHeaderValidation(Http.HeaderValue header, boolean expectsValid) {
        Http1Client clientWithNoHeaderValidation = Http1Client.builder()
                .protocolConfig(it -> it.validateHeaders(false))
                .build();
        Http1ClientRequest request = clientWithNoHeaderValidation.put("http://localhost:" + dummyPort + "/test");
        request.header(header);
        request.connection(new FakeHttp1ClientConnection());
        HttpClientResponse response = request.submit("Sending Something");
        if (expectsValid) {
            assertThat(response.status(), is(Http.Status.OK_200));
        } else {
            assertThat(response.status(), is(Http.Status.BAD_REQUEST_400));
        }
    }

    @ParameterizedTest
    @MethodSource("responseHeaders")
    void testHeadersFromResponse(String headerName, String headerValue, boolean expectsValid) {
        Http1ClientRequest request = client.get("http://localhost:" + dummyPort + BAD_HEADER_PATH);
        request.connection(new FakeHttp1ClientConnection());
        String headerNameAndValue = headerName + HEADER_NAME_VALUE_DELIMETER + headerValue;
        if (expectsValid) {
            HttpClientResponse response = request.submit(headerNameAndValue);
            assertThat(response.status(), is(Http.Status.OK_200));
            String responseHeaderValue = response.headers().get(Http.Header.create(headerName)).values();
            assertThat(responseHeaderValue, is(headerValue.trim()));
        } else {
            assertThrows(IllegalArgumentException.class, () -> request.submit(headerNameAndValue));
        }
    }

    @ParameterizedTest
    @MethodSource("responseHeadersForDisabledValidation")
    void testDisableValidationForHeadersFromResponse(String headerName, String headerValue) {
        Http1Client clientWithNoHeaderValidation = Http1Client.builder()
                .protocolConfig(it -> it.validateHeaders(false))
                .build();
        Http1ClientRequest request = clientWithNoHeaderValidation.put("http://localhost:" + dummyPort + BAD_HEADER_PATH);
        request.connection(new FakeHttp1ClientConnection());
        Http1ClientResponse response = request.submit(headerName + HEADER_NAME_VALUE_DELIMETER + headerValue);
        assertThat(response.status(), is(Http.Status.OK_200));
        String responseHeaderValue = response.headers().get(Http.Header.create(headerName)).values();
        assertThat(responseHeaderValue, is(headerValue.trim()));
    }

    private static void validateSuccessfulResponse(Http1Client client, ClientConnection connection) {
        String requestEntity = "Sending Something";
        Http1ClientRequest request = client.put("http://localhost:" + dummyPort + "/test");
        if (connection != null) {
            request.connection(connection);
        }
        Http1ClientResponse response = request.submit(requestEntity);

        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(response.entity().as(String.class), is(requestEntity));
    }

    private static void validateFailedResponse(Http1Client client, ClientConnection connection, String errorMessage) {
        String requestEntity = "Sending Something";
        Http1ClientRequest request = client.put("http://localhost:" + dummyPort + "/test");
        if (connection != null) {
            request.connection(connection);
        }
        IllegalStateException ie = assertThrows(IllegalStateException.class, () -> request.submit(requestEntity));
        if (errorMessage != null) {
            assertThat(ie.getMessage(), containsString(errorMessage));
        }
    }

    private static void validateChunkTransfer(Http1ClientResponse response, boolean chunked, long contentLength, String entity) {
        assertThat(response.status(), is(Http.Status.OK_200));
        if (contentLength == NO_CONTENT_LENGTH) {
            assertThat(response.headers(), noHeader(REQ_CONTENT_LENGTH_HEADER_NAME));
        } else {
            assertThat(response.headers(), hasHeader(REQ_CONTENT_LENGTH_HEADER_NAME, String.valueOf(contentLength)));
        }
        if (chunked) {
            assertThat(response.headers(), hasHeader(REQ_CHUNKED_HEADER));
        } else {
            assertThat(response.headers(), noHeader(REQ_CHUNKED_HEADER.headerName()));
        }
        String responseEntity = response.entity().as(String.class);
        assertThat(responseEntity, is(entity));
    }

    private static Http1ClientRequest getHttp1ClientRequest(Http.Method method, String uriPath) {
        return client.method(method).uri("http://localhost:" + dummyPort + uriPath);
    }

    private static Http1ClientResponse getHttp1ClientResponseFromOutputStream(Http1ClientRequest request,
                                                                              String[] requestEntityParts) {
        Http1ClientResponse response = request.outputStream(it -> {
            for (String r : requestEntityParts) {
                it.write(r.getBytes(StandardCharsets.UTF_8));
            }
            it.close();
        });
        return response;
    }

    private static Stream<Arguments> relativeUris() {
        return Stream.of(
                // OutputStream (chunk request)
                arguments(false, true, "http://www.dummy.com/test", "http://www.dummy.com:80/"),
                arguments(false, true, "http://www.dummy.com:1111/test", "http://www.dummy.com:1111/"),
                arguments(false, true, "https://www.dummy.com/test", "https://www.dummy.com:443/"),
                arguments(false, true, "https://www.dummy.com:1111/test", "https://www.dummy.com:1111/"),
                arguments(true, true, "http://www.dummy.com/test", "/test"),
                arguments(true, true, "http://www.dummy.com:1111/test", "/test"),
                arguments(true, true, "https://www.dummy.com/test", "/test"),
                arguments(true, true, "https://www.dummy.com:1111/test", "/test"),
                // non-OutputStream (single entity request)
                arguments(false, false, "http://www.dummy.com/test", "http://www.dummy.com:80/"),
                arguments(false, false, "http://www.dummy.com:1111/test", "http://www.dummy.com:1111/"),
                arguments(false, false, "https://www.dummy.com/test", "https://www.dummy.com:443/"),
                arguments(false, false, "https://www.dummy.com:1111/test", "https://www.dummy.com:1111/"),
                arguments(true, false, "http://www.dummy.com/test", "/test"),
                arguments(true, false, "http://www.dummy.com:1111/test", "/test"),
                arguments(true, false, "https://www.dummy.com/test", "/test"),
                arguments(true, false, "https://www.dummy.com:1111/test", "/test"));
    }

    private static Stream<Arguments> headerValues() {
        return Stream.of(
                // Valid header values
                arguments(Arrays.asList("Header Value"), true),
                arguments(Arrays.asList("HeaderValue1", "Header\u0080Value\u00ff2"), true),
                arguments(Arrays.asList("HeaderName1\u0009", "Header=Value2"), true),
                // Invalid header values
                arguments(Arrays.asList(" HeaderValue"), false),
                arguments(Arrays.asList("HeaderValue1", "Header\u007fValue"), false),
                arguments(Arrays.asList("HeaderValue1\r\n", "HeaderValue2"), false)
        );
    }

    private static Stream<Arguments> headers() {
        return Stream.of(
                // Valid headers
                arguments(Http.HeaderValues.ACCEPT_RANGES_BYTES, true),
                arguments(Http.HeaderValues.CONNECTION_KEEP_ALIVE, true),
                arguments(Http.HeaderValues.CONTENT_TYPE_TEXT_PLAIN, true),
                arguments(Http.HeaderValues.ACCEPT_TEXT, true),
                arguments(Http.HeaderValues.CACHE_NO_CACHE, true),
                arguments(Http.HeaderValues.TE_TRAILERS, true),
                arguments(Http.Header.create(Http.Header.create("!#$Custom~%&\'*Header+^`|"), "!Header\tValue~"), true),
                arguments(Http.Header.create(Http.Header.create("Custom_0-9_a-z_A-Z_Header"),
                                             "\u0080Header Value\u00ff"), true),
                // Invalid headers
                arguments(Http.Header.create(Http.Header.create(VALID_HEADER_NAME), "H\u001ceaderValue1"), false),
                arguments(Http.Header.create(Http.Header.create(VALID_HEADER_NAME),
                                             "HeaderValue1, Header\u007fValue"), false),
                arguments(Http.Header.create(Http.Header.create(VALID_HEADER_NAME),
                                             "HeaderValue1\u001f, HeaderValue2"), false),
                arguments(Http.Header.create(Http.Header.create("Header\u001aName"), VALID_HEADER_VALUE), false),
                arguments(Http.Header.create(Http.Header.create("Header\u000EName"), VALID_HEADER_VALUE), false),
                arguments(Http.Header.create(Http.Header.create("HeaderName\r\n"), VALID_HEADER_VALUE), false),
                arguments(Http.Header.create(Http.Header.create("HeaderName\u00FF\u0124"), VALID_HEADER_VALUE), false),
                arguments(Http.Header.create(Http.Header.create("(Header:Name)"), VALID_HEADER_VALUE), false),
                arguments(Http.Header.create(Http.Header.create("<Header?Name>"), VALID_HEADER_VALUE), false),
                arguments(Http.Header.create(Http.Header.create("{Header=Name}"), VALID_HEADER_VALUE), false),
                arguments(Http.Header.create(Http.Header.create("\"HeaderName\""), VALID_HEADER_VALUE), false),
                arguments(Http.Header.create(Http.Header.create("[\\HeaderName]"), VALID_HEADER_VALUE), false),
                arguments(Http.Header.create(Http.Header.create("@Header,Name;"), VALID_HEADER_VALUE), false)
        );
    }

    private static Stream<Arguments> responseHeaders() {
        return Stream.of(
                // Invalid header names
                arguments("Header\u001aName", VALID_HEADER_VALUE, false),
                arguments("Header\u000EName", VALID_HEADER_VALUE, false),
                arguments("HeaderName\r\n", VALID_HEADER_VALUE, false),
                arguments("(Header:Name)", VALID_HEADER_VALUE, false),
                arguments("<Header?Name>", VALID_HEADER_VALUE, false),
                arguments("{Header=Name}", VALID_HEADER_VALUE, false),
                arguments("\"HeaderName\"", VALID_HEADER_VALUE, false),
                arguments("[\\HeaderName]", VALID_HEADER_VALUE, false),
                arguments("@Header,Name;", VALID_HEADER_VALUE, false),
                // Valid header names
                arguments("!#$Custom~%&\'*Header+^`|", VALID_HEADER_VALUE, true),
                arguments("Custom_0-9_a-z_A-Z_Header", VALID_HEADER_VALUE, true),
                // Valid header values
                arguments(VALID_HEADER_NAME, "Header Value", true),
                arguments(VALID_HEADER_NAME, "HeaderValue1\u0009, Header=Value2", true),
                arguments(VALID_HEADER_NAME, "Header\tValue", true),
                arguments(VALID_HEADER_NAME, " Header Value ", true),
                // Invalid header values
                arguments(VALID_HEADER_NAME, "H\u001ceaderValue1", false),
                arguments(VALID_HEADER_NAME, "HeaderValue1, Header\u007fValue", false),
                arguments(VALID_HEADER_NAME, "HeaderValue1\u001f, HeaderValue2", false)
        );
    }

    private static Stream<Arguments> responseHeadersForDisabledValidation() {
        return Stream.of(
                // Invalid header names
                arguments("Header\u001aName", VALID_HEADER_VALUE, false),
                arguments("Header\u000EName", VALID_HEADER_VALUE, false),
                arguments("{Header=Name}", VALID_HEADER_VALUE, false),
                arguments("\"HeaderName\"", VALID_HEADER_VALUE, false),
                arguments("[\\HeaderName]", VALID_HEADER_VALUE, false),
                arguments("@Header,Name;", VALID_HEADER_VALUE, false),
                // Valid header names
                arguments("!#$Custom~%&\'*Header+^`|", VALID_HEADER_VALUE, true),
                arguments("Custom_0-9_a-z_A-Z_Header", VALID_HEADER_VALUE, true),
                // Valid header values
                arguments(VALID_HEADER_NAME, "Header Value", true),
                arguments(VALID_HEADER_NAME, "HeaderValue1\u0009, Header=Value2", true),
                arguments(VALID_HEADER_NAME, "Header\tValue", true),
                arguments(VALID_HEADER_NAME, " Header Value ", true),
                // Invalid header values
                arguments(VALID_HEADER_NAME, "H\u001ceaderValue1", false),
                arguments(VALID_HEADER_NAME, "HeaderValue1, Header\u007fValue", false),
                arguments(VALID_HEADER_NAME, "HeaderValue1\u001f, HeaderValue2", false)
        );
    }

    private static class FakeHttp1ClientConnection implements ClientConnection {
        private final DataReader clientReader;
        private final DataWriter clientWriter;
        private final DataReader serverReader;
        private final DataWriter serverWriter;
        private Throwable serverException;
        private ExecutorService webServerEmulator;
        private String prologue;

        FakeHttp1ClientConnection() {
            ArrayBlockingQueue<byte[]> serverToClient = new ArrayBlockingQueue<>(1024);
            ArrayBlockingQueue<byte[]> clientToServer = new ArrayBlockingQueue<>(1024);

            this.clientReader = reader(serverToClient);
            this.clientWriter = writer(clientToServer);
            this.serverReader = reader(clientToServer);
            this.serverWriter = writer(serverToClient);
        }

        @Override
        public DataReader reader() {
            return clientReader;
        }

        @Override
        public DataWriter writer() {
            return clientWriter;
        }

        @Override
        public void release() {
        }

        @Override
        public void close() {
            webServerEmulator.shutdownNow();
        }

        @Override
        public String channelId() {
            return null;
        }

        @Override
        public void readTimeout(Duration readTimeout) {
            //NOOP
        }

        // This will be used for testing the element of Prologue
        String getPrologue() {
            return prologue;
        }

        private DataWriter writer(ArrayBlockingQueue<byte[]> queue) {
            return new DataWriter() {
                @Override
                public void write(BufferData... buffers) {
                    writeNow(buffers);
                }

                @Override
                public void write(BufferData buffer) {
                    writeNow(buffer);
                }

                @Override
                public void writeNow(BufferData... buffers) {
                    for (BufferData buffer : buffers) {
                        writeNow(buffer);
                    }
                }

                @Override
                public void writeNow(BufferData buffer) {
                    if (serverException != null) {
                        throw new IllegalStateException("Server exception", serverException);
                    }
                    if (webServerEmulator == null) {
                        webServerEmulator = Executors.newFixedThreadPool(1);
                        webServerEmulator.submit(() -> {
                            try {
                                webServerHandle();
                            } catch (Throwable t) {
                                serverException = t;
                                throw t;
                            }
                        });
                    }

                    byte[] bytes = new byte[buffer.available()];
                    buffer.read(bytes);
                    try {
                        queue.put(bytes);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException("Thread interrupted", e);
                    }
                }
            };
        }

        private DataReader reader(ArrayBlockingQueue<byte[]> queue) {
            return new DataReader(() -> {
                if (serverException != null) {
                    throw new IllegalStateException("Server exception", serverException);
                }
                byte[] data;
                try {
                    data = queue.take();
                } catch (InterruptedException e) {
                    throw new IllegalArgumentException("Thread interrupted", e);
                }
                if (data.length == 0) {
                    return null;
                }
                return data;
            });
        }

        private void webServerHandle() {
            BufferData entity = BufferData.growing(128);

            // Read prologue
            int lineLength = serverReader.findNewLine(16384);
            if (lineLength > 0) {
                prologue = serverReader.readAsciiString(lineLength);
                serverReader.skip(2); // skip CRLF
            }

            boolean requestFailed = false;
            // Read Headers
            WritableHeaders<?> reqHeaders = null;
            try {
                reqHeaders = Http1HeadersParser.readHeaders(serverReader, 16384, false);
                for (Iterator<Http.HeaderValue> it = reqHeaders.iterator(); it.hasNext(); ) {
                    Http.HeaderValue header = it.next();
                    header.validate();
                }
            } catch (IllegalArgumentException e) {
                requestFailed = true;
            }

            int entitySize = 0;
            if (!requestFailed) {
                if (reqHeaders.contains(Http.HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
                    // Send 100-Continue if requested
                    if (reqHeaders.contains(Http.HeaderValues.EXPECT_100)) {
                        serverWriter.write(
                                BufferData.create("HTTP/1.1 100 Continue\r\n".getBytes(StandardCharsets.UTF_8)));
                    }

                    // Assemble the entity from the chunks
                    while (true) {
                        String hex = serverReader.readLine();
                        int chunkLength = Integer.parseUnsignedInt(hex, 16);
                        if (chunkLength == 0) {
                            serverReader.readLine();
                            break;
                        }
                        BufferData chunkData = serverReader.readBuffer(chunkLength);
                        entity.write(chunkData);
                        serverReader.skip(2);
                        entitySize += chunkLength;
                    }
                } else if (reqHeaders.contains(Http.Header.CONTENT_LENGTH)) {
                    entitySize = reqHeaders.get(Http.Header.CONTENT_LENGTH).value(int.class);
                    if (entitySize > 0) {
                        entity.write(serverReader.getBuffer(entitySize));
                    }
                }
            }

            WritableHeaders<?> resHeaders = WritableHeaders.create();
            resHeaders.add(Http.HeaderValues.CONNECTION_KEEP_ALIVE);

            if (reqHeaders != null) {
                // Send headers that can be validated if Expect-100-Continue, Content_Length, and Chunked request headers exist
                if (reqHeaders.contains(Http.HeaderValues.EXPECT_100)) {
                    resHeaders.set(REQ_EXPECT_100_HEADER_NAME);
                }
                if (reqHeaders.contains(Http.Header.CONTENT_LENGTH)) {
                    resHeaders.set(REQ_CONTENT_LENGTH_HEADER_NAME, reqHeaders.get(Http.Header.CONTENT_LENGTH).value());
                }
                if (reqHeaders.contains(Http.HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
                    resHeaders.set(REQ_CHUNKED_HEADER);
                }
            }

            // if prologue contains "/badHeader" path, send back the entity (name and value delimited by ->) as a header
            if (getPrologue().contains(BAD_HEADER_PATH)) {
                String[] header = entity.readString(entitySize, StandardCharsets.US_ASCII).split(HEADER_NAME_VALUE_DELIMETER);
                resHeaders.add(Http.Header.create(Http.Header.create(header[0]), header[1]));
            }

            String responseMessage = !requestFailed ? "HTTP/1.1 200 OK\r\n" : "HTTP/1.1 400 Bad Request\r\n";
            serverWriter.write(BufferData.create(responseMessage.getBytes(StandardCharsets.UTF_8)));

            // Send the headers
            resHeaders.add(Http.Header.CONTENT_LENGTH, Integer.toString(entitySize));
            BufferData entityBuffer = BufferData.growing(128);
            for (Http.HeaderValue header : resHeaders) {
                header.writeHttp1Header(entityBuffer);
            }
            entityBuffer.write(Bytes.CR_BYTE);
            entityBuffer.write(Bytes.LF_BYTE);
            serverWriter.write(entityBuffer);

            // Send the entity if it exist
            if (entitySize > 0) {
                serverWriter.write(entity);
            }
        }
    }

    private static class CustomizedMediaContext implements MediaContext {
        private final MediaContext delegated = MediaContext.create();

        @Override
        public MediaContextConfig prototype() {
            return delegated.prototype();
        }

        @Override
        public <T> EntityReader<T> reader(GenericType<T> type, Headers headers) {
            return delegated.reader(type, headers);
        }

        @Override
        public <T> EntityWriter<T> writer(GenericType<T> type, Headers requestHeaders, WritableHeaders<?> responseHeaders) {
            EntityWriter<T> impl = delegated.writer(type, requestHeaders, responseHeaders);

            EntityWriter<T> realWriter = new EntityWriter<T>() {
                @Override
                public void write(GenericType<T> type,
                                  T object,
                                  OutputStream outputStream,
                                  Headers requestHeaders,
                                  WritableHeaders<?> responseHeaders) {
                    if (object instanceof String) {
                        @SuppressWarnings("unchecked") final T maxLen5 = (T) ((String) object).substring(0, 5);
                        impl.write(type, maxLen5, outputStream, requestHeaders, responseHeaders);
                    } else {
                        impl.write(type, object, outputStream, requestHeaders, responseHeaders);
                    }
                }

                @Override
                public void write(GenericType<T> type, T object, OutputStream outputStream, WritableHeaders<?> headers) {
                    impl.write(type, object, outputStream, headers);
                }
            };
            return realWriter;
        }

        @Override
        public <T> EntityReader<T> reader(GenericType<T> type, Headers requestHeaders, Headers responseHeaders) {
            return delegated.reader(type, requestHeaders, responseHeaders);
        }

        @Override
        public <T> EntityWriter<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
            return delegated.writer(type, requestHeaders);
        }
    }
}
