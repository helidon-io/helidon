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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RequestTargetTest {
    private static final String SIGNATURE_HEADER = "X-Forwarded-Signature";

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("requestTargets")
    void preservesForwardedRequestTarget(Construction construction, String target) throws Exception {
        try (var listener = new ServerSocket();
             var executor = Executors.newSingleThreadExecutor()) {
            listener.bind(new InetSocketAddress("127.0.0.1", 0));
            listener.setSoTimeout(5000);
            var capture = executor.submit(() -> {
                try (var socket = listener.accept()) {
                    socket.setSoTimeout(5000);
                    var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    String requestLine = reader.readLine();
                    String signature = null;
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        int colon = line.indexOf(':');
                        if (colon > 0 && SIGNATURE_HEADER.equalsIgnoreCase(line.substring(0, colon))) {
                            signature = line.substring(colon + 1).strip();
                        }
                    }
                    socket.getOutputStream().write(("HTTP/1.1 204 No Content\r\n"
                            + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    return new CapturedRequest(requestLine, signature);
                }
            });

            URI uri = URI.create("http://127.0.0.1:" + listener.getLocalPort() + target);
            String expectedTarget = rawTarget(uri);
            String forwardedSignature = signature(expectedTarget);
            var signerUri = new AtomicReference<URI>();
            var builder = WebClient.builder()
                    .keepAlive(false)
                    .proxy(Proxy.noProxy())
                    .shareConnectionCache(false)
                    .connectTimeout(Duration.ofSeconds(5))
                    .readTimeout(Duration.ofSeconds(5))
                    .addService((chain, request) -> {
                        signerUri.set(request.uri().toUri());
                        request.headers().set(HeaderNames.create(SIGNATURE_HEADER), forwardedSignature);
                        return chain.proceed(request);
                    });
            switch (construction) {
            case BASE_URI -> builder.baseUri(uri);
            case BASE_URI_STRING -> builder.baseUri(uri.toString());
            default -> builder.baseUri("http://127.0.0.1:" + listener.getLocalPort());
            }
            WebClient client = builder.build();
            try {
                var request = client.get();
                switch (construction) {
                case REQUEST_URI -> request.uri(uri);
                case CLIENT_URI_COPY -> request.uri(ClientUri.create(ClientUri.create(uri)));
                default -> {
                }
                }
                URI resolvedUri = request.resolvedUri().toUri();
                try (var response = request.request()) {
                    assertThat(response.status(), is(Status.NO_CONTENT_204));
                }
                CapturedRequest received = capture.get(10, TimeUnit.SECONDS);
                assertThat("HTTP/1 request line", received.requestLine(), is("GET " + expectedTarget + " HTTP/1.1"));
                assertThat("signer-visible URI", rawTarget(signerUri.get()), is(expectedTarget));
                assertThat("resolved URI", rawTarget(resolvedUri), is(expectedTarget));
                String wireTarget = received.requestLine().split(" ", 3)[1];
                assertThat("forwarded signature verified against wire target", received.signature(), is(signature(wireTarget)));
            } finally {
                client.closeResource();
            }
        }
    }

    private static Stream<Arguments> requestTargets() {
        return Stream.of("/proxy?a=1",
                         "/proxy?b=2&a=1&b=3",
                         "/proxy?flag",
                         "/proxy?x=1&&y=2",
                         "/proxy?=value&empty=&=",
                         "/proxy?x=1&",
                         "/proxy?value=%2f%2F%7e%7E%25%3f%3F%23&space=+%20",
                         "/proxy/a%2Fb%2fc?sig=one",
                         "/proxy/%7e%7E%41%61",
                         "/proxy/%25/%3F/%3f/%23",
                         "/proxy;a=1/b;c=2?x=3",
                         "/proxy/a+b//c/./d/../e",
                         "/proxy?",
                         "/proxy?flag#not-sent",
                         "?flag",
                         "?",
                         "#not-sent",
                         "")
                .flatMap(target -> Stream.of(Construction.values()).map(mode -> Arguments.of(mode, target)));
    }

    private static String rawTarget(URI uri) {
        String path = uri.getRawPath();
        String query = uri.getRawQuery();
        return (path.isEmpty() ? "/" : path) + (query == null ? "" : "?" + query);
    }

    private static String signature(String target) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("forwarded-request-test-key".getBytes(StandardCharsets.US_ASCII), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(("(request-target): get " + target)
                                                                    .getBytes(StandardCharsets.US_ASCII)));
    }

    private enum Construction {
        REQUEST_URI,
        CLIENT_URI_COPY,
        BASE_URI,
        BASE_URI_STRING
    }

    private record CapturedRequest(String requestLine, String signature) {
    }
}
