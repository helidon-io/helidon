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

package io.helidon.webserver.grpc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.buffers.BufferData;
import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2HuffmanEncoder;
import io.helidon.http.http2.Http2Setting;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2Util;
import io.helidon.webserver.WebServer;

import io.grpc.Context;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerServiceDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

@Timeout(15)
class GrpcDisconnectTest {
    @Test
    void disconnectCancelsIdleCallWithLongDeadline() throws Exception {
        var started = new CompletableFuture<Context>();
        var contextCancelled = new CompletableFuture<Context>();
        var listenerCancelled = new CompletableFuture<Context>();
        var cancellations = new AtomicInteger();
        var completions = new AtomicInteger();
        var service = ServerServiceDefinition.builder("test.Disconnect")
                .addMethod(descriptor(), (call, metadata) -> {
                    Context.current().addListener(contextCancelled::complete, Runnable::run);
                    return new ServerCall.Listener<>() {
                        @Override
                        public void onReady() {
                            started.complete(Context.current());
                        }

                        @Override
                        public void onCancel() {
                            cancellations.incrementAndGet();
                            listenerCancelled.complete(Context.current());
                        }

                        @Override
                        public void onComplete() {
                            completions.incrementAndGet();
                        }
                    };
                })
                .build();
        var server = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .addRouting(GrpcRouting.builder().config(Config.empty()).service(service))
                .build()
                .start();
        try {
            try (var socket = new Socket("127.0.0.1", server.port())) {
                startCall(socket);
                Context context = started.get(5, TimeUnit.SECONDS);
                assertThat(context.getDeadline(), notNullValue());
                assertThat(context.getDeadline().timeRemaining(TimeUnit.MINUTES), greaterThan(30L));
                assertThat(context.isCancelled(), is(false));
            }

            // Closing TCP, without RST_STREAM or any request DATA, must release the call before its deadline.
            assertThat(contextCancelled.get(5, TimeUnit.SECONDS).isCancelled(), is(true));
            assertThat(listenerCancelled.get(5, TimeUnit.SECONDS).isCancelled(), is(true));
            assertThat(cancellations.get(), is(1));
            assertThat(completions.get(), is(0));
        } finally {
            server.stop();
        }
    }

    private static void startCall(Socket socket) throws IOException {
        var output = socket.getOutputStream();
        output.write(Http2Util.prefaceData().readBytes());
        var settings = Http2Settings.create().toFrameData(Http2Settings.create(), 0, Http2Flag.SettingsFlags.create(0));
        output.write(settings.header().write().readBytes());
        output.write(settings.data().readBytes());

        var writable = WritableHeaders.create()
                .set(HeaderNames.CONTENT_TYPE, "application/grpc")
                .set(HeaderNames.TE, "trailers")
                .set(HeaderNames.create("grpc-timeout"), "1H");
        var headers = Http2Headers.create(writable);
        headers.method(Method.POST);
        headers.path("/test.Disconnect/Call");
        headers.scheme("http");
        headers.authority("localhost");
        var encoded = BufferData.growing(512);
        headers.write(Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                      Http2HuffmanEncoder.create(), encoded);
        var header = Http2FrameHeader.create(encoded.available(), Http2FrameTypes.HEADERS,
                                              Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS), 1);
        output.write(header.write().readBytes());
        output.write(encoded.readBytes());
        output.flush();
    }

    private static MethodDescriptor<String, String> descriptor() {
        var marshaller = new MethodDescriptor.Marshaller<String>() {
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
                .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
                .setFullMethodName("test.Disconnect/Call")
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
    }
}
