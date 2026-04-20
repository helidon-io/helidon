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

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.ClientCall;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;

import io.grpc.CallOptions;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Verifies that a call rejected before I/O (already-expired deadline) still increments
 * the {@code grpc.client.attempt.started} counter.
 */
class DeadlineMetricsTest {

    private static final MethodDescriptor<byte[], byte[]> METHOD =
            MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("TestService/TestMethod")
                    .setRequestMarshaller(ByteMarshaller.INSTANCE)
                    .setResponseMarshaller(ByteMarshaller.INSTANCE)
                    .build();

    @Test
    void preStartDeadlineCountsAsAttempt() {
        GrpcClient grpcClient = GrpcClient.builder()
                .baseUri("http://localhost:19999")   // no server needed — fails before connecting
                .enableMetrics(true)
                .build();
        GrpcChannel channel = new GrpcChannel(grpcClient);

        MeterRegistry registry = MetricsFactory.getInstance().globalRegistry();
        Tag grpcMethod = Tag.create("grpc.method", "TestService/TestMethod");
        Tag grpcTarget = Tag.create("grpc.target", channel.baseUri().toString());

        long before = registry.counter("grpc.client.attempt.started", List.of(grpcMethod, grpcTarget))
                .map(Counter::count)
                .orElse(0L);

        // Already-expired deadline: start() inits metrics then returns before opening a connection
        AtomicReference<Status> closedStatus = new AtomicReference<>();
        var call = channel.newCall(METHOD, CallOptions.DEFAULT.withDeadlineAfter(0, TimeUnit.NANOSECONDS));
        call.start(new ClientCall.Listener<>() {
            @Override
            public void onClose(Status status, Metadata trailers) {
                closedStatus.set(status);
            }
        }, new Metadata());

        assertThat("call should have been closed", closedStatus.get(), is(notNullValue()));
        assertThat("status should be DEADLINE_EXCEEDED",
                closedStatus.get().getCode(), is(Status.Code.DEADLINE_EXCEEDED));

        Optional<Counter> counter = registry.counter("grpc.client.attempt.started",
                List.of(grpcMethod, grpcTarget));
        assertThat("attempt.started counter should exist", counter.isPresent(), is(true));
        assertThat("pre-start deadline failure should increment attempt.started by 1",
                counter.get().count() - before, is(1L));
    }

    /** Minimal marshaller for byte arrays — never called in the fail-fast deadline path. */
    private enum ByteMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        INSTANCE;

        @Override
        public InputStream stream(byte[] value) {
            return new java.io.ByteArrayInputStream(value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            try {
                return stream.readAllBytes();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
