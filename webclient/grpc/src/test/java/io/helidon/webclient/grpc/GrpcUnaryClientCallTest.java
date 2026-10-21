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

package io.helidon.webclient.grpc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.InternalStatus;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertAll;

@Timeout(15)
class GrpcUnaryClientCallTest {
    private static final Metadata.Key<String> ASCII_TRAILER = Metadata.Key.of("application-trailer",
                                                                             Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<byte[]> BINARY_TRAILER = Metadata.Key.of("application-trailer-bin",
                                                                              Metadata.BINARY_BYTE_MARSHALLER);
    private static final byte[] FIRST_BINARY = {0, 1, 127, (byte) 128, (byte) 255};
    private static final byte[] SECOND_BINARY = {(byte) 254, 0, 42};
    private static final MethodDescriptor<String, String> METHOD = descriptor();

    @ParameterizedTest
    @EnumSource(Completion.class)
    void preservesApplicationTrailers(Completion completion) throws Exception {
        Status expectedStatus = Status.fromCode(completion.code).withDescription("application completion: 50% / detail");
        var service = ServerServiceDefinition.builder("test.Trailers")
                .addMethod(METHOD, (call, _) -> {
                    call.request(1);
                    return new ServerCall.Listener<>() {
                        @Override
                        public void onHalfClose() {
                            if (completion.headers) {
                                call.sendHeaders(new Metadata());
                            }
                            if (completion.response) {
                                call.sendMessage("response");
                            }
                            var trailers = new Metadata();
                            trailers.put(ASCII_TRAILER, "application value, with comma");
                            trailers.put(BINARY_TRAILER, FIRST_BINARY);
                            trailers.put(BINARY_TRAILER, SECOND_BINARY);
                            call.close(expectedStatus, trailers);
                        }
                    };
                })
                .build();
        var server = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .addRouting(GrpcRouting.builder().service(service))
                .build()
                .start();
        var client = GrpcClient.builder()
                .baseUri("http://127.0.0.1:" + server.port())
                .tls(tls -> tls.enabled(false))
                .readTimeout(Duration.ofSeconds(5))
                .build();
        var call = client.channel().newCall(METHOD, CallOptions.DEFAULT);
        var listener = new ResponseListener();
        try {
            call.start(listener, new Metadata());
            call.request(1);
            call.sendMessage("request");
            call.halfClose();
            var result = listener.result.get(5, TimeUnit.SECONDS);

            // A completed unary call must not notify the listener again.
            call.halfClose();
            call.cancel("already completed", null);
            call.halfClose();

            assertAll(() -> assertThat("status code", result.status.getCode(), is(expectedStatus.getCode())),
                      () -> assertThat("status description", result.status.getDescription(), is(expectedStatus.getDescription())),
                      () -> assertThat("ASCII application trailers", result.trailers.getAll(ASCII_TRAILER),
                                       contains("application value, with comma")),
                      () -> assertThat("binary application trailers", result.trailers.getAll(BINARY_TRAILER),
                                       contains(FIRST_BINARY, SECOND_BINARY)),
                      () -> assertThat("status is delivered separately", result.trailers.get(InternalStatus.CODE_KEY),
                                       nullValue()),
                      () -> assertThat("description is delivered separately", result.trailers.get(InternalStatus.MESSAGE_KEY),
                                       nullValue()),
                      () -> assertThat("response messages", listener.messages,
                                       is(completion.response ? List.of("response") : List.of())),
                      () -> assertThat("terminal notifications", listener.closeCount.get(), is(1)),
                      () -> assertThat("callbacks after terminal notification", listener.callbacksAfterClose.get(), is(0)));
        } finally {
            call.cancel("test cleanup", null);
            server.stop();
        }
    }

    private static MethodDescriptor<String, String> descriptor() {
        MethodDescriptor.Marshaller<String> marshaller = new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(String value) {
                return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public String parse(InputStream stream) {
                try {
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        return MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("test.Trailers/Call")
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
    }

    private enum Completion {
        RESPONSE_OK(true, true, Status.Code.OK),
        RESPONSE_ERROR(true, true, Status.Code.INVALID_ARGUMENT),
        TRAILERS_ONLY_OK(false, false, Status.Code.OK),
        TRAILERS_ONLY_ERROR(false, false, Status.Code.INVALID_ARGUMENT),
        HEADERS_THEN_ERROR(true, false, Status.Code.INVALID_ARGUMENT);

        private final boolean headers;
        private final boolean response;
        private final Status.Code code;

        Completion(boolean headers, boolean response, Status.Code code) {
            this.headers = headers;
            this.response = response;
            this.code = code;
        }
    }

    private record Response(Status status, Metadata trailers) {
    }

    private static final class ResponseListener extends ClientCall.Listener<String> {
        private final CompletableFuture<Response> result = new CompletableFuture<>();
        private final List<String> messages = new CopyOnWriteArrayList<>();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicInteger closeCount = new AtomicInteger();
        private final AtomicInteger callbacksAfterClose = new AtomicInteger();

        @Override
        public void onHeaders(Metadata headers) {
            recordCallback();
        }

        @Override
        public void onMessage(String message) {
            recordCallback();
            messages.add(message);
        }

        @Override
        public void onReady() {
            recordCallback();
        }

        @Override
        public void onClose(Status status, Metadata trailers) {
            recordCallback();
            closed.set(true);
            closeCount.incrementAndGet();
            result.complete(new Response(status, trailers));
        }

        private void recordCallback() {
            if (closed.get()) {
                callbacksAfterClose.incrementAndGet();
            }
        }
    }
}
