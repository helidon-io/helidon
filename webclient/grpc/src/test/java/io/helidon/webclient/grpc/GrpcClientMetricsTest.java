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
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactory;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

class GrpcClientMetricsTest {
    private static final String METHOD = "metrics.Test/Stream";
    private static final String DURATION = "grpc.client.attempt.duration";
    private static final String SENT = "grpc.client.attempt.sent_total_compressed_message_size";
    private static final String RECEIVED = "grpc.client.attempt.rcvd_total_compressed_message_size";

    @ParameterizedTest
    @EnumSource(value = MethodDescriptor.MethodType.class,
                names = {"UNARY", "CLIENT_STREAMING", "SERVER_STREAMING", "BIDI_STREAMING"})
    void recordsTerminalStatusOnce(MethodDescriptor.MethodType methodType) throws Exception {
        AtomicReference<Status.Code> responseCode = new AtomicReference<>(Status.Code.OK);
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        WebServer server = WebServer.builder()
                .tls(tls -> tls.enabled(false))
                .addConnectionSelector(Http2ConnectionSelector.builder()
                                               .http2Config(Http2Config.create())
                                               .build())
                .addRouting(HttpRouting.builder()
                                    .post("/" + METHOD, (req, res) -> {
                                        req.content().as(byte[].class);
                                        Status.Code code = responseCode.get();
                                        if (code == Status.Code.CANCELLED) {
                                            requestReceived.countDown();
                                            if (!releaseResponse.await(10, TimeUnit.SECONDS)) {
                                                throw new IllegalStateException("Response was not released");
                                            }
                                        }
                                        res.header(HeaderNames.CONTENT_TYPE, "application/grpc");
                                        res.header(HeaderNames.TRAILER, "grpc-status");
                                        res.trailers().set(HeaderValues.create(GrpcBaseClientCall.STATUS_NAME,
                                                                              String.valueOf(code.value())));
                                        byte[] response = "response".getBytes(StandardCharsets.UTF_8);
                                        BufferData data = BufferData.create(GrpcBaseClientCall.DATA_PREFIX_LENGTH + response.length);
                                        data.write(0);
                                        data.writeUnsignedInt32(response.length);
                                        data.write(response);
                                        res.send(data.readBytes());
                                    }))
                .build()
                .start();
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MeterRegistry registry = factory.createMeterRegistry(MetricsConfig.create());
        try {
            String target = "http://localhost:" + server.port() + "/";
            GrpcChannel channel = (GrpcChannel) GrpcClient.builder()
                    .baseUri(target)
                    .tls(tls -> tls.enabled(false))
                    .enableMetrics(true)
                    .meterRegistry(registry)
                    .build()
                    .channel();
            MethodDescriptor<String, String> descriptor = descriptor(methodType);
            AtomicInteger notifications = new AtomicInteger();
            var expectedCounts = new EnumMap<Status.Code, Long>(Status.Code.class);
            int completed = 0;
            List<Status.Code> outcomes = methodType == MethodDescriptor.MethodType.UNARY
                    ? List.of(Status.Code.OK, Status.Code.UNAVAILABLE, Status.Code.OK)
                    : List.of(Status.Code.OK, Status.Code.UNAVAILABLE, Status.Code.CANCELLED, Status.Code.OK);
            for (Status.Code code : outcomes) {
                responseCode.set(code);
                CompletableFuture<Status> closed = new CompletableFuture<>();
                GrpcBaseClientCall<String, String> call =
                        (GrpcBaseClientCall<String, String>) channel.newCall(descriptor, CallOptions.DEFAULT);
                try {
                    call.start(new ClientCall.Listener<>() {
                        @Override
                        public void onClose(Status status, Metadata trailers) {
                            notifications.incrementAndGet();
                            closed.complete(status);
                        }
                    }, new Metadata());
                    call.request(1);
                    call.sendMessage("request");
                    call.halfClose();
                    if (code == Status.Code.CANCELLED) {
                        assertThat("server received the streaming request", requestReceived.await(10, TimeUnit.SECONDS), is(true));
                        call.cancel("cancel streaming call", null);
                    }
                    assertThat("listener terminal status", closed.get(10, TimeUnit.SECONDS).getCode(), is(code));
                    call.cancel("duplicate cancellation", null);
                    call.close(Status.INTERNAL);
                    completed++;
                    long expectedCount = expectedCounts.merge(code, 1L, Long::sum);

                    List<Tag> tags = List.of(factory.tagCreate("grpc.method", METHOD),
                                             factory.tagCreate("grpc.target", target),
                                             factory.tagCreate("grpc.status", code.name()));
                    assertAll(
                            () -> assertThat(code + " duration count",
                                             registry.timer(DURATION, tags).orElseThrow().count(), is(expectedCount)),
                            () -> assertThat(code + " sent-size count",
                                             registry.summary(SENT, tags).orElseThrow().count(), is(expectedCount)),
                            () -> assertThat(code + " received-size count",
                                             registry.summary(RECEIVED, tags).orElseThrow().count(), is(expectedCount)));
                    assertThat("one terminal callback per call", notifications.get(), is(completed));
                    assertThat("one duration sample per call across all statuses",
                               registry.meters(meter -> meter.id().name().equals(DURATION)).stream()
                                       .map(Timer.class::cast).mapToLong(Timer::count).sum(), is((long) completed));
                    for (String name : List.of(SENT, RECEIVED)) {
                        assertThat("one " + name + " sample per call across all statuses",
                                   registry.meters(meter -> meter.id().name().equals(name)).stream()
                                           .map(DistributionSummary.class::cast).mapToLong(DistributionSummary::count).sum(),
                                   is((long) completed));
                    }
                    assertThat("started counter keeps method and target dimensions",
                               registry.counter("grpc.client.attempt.started",
                                                List.of(factory.tagCreate("grpc.method", METHOD),
                                                        factory.tagCreate("grpc.target", target)))
                                       .orElseThrow().count(), is((long) completed));
                } finally {
                    call.cancel("test cleanup", null);
                }
            }
        } finally {
            releaseResponse.countDown();
            server.stop();
            registry.close();
            factory.close();
        }
    }

