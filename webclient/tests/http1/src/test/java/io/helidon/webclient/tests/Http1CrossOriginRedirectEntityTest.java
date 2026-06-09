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

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.http.HeaderNames;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.spi.WebClientService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class Http1CrossOriginRedirectEntityTest {
    private static final String FIRST_HOP_HOST = "127.0.0.1";
    private static final String SECOND_HOP_HOST = "localhost";
    private static final String AUTHORIZATION = "Basic secret";
    private static final String REQUEST_BODY =
            "grant_type=authorization_code&code=live-code&redirect_uri=http%3A%2F%2F127.0.0.1%2Fcallback";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final String BLOCKED_REDIRECT_MESSAGE = "Cross-origin redirect with request entity is disabled.";

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    @Timeout(20)
    void doesNotReplayBufferedEntityAcrossCrossOriginRedirect(int redirectStatus) throws Exception {
        try (SecondHopServer secondHop = new SecondHopServer(InetAddress.getByName(SECOND_HOP_HOST));
             FirstHopServer firstHop = new FirstHopServer(InetAddress.getByName(FIRST_HOP_HOST),
                                                          secondHop.port(),
                                                          true,
                                                          redirectStatus)) {
            Http1Client client = newClient(firstHop.port());
            try {
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                                       () -> client.post("/token")
                                                                       .readTimeout(REQUEST_TIMEOUT)
                                                                       .header(HeaderNames.CONTENT_TYPE,
                                                                               "application/x-www-form-urlencoded")
                                                                       .submit(requestBodyBytes()));
                assertThat(exception.getMessage(), is(BLOCKED_REDIRECT_MESSAGE));

                CapturedRequest originRequest = firstHop.awaitRequest();
                assertThat(originRequest.path(), is("/token"));
                assertThat(originRequest.body(), is(REQUEST_BODY));
                secondHop.assertNoRequest();
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    @Timeout(20)
    void doesNotReplayOutputStreamEntityAcrossCrossOriginRedirect(int redirectStatus) throws Exception {
        try (SecondHopServer secondHop = new SecondHopServer(InetAddress.getByName(SECOND_HOP_HOST));
             FirstHopServer firstHop = new FirstHopServer(InetAddress.getByName(FIRST_HOP_HOST),
                                                          secondHop.port(),
                                                          false,
                                                          redirectStatus)) {
            Http1Client client = newClient(firstHop.port());
            try {
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                               () -> client.post("/token")
                                                                       .sendExpectContinue(true)
                                                                       .readContinueTimeout(REQUEST_TIMEOUT)
                                                                       .readTimeout(REQUEST_TIMEOUT)
                                                                       .header(HeaderNames.CONTENT_TYPE,
                                                                               "application/x-www-form-urlencoded")
                                                                       .outputStream(outputStream -> {
                                                                           outputStream.write(requestBodyBytes());
                                                                           outputStream.close();
                                                                       }));
                assertThat(exception.getMessage(), is(BLOCKED_REDIRECT_MESSAGE));

                CapturedRequest originRequest = firstHop.awaitRequest();
                assertThat(originRequest.path(), is("/token"));
                assertThat(originRequest.header("expect"), is("100-continue"));
                assertThat(originRequest.body(), is(""));
                secondHop.assertNoRequest();
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    @Timeout(20)
    void followsOutputStreamRedirectWithZeroLengthWriteByDefault(int redirectStatus) throws Exception {
        try (SecondHopServer secondHop = new SecondHopServer(InetAddress.getByName(SECOND_HOP_HOST));
             FirstHopServer firstHop = new FirstHopServer(InetAddress.getByName(FIRST_HOP_HOST),
                                                          secondHop.port(),
                                                          false,
                                                          redirectStatus)) {
            Http1Client client = newClient(firstHop.port());
            try {
                try (Http1ClientResponse response = client.post("/token")
                        .maxRedirects(1)
                        .sendExpectContinue(true)
                        .readContinueTimeout(REQUEST_TIMEOUT)
                        .readTimeout(REQUEST_TIMEOUT)
                        .outputStream(outputStream -> {
                            outputStream.write(new byte[0]);
                            outputStream.close();
                        })) {
                    assertThat(response.status().code(), is(200));
                }

                CapturedRequest originRequest = firstHop.awaitRequest();
                assertThat(originRequest.path(), is("/token"));
                assertThat(originRequest.header("expect"), is((String) null));
                assertThat(originRequest.body(), is(""));

                CapturedRequest redirectRequest = secondHop.awaitRequest();
                assertThat(redirectRequest.path(), is("/steal"));
                assertThat(redirectRequest.header("authorization"), is((String) null));
                assertThat(redirectRequest.body(), is(""));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    @Timeout(20)
    void followsOutputStream302RedirectWithMaxRedirectsOne() throws Exception {
        try (SecondHopServer secondHop = new SecondHopServer(InetAddress.getByName(SECOND_HOP_HOST));
             FirstHopServer firstHop = new FirstHopServer(InetAddress.getByName(FIRST_HOP_HOST),
                                                          secondHop.port(),
                                                          true,
                                                          302)) {
            Http1Client client = newClient(firstHop.port());
            try {
                try (Http1ClientResponse response = client.post("/token")
                        .maxRedirects(1)
                        .sendExpectContinue(false)
                        .readTimeout(REQUEST_TIMEOUT)
                        .header(HeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded")
                        .outputStream(outputStream -> {
                            outputStream.write(requestBodyBytes());
                            outputStream.close();
                        })) {
                    assertThat(response.status().code(), is(200));
                }

                CapturedRequest originRequest = firstHop.awaitRequest();
                assertThat(originRequest.path(), is("/token"));
                assertThat(originRequest.body(), is(REQUEST_BODY));

                CapturedRequest redirectRequest = secondHop.awaitRequest();
                assertThat(redirectRequest.path(), is("/steal"));
                assertThat(redirectRequest.header("authorization"), is((String) null));
                assertThat(redirectRequest.body(), is(""));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    @Timeout(20)
    void rejectsSecondExpectContinueOutputStream302RedirectWithMaxRedirectsOne() throws Exception {
        try (SecondHopServer secondHop = new SecondHopServer(InetAddress.getByName(SECOND_HOP_HOST));
             IntermediateRedirectServer intermediateHop =
                     new IntermediateRedirectServer(InetAddress.getByName(SECOND_HOP_HOST), secondHop.port());
             FirstHopServer firstHop = new FirstHopServer(InetAddress.getByName(FIRST_HOP_HOST),
                                                          intermediateHop.port(),
                                                          false,
                                                          302)) {
            Http1Client client = newClient(firstHop.port());
            try {
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                               () -> client.post("/token")
                                                                       .maxRedirects(1)
                                                                       .sendExpectContinue(true)
                                                                       .readContinueTimeout(REQUEST_TIMEOUT)
                                                                       .readTimeout(REQUEST_TIMEOUT)
                                                                       .header(HeaderNames.CONTENT_TYPE,
                                                                               "application/x-www-form-urlencoded")
                                                                       .outputStream(outputStream -> {
                                                                           outputStream.write(requestBodyBytes());
                                                                           outputStream.close();
                                                                       }));
                assertThat(exception.getMessage(), is("Maximum number of request redirections (1) reached."));

                CapturedRequest originRequest = firstHop.awaitRequest();
                assertThat(originRequest.path(), is("/token"));
                assertThat(originRequest.header("expect"), is("100-continue"));
                assertThat(originRequest.body(), is(""));

                CapturedRequest intermediateRequest = intermediateHop.awaitRequest();
                assertThat(intermediateRequest.path(), is("/steal"));
                assertThat(intermediateRequest.header("authorization"), is((String) null));
                assertThat(intermediateRequest.body(), is(""));
                secondHop.assertNoRequest();
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    @Timeout(20)
    void doesNotTreatZeroLengthWriteAsReplayableEntity(int redirectStatus) throws Exception {
        try (SecondHopServer secondHop = new SecondHopServer(InetAddress.getByName(SECOND_HOP_HOST));
             FirstHopServer firstHop = new FirstHopServer(InetAddress.getByName(FIRST_HOP_HOST),
                                                          secondHop.port(),
                                                          false,
                                                          redirectStatus)) {
            Http1Client client = newClient(firstHop.port());
            try {
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                               () -> client.post("/token")
                                                                       .sendExpectContinue(true)
                                                                       .readContinueTimeout(REQUEST_TIMEOUT)
                                                                       .readTimeout(REQUEST_TIMEOUT)
                                                                       .header(HeaderNames.CONTENT_TYPE,
                                                                               "application/x-www-form-urlencoded")
                                                                       .outputStream(outputStream -> {
                                                                           outputStream.write(new byte[0]);
                                                                           outputStream.write(requestBodyBytes());
                                                                           outputStream.close();
                                                                       }));
                assertThat(exception.getMessage(), is(BLOCKED_REDIRECT_MESSAGE));

                CapturedRequest originRequest = firstHop.awaitRequest();
                assertThat(originRequest.path(), is("/token"));
                assertThat(originRequest.header("expect"), is("100-continue"));
                assertThat(originRequest.body(), is(""));
                secondHop.assertNoRequest();
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    @Timeout(20)
    void closesExpectContinueRedirectConnectionWhenEntityReplayBlocked(int redirectStatus) throws Exception {
        // This deliberately uses the raw socket server below instead of WebServer.
        // The client receives a final 307/308 response before sending the request body, the response carries an unread
        // entity, and replay to the redirected origin is blocked. The test must observe that the exact first-hop TCP
        // connection is closed, not returned to the pool with unread bytes. WebServer handlers abstract that connection
        // lifecycle and may consume, flush, or close the response in ways that would hide the client-side pooling bug.
        try (SecondHopServer secondHop = new SecondHopServer(InetAddress.getByName(SECOND_HOP_HOST));
             FirstHopBodyRedirectServer firstHop = new FirstHopBodyRedirectServer(InetAddress.getByName(FIRST_HOP_HOST),
                                                                                  secondHop.port(),
                                                                                  redirectStatus)) {
            Http1Client client = newClient(firstHop.port());
            try {
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                               () -> client.post("/token")
                                                                       .sendExpectContinue(true)
                                                                       .readContinueTimeout(REQUEST_TIMEOUT)
                                                                       .readTimeout(REQUEST_TIMEOUT)
                                                                       .header(HeaderNames.CONTENT_TYPE,
                                                                               "application/x-www-form-urlencoded")
                                                                       .outputStream(outputStream -> {
                                                                           outputStream.write(requestBodyBytes());
                                                                           outputStream.close();
                                                                       }));
                assertThat(exception.getMessage(), is(BLOCKED_REDIRECT_MESSAGE));

                CapturedRequest originRequest = firstHop.awaitRequest();
                assertThat(originRequest.path(), is("/token"));
                assertThat(originRequest.header("expect"), is("100-continue"));
                assertThat(originRequest.body(), is(""));
                secondHop.assertNoRequest();
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    @Timeout(20)
    void rejectsAlreadySentOutputStreamEntityAcrossCrossOriginRedirect(int redirectStatus) throws Exception {
        try (SecondHopServer secondHop = new SecondHopServer(InetAddress.getByName(SECOND_HOP_HOST));
             FirstHopServer firstHop = new FirstHopServer(InetAddress.getByName(FIRST_HOP_HOST),
                                                          secondHop.port(),
                                                          true,
                                                          redirectStatus)) {
            Http1Client client = newClient(firstHop.port());
            try {
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                               () -> client.post("/token")
                                                                       .sendExpectContinue(false)
                                                                       .readTimeout(REQUEST_TIMEOUT)
                                                                       .header(HeaderNames.CONTENT_TYPE,
                                                                               "application/x-www-form-urlencoded")
                                                                       .outputStream(outputStream -> {
                                                                           outputStream.write(requestBodyBytes());
                                                                           outputStream.close();
                                                                       }));
                assertThat(exception.getMessage(), is(BLOCKED_REDIRECT_MESSAGE));

                CapturedRequest originRequest = firstHop.awaitRequest();
                assertThat(originRequest.path(), is("/token"));
                assertThat(originRequest.body(), is(REQUEST_BODY));
                secondHop.assertNoRequest();
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    @Timeout(20)
    void followsAlreadySentOutputStreamRedirectWithEntityWhenEnabled(int redirectStatus) throws Exception {
        try (SecondHopServer secondHop = new SecondHopServer(InetAddress.getByName(SECOND_HOP_HOST));
             FirstHopServer firstHop = new FirstHopServer(InetAddress.getByName(FIRST_HOP_HOST),
                                                          secondHop.port(),
                                                          true,
                                                          redirectStatus)) {
            Http1Client client = newClient(firstHop.port(), true);
            try {
                try (Http1ClientResponse response = client.post("/token")
                        .maxRedirects(1)
                        .sendExpectContinue(false)
                        .readTimeout(REQUEST_TIMEOUT)
                        .header(HeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded")
                        .outputStream(outputStream -> {
                            outputStream.write(requestBodyBytes());
                            outputStream.close();
                        })) {
                    assertThat(response.status().code(), is(200));
                }

                CapturedRequest originRequest = firstHop.awaitRequest();
                assertThat(originRequest.path(), is("/token"));
                assertThat(originRequest.body(), is(REQUEST_BODY));

                CapturedRequest redirectRequest = secondHop.awaitRequest();
                assertThat(redirectRequest.path(), is("/steal"));
                assertThat(redirectRequest.header("authorization"), is((String) null));
                assertThat(redirectRequest.body(), is(REQUEST_BODY));
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    @Timeout(20)
    void followsExpectContinueOutputStreamRedirectWithEntityWhenEnabled(int redirectStatus) throws Exception {
        try (SecondHopServer secondHop = new SecondHopServer(InetAddress.getByName(SECOND_HOP_HOST));
             FirstHopServer firstHop = new FirstHopServer(InetAddress.getByName(FIRST_HOP_HOST),
                                                          secondHop.port(),
                                                          false,
                                                          redirectStatus)) {
            Http1Client client = newClient(firstHop.port(), true);
            try {
                try (Http1ClientResponse response = client.post("/token")
                        .maxRedirects(1)
                        .sendExpectContinue(true)
                        .readContinueTimeout(REQUEST_TIMEOUT)
                        .readTimeout(REQUEST_TIMEOUT)
                        .header(HeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded")
                        .outputStream(outputStream -> {
                            outputStream.write(requestBodyBytes());
                            outputStream.close();
                        })) {
                    assertThat(response.status().code(), is(200));
                }

                CapturedRequest originRequest = firstHop.awaitRequest();
                assertThat(originRequest.path(), is("/token"));
                assertThat(originRequest.header("expect"), is("100-continue"));
                assertThat(originRequest.body(), is(""));

                CapturedRequest redirectRequest = secondHop.awaitRequest();
                assertThat(redirectRequest.path(), is("/steal"));
                assertThat(redirectRequest.header("authorization"), is((String) null));
                assertThat(redirectRequest.body(), is(REQUEST_BODY));
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    @Timeout(20)
    void followsCrossOriginRedirectWithEntityWhenEnabled(int redirectStatus) throws Exception {
        try (SecondHopServer secondHop = new SecondHopServer(InetAddress.getByName(SECOND_HOP_HOST));
             FirstHopServer firstHop = new FirstHopServer(InetAddress.getByName(FIRST_HOP_HOST),
                                                          secondHop.port(),
                                                          true,
                                                          redirectStatus)) {
            Http1Client client = newClient(firstHop.port(), true);
            try {
                try (Http1ClientResponse response = client.post("/token")
                        .readTimeout(REQUEST_TIMEOUT)
                        .header(HeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded")
                        .submit(requestBodyBytes())) {
                    assertThat(response.status().code(), is(200));
                }

                CapturedRequest originRequest = firstHop.awaitRequest();
                assertThat(originRequest.path(), is("/token"));
                assertThat(originRequest.body(), is(REQUEST_BODY));

                CapturedRequest redirectRequest = secondHop.awaitRequest();
                assertThat(redirectRequest.path(), is("/steal"));
                assertThat(redirectRequest.header("authorization"), is((String) null));
                assertThat(redirectRequest.body(), is(REQUEST_BODY));
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    @Timeout(20)
    void followsCrossOriginRedirectWithEmptyEntityByDefault(int redirectStatus) throws Exception {
        try (SecondHopServer secondHop = new SecondHopServer(InetAddress.getByName(SECOND_HOP_HOST));
             FirstHopServer firstHop = new FirstHopServer(InetAddress.getByName(FIRST_HOP_HOST),
                                                          secondHop.port(),
                                                          true,
                                                          redirectStatus)) {
            Http1Client client = newClient(firstHop.port());
            try {
                try (Http1ClientResponse response = client.post("/token")
                        .readTimeout(REQUEST_TIMEOUT)
                        .submit(new byte[0])) {
                    assertThat(response.status().code(), is(200));
                }

                CapturedRequest originRequest = firstHop.awaitRequest();
                assertThat(originRequest.path(), is("/token"));
                assertThat(originRequest.body(), is(""));

                CapturedRequest redirectRequest = secondHop.awaitRequest();
                assertThat(redirectRequest.path(), is("/steal"));
                assertThat(redirectRequest.header("authorization"), is((String) null));
                assertThat(redirectRequest.body(), is(""));
            } finally {
                client.closeResource();
            }
        }
    }

    private static Http1Client newClient(int firstHopPort) {
        return newClient(firstHopPort, null);
    }

    private static Http1Client newClient(int firstHopPort, boolean followCrossOriginEntityRedirects) {
        return newClient(firstHopPort, Boolean.valueOf(followCrossOriginEntityRedirects));
    }

    private static Http1Client newClient(int firstHopPort, Boolean followCrossOriginEntityRedirects) {
        var builder = Http1Client.builder()
                .servicesDiscoverServices(false)
                .addService(new AuthorizationService())
                .baseUri("http://" + FIRST_HOP_HOST + ":" + firstHopPort);
        if (followCrossOriginEntityRedirects != null) {
            builder.followCrossOriginEntityRedirects(followCrossOriginEntityRedirects);
        }
        return builder.build();
    }

    private static byte[] requestBodyBytes() {
        return REQUEST_BODY.getBytes(StandardCharsets.UTF_8);
    }

    private record AuthorizationService() implements WebClientService {
        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            request.headers().set(HeaderNames.AUTHORIZATION, AUTHORIZATION);
            return chain.proceed(request);
        }
    }

    private static final class FirstHopServer extends SingleRequestServer {
        private final int secondHopPort;
        private final boolean readBodyBeforeRedirect;
        private final int redirectStatus;

        private FirstHopServer(InetAddress bindAddress,
                               int secondHopPort,
                               boolean readBodyBeforeRedirect,
                               int redirectStatus)
                throws IOException {
            super(bindAddress);
            this.secondHopPort = secondHopPort;
            this.readBodyBeforeRedirect = readBodyBeforeRedirect;
            this.redirectStatus = redirectStatus;
        }

        @Override
        protected CapturedRequest handle(Socket socket) throws IOException {
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();
            CapturedRequest request = readHeadersOnly(inputStream);
            if (readBodyBeforeRedirect) {
                request = request.withBody(readBody(inputStream, request.headers()));
            }

            writeAscii(outputStream,
                       "HTTP/1.1 " + redirectStatus + " " + redirectReasonPhrase(redirectStatus) + "\r\n"
                               + "Location: http://" + SECOND_HOP_HOST + ":" + secondHopPort + "/steal\r\n"
                               + "Content-Length: 0\r\n"
                               + "Connection: close\r\n"
                               + "\r\n");
            return request;
        }
    }

    private static final class FirstHopBodyRedirectServer extends SingleRequestServer {
        private final int secondHopPort;
        private final int redirectStatus;

        private FirstHopBodyRedirectServer(InetAddress bindAddress, int secondHopPort, int redirectStatus)
                throws IOException {
            super(bindAddress);
            this.secondHopPort = secondHopPort;
            this.redirectStatus = redirectStatus;
        }

        @Override
        protected CapturedRequest handle(Socket socket) throws IOException {
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();
            CapturedRequest request = readHeadersOnly(inputStream);

            writeAscii(outputStream,
                       "HTTP/1.1 " + redirectStatus + " " + redirectReasonPhrase(redirectStatus) + "\r\n"
                               + "Location: http://" + SECOND_HOP_HOST + ":" + secondHopPort + "/steal\r\n"
                               + "Content-Length: 4\r\n"
                               + "\r\n"
                               + "BODY");
            int read = inputStream.read();
            if (read != -1) {
                throw new IOException("Expected client to close blocked redirect connection");
            }
            return request;
        }
    }

    private static final class IntermediateRedirectServer extends SingleRequestServer {
        private final int secondHopPort;

        private IntermediateRedirectServer(InetAddress bindAddress, int secondHopPort) throws IOException {
            super(bindAddress);
            this.secondHopPort = secondHopPort;
        }

        @Override
        protected CapturedRequest handle(Socket socket) throws IOException {
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();
            CapturedRequest request = readHeadersOnly(inputStream);

            writeAscii(outputStream,
                       "HTTP/1.1 302 Found\r\n"
                               + "Location: http://" + SECOND_HOP_HOST + ":" + secondHopPort + "/steal\r\n"
                               + "Content-Length: 0\r\n"
                               + "Connection: close\r\n"
                               + "\r\n");
            return request;
        }
    }

    private static String redirectReasonPhrase(int status) {
        return switch (status) {
            case 302 -> "Found";
            case 307 -> "Temporary Redirect";
            case 308 -> "Permanent Redirect";
            default -> throw new IllegalArgumentException("Unexpected redirect status: " + status);
        };
    }

    private static final class SecondHopServer extends SingleRequestServer {
        private SecondHopServer(InetAddress bindAddress) throws IOException {
            super(bindAddress);
        }

        @Override
        protected CapturedRequest handle(Socket socket) throws IOException {
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();

            CapturedRequest request = readHeadersOnly(inputStream);
            if ("100-continue".equalsIgnoreCase(request.header("expect"))) {
                writeAscii(outputStream, "HTTP/1.1 100 Continue\r\n\r\n");
            }
            String body = readBody(inputStream, request.headers());

            writeAscii(outputStream,
                       "HTTP/1.1 200 OK\r\n"
                               + "Content-Length: 2\r\n"
                               + "Connection: close\r\n"
                               + "\r\n"
                               + "OK");

            return request.withBody(body);
        }
    }

    private abstract static class SingleRequestServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread thread;
        private final CompletableFuture<CapturedRequest> requestFuture = new CompletableFuture<>();
        private volatile Socket acceptedSocket;

        private SingleRequestServer(InetAddress bindAddress) throws IOException {
            this.serverSocket = new ServerSocket();
            this.serverSocket.bind(new InetSocketAddress(bindAddress, 0));
            this.thread = new Thread(this::serveOnce, getClass().getSimpleName() + "-" + port());
            this.thread.setDaemon(true);
            this.thread.start();
        }

        private void serveOnce() {
            try (Socket socket = serverSocket.accept()) {
                acceptedSocket = socket;
                socket.setSoTimeout((int) REQUEST_TIMEOUT.toMillis());
                requestFuture.complete(handle(socket));
            } catch (SocketException e) {
                if (!serverSocket.isClosed() && !requestFuture.isDone()) {
                    requestFuture.completeExceptionally(e);
                }
            } catch (Throwable t) {
                if (!requestFuture.isDone()) {
                    requestFuture.completeExceptionally(t);
                }
            }
        }

        protected abstract CapturedRequest handle(Socket socket) throws Exception;

        int port() {
            return serverSocket.getLocalPort();
        }

        CapturedRequest awaitRequest() throws InterruptedException, ExecutionException, TimeoutException {
            return requestFuture.get(10, TimeUnit.SECONDS);
        }

        void assertNoRequest() throws InterruptedException, ExecutionException {
            try {
                CapturedRequest request = requestFuture.get(300, TimeUnit.MILLISECONDS);
                fail("Unexpected request to cross-origin target: " + request);
            } catch (TimeoutException expected) {
                // expected
            }
        }

        @Override
        public void close() throws Exception {
            Socket socket = acceptedSocket;
            if (socket != null) {
                socket.close();
            }
            serverSocket.close();
            thread.join(TimeUnit.SECONDS.toMillis(2));
        }
    }

    private static CapturedRequest readHeadersOnly(InputStream inputStream) throws IOException {
        String requestLine = readRequiredLine(inputStream);
        Map<String, String> headers = new LinkedHashMap<>();

        while (true) {
            String line = readRequiredLine(inputStream);
            if (line.isEmpty()) {
                return new CapturedRequest(requestLine, headers, "");
            }

            int separator = line.indexOf(':');
            if (separator < 0) {
                continue;
            }

            String name = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separator + 1).trim();
            headers.put(name, value);
        }
    }

    private static String readBody(InputStream inputStream, Map<String, String> headers) throws IOException {
        String transferEncoding = headers.getOrDefault("transfer-encoding", "");
        if (transferEncoding.toLowerCase(Locale.ROOT).contains("chunked")) {
            return readChunkedBody(inputStream);
        }

        String contentLength = headers.get("content-length");
        if (contentLength == null) {
            return "";
        }

        return new String(readExactly(inputStream, Integer.parseInt(contentLength)), StandardCharsets.UTF_8);
    }

    private static String readChunkedBody(InputStream inputStream) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        while (true) {
            String sizeLine = readRequiredLine(inputStream);
            int extension = sizeLine.indexOf(';');
            String sizeToken = extension >= 0 ? sizeLine.substring(0, extension) : sizeLine;
            int size = Integer.parseInt(sizeToken.trim(), 16);

            if (size == 0) {
                readTrailingHeaders(inputStream);
                return new String(body.toByteArray(), StandardCharsets.UTF_8);
            }

            body.write(readExactly(inputStream, size));
            readRequiredCrlf(inputStream);
        }
    }

    private static void readTrailingHeaders(InputStream inputStream) throws IOException {
        while (!readRequiredLine(inputStream).isEmpty()) {
            // no trailers expected
        }
    }

    private static byte[] readExactly(InputStream inputStream, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;

        while (offset < length) {
            int read = inputStream.read(data, offset, length - offset);
            if (read < 0) {
                throw new EOFException("Unexpected EOF while reading request body");
            }
            offset += read;
        }

        return data;
    }

    private static void readRequiredCrlf(InputStream inputStream) throws IOException {
        int cr = inputStream.read();
        int lf = inputStream.read();
        if (cr != '\r' || lf != '\n') {
            throw new IOException("Invalid chunk framing");
        }
    }

    private static String readRequiredLine(InputStream inputStream) throws IOException {
        String line = readLine(inputStream);
        if (line == null) {
            throw new EOFException("Unexpected EOF while reading request");
        }
        return line;
    }

    private static String readLine(InputStream inputStream) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();

        while (true) {
            int read = inputStream.read();
            if (read < 0) {
                if (line.size() == 0) {
                    return null;
                }
                throw new EOFException("Unexpected EOF while reading line");
            }

            if (read == '\n') {
                return new String(line.toByteArray(), StandardCharsets.US_ASCII);
            }

            if (read != '\r') {
                line.write(read);
            }
        }
    }

    private static void writeAscii(OutputStream outputStream, String response) throws IOException {
        outputStream.write(response.getBytes(StandardCharsets.US_ASCII));
        outputStream.flush();
    }

    private static final class CapturedRequest {
        private final String requestLine;
        private final Map<String, String> headers;
        private final String body;

        private CapturedRequest(String requestLine, Map<String, String> headers, String body) {
            this.requestLine = requestLine;
            this.headers = new LinkedHashMap<>(headers);
            this.body = body;
        }

        private CapturedRequest withBody(String body) {
            return new CapturedRequest(requestLine, headers, body);
        }

        private String path() {
            String[] parts = requestLine.split(" ");
            return parts.length > 1 ? parts[1] : requestLine;
        }

        private String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        private Map<String, String> headers() {
            return headers;
        }

        private String body() {
            return body;
        }

        @Override
        public String toString() {
            return requestLine + " " + headers;
        }
    }
}
