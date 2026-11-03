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

package io.helidon.common.testing.http.junit5;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import io.helidon.http.HeaderNames;
import io.helidon.http.Method;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class SocketHttpClientTest {
    private static final List<String> LINE_ENDINGS = List.of("\r\n", "\n");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void receivePreservesEmptyResponse() throws Exception {
        for (String eol : LINE_ENDINGS) {
            String response = "HTTP/1.1 200 OK" + eol + "Content-Length: 0" + eol + eol;

            assertReceivedResponse(response, false, SocketHttpClient::receive);
        }
    }

    @Test
    void receivePreservesContentLengthResponse() throws Exception {
        String body = "first\r\nsecond\nlast";
        for (String eol : LINE_ENDINGS) {
            String response = "HTTP/1.1 200 OK" + eol
                    + "Content-Length: " + body.length() + eol + eol + body;

            assertReceivedResponse(response, false, SocketHttpClient::receive);
        }
    }

    @Test
    void receivePreservesChunkedResponse() throws Exception {
        String body = "first\r\nsecond\nlast";
        for (String eol : LINE_ENDINGS) {
            String response = "HTTP/1.1 200 OK" + eol
                    + "Transfer-Encoding: chunked" + eol + eol
                    + Integer.toHexString(body.length()) + eol + body + eol
                    + "0" + eol + eol;

            assertReceivedResponse(response, false, SocketHttpClient::receive);
        }
    }

    @Test
    void receivePreservesCloseDelimitedResponse() throws Exception {
        for (String eol : LINE_ENDINGS) {
            String response = "HTTP/1.1 200 OK" + eol
                    + "Connection: close" + eol + eol
                    + "first\r\nsecond\nunterminated";

            assertReceivedResponse(response, true, SocketHttpClient::receive);
        }
    }

    @Test
    void sendAndReceivePreservesResponse() throws Exception {
        List<Function<SocketHttpClient, String>> requests = List.of(
                client -> client.sendAndReceive(Method.GET, ""),
                client -> client.sendAndReceive(Method.GET, "/test", ""),
                client -> client.sendAndReceive(Method.GET, "/test", "", List.of("Accept: text/plain")));

        for (String eol : LINE_ENDINGS) {
            String response = "HTTP/1.1 200 OK" + eol + "Content-Length: 4" + eol + eol + "body";
            for (Function<SocketHttpClient, String> request : requests) {
                assertReceivedResponse(response, false, request);
            }
        }
    }

    @Test
    void responseHelpersAcceptBothLineEndingsAndPreserveBody() {
        String body = "first\r\n\r\nnot: a-header\n\nunterminated";
        for (String eol : LINE_ENDINGS) {
            String response = "HTTP/1.1 200 OK" + eol
                    + "Content-Type: text/plain" + eol
                    + "Content-Length: " + body.length() + eol + eol + body;

            var headers = SocketHttpClient.headersFromResponse(response);
            assertThat(headers.size(), is(2));
            assertThat(headers.get(HeaderNames.CONTENT_TYPE).values(), is("text/plain"));
            assertThat(headers.get(HeaderNames.CONTENT_LENGTH).values(), is(Integer.toString(body.length())));
            assertThat(SocketHttpClient.entityFromResponse(response, true), is(body));
            assertThat(SocketHttpClient.entityFromResponse(response, false), is(body));
        }
    }

    private static void assertReceivedResponse(String response,
                                               boolean closeOutput,
                                               Function<SocketHttpClient, String> receive) throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (var server = new ServerSocket(0, 1, loopback);
             var client = SocketHttpClient.create(loopback.getHostAddress(), server.getLocalPort(), TIMEOUT)) {
            server.setSoTimeout((int) TIMEOUT.toMillis());
            client.connect();
            try (Socket peer = server.accept()) {
                peer.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
                peer.getOutputStream().flush();
                if (closeOutput) {
                    peer.shutdownOutput();
                }

                assertThat(receive.apply(client), is(response));
            }
        }
    }
}