    @ParameterizedTest
    @EnumSource(value = MethodDescriptor.MethodType.class,
                names = {"UNARY", "CLIENT_STREAMING", "SERVER_STREAMING", "BIDI_STREAMING"})
    void cancellationBeforeStartRecordsOnceWhenStarted(MethodDescriptor.MethodType methodType) throws Exception {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MeterRegistry registry = factory.createMeterRegistry(MetricsConfig.create());
        try {
            String target = "http://localhost:1/";
            GrpcChannel channel = (GrpcChannel) GrpcClient.builder()
                    .baseUri(target)
                    .enableMetrics(true)
                    .meterRegistry(registry)
                    .build()
                    .channel();
            var call = (GrpcBaseClientCall<String, String>) channel.newCall(
                    descriptor(methodType), CallOptions.DEFAULT);
            CompletableFuture<Status> closed = new CompletableFuture<>();
            AtomicInteger notifications = new AtomicInteger();
            call.cancel("before start", null);
            call.start(new ClientCall.Listener<>() {
                @Override
                public void onClose(Status status, Metadata trailers) {
                    notifications.incrementAndGet();
                    closed.complete(status);
                }
            }, new Metadata());
            call.cancel("duplicate cancellation", null);
            assertThat("cancelled before transport setup", closed.get(10, TimeUnit.SECONDS).getCode(), is(Status.Code.CANCELLED));
            List<Tag> tags = List.of(factory.tagCreate("grpc.method", METHOD),
                                     factory.tagCreate("grpc.target", target),
                                     factory.tagCreate("grpc.status", "CANCELLED"));
            assertAll(
                    () -> assertThat("one terminal callback", notifications.get(), is(1)),
                    () -> assertThat("cancelled duration count", registry.timer(DURATION, tags).orElseThrow().count(), is(1L)),
                    () -> assertThat("cancelled sent-size count", registry.summary(SENT, tags).orElseThrow().count(), is(1L)),
                    () -> assertThat("cancelled received-size count", registry.summary(RECEIVED, tags).orElseThrow().count(), is(1L)));
        } finally {
            registry.close();
            factory.close();
        }
    }

    private static MethodDescriptor<String, String> descriptor(MethodDescriptor.MethodType methodType) {
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
                .setType(methodType)
                .setFullMethodName(METHOD)
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
    }
}
