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
package io.helidon.webclient.grpc.tests;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;

import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(15)
class GrpcDeadlinePropagationTest {
    @Test
    void workerCompletesUnaryCallWhileCallbackWaits() throws Exception {
        workerCompletesUnaryCallWhileCallbackWaits(false);
    }

    @Test
    void workerCompletesUnaryCallWhileCallbackWaitsWithDeadline() throws Exception {
        workerCompletesUnaryCallWhileCallbackWaits(true);
    }

    @Test
    void generatedStubHonorsDeadline() {
        var release = new CountDownLatch(1);
        var server = WebServer.builder()
                .addRouting(GrpcRouting.builder()
                                    .unary(Strings.getDescriptor(), "StringService", "Upper",
                                           (Strings.StringMessage request,
                                            StreamObserver<Strings.StringMessage> response) -> {
                                               try {
                                                   release.await(10, TimeUnit.SECONDS);
                                               } catch (InterruptedException _) {
                                                   Thread.currentThread().interrupt();
                                               }
                                               response.onNext(request);
                                               response.onCompleted();
                                           }))
                .build()
                .start();
        try {
            var stub = StringServiceGrpc.newBlockingStub(client(server).channel())
                    .withDeadlineAfter(300, TimeUnit.MILLISECONDS);

            var failure = assertThrows(StatusRuntimeException.class,
                                       () -> stub.upper(Strings.StringMessage.newBuilder().setText("request").build()));

            assertThat(failure.getStatus().getCode(), is(Status.Code.DEADLINE_EXCEEDED));
        } finally {
            release.countDown();
            server.stop();
        }
    }

    @Test
    void forwardsInboundDeadlineWithoutExplicitCallOptions() {
        var downstreamDeadline = new AtomicReference<Deadline>();
        var upstreamDeadline = new AtomicReference<Deadline>();
        var downstreamTimeout = new TimeoutInterceptor();
        var downstream = WebServer.builder()
                .addRouting(GrpcRouting.builder()
                                    .intercept(downstreamTimeout)
                                    .unary(Strings.getDescriptor(), "StringService", "Upper",
                                           (Strings.StringMessage request,
                                            StreamObserver<Strings.StringMessage> response) -> {
                                               downstreamDeadline.set(Context.current().getDeadline());
                                               response.onNext(request);
                                               response.onCompleted();
                                           }))
                .build()
                .start();
        try {
            var downstreamStub = StringServiceGrpc.newBlockingStub(client(downstream).channel());
            var upstream = WebServer.builder()
                    .addRouting(GrpcRouting.builder()
                                        .unary(Strings.getDescriptor(), "StringService", "Upper",
                                               (Strings.StringMessage request,
                                                StreamObserver<Strings.StringMessage> response) -> {
                                                   upstreamDeadline.set(Context.current().getDeadline());
                                                   response.onNext(downstreamStub.upper(request));
                                                   response.onCompleted();
                                               }))
                    .build()
                    .start();
            try {
                var stub = StringServiceGrpc.newBlockingStub(client(upstream).channel())
                        .withDeadlineAfter(30, TimeUnit.SECONDS);
                var request = Strings.StringMessage.newBuilder().setText("forwarded").build();

                assertThat(stub.upper(request), is(request));

                assertThat("inbound handler deadline", upstreamDeadline.get(), notNullValue());
                assertThat("downstream handler deadline", downstreamDeadline.get(), notNullValue());
                assertThat("forwarded grpc-timeout", downstreamTimeout.timeout.get(), notNullValue());
                assertThat(downstreamDeadline.get().timeRemaining(TimeUnit.NANOSECONDS), greaterThan(0L));
                assertThat(downstreamDeadline.get().timeRemaining(TimeUnit.NANOSECONDS),
                           lessThanOrEqualTo(TimeUnit.SECONDS.toNanos(30)));
            } finally {
                upstream.stop();
            }
        } finally {
            downstream.stop();
        }
    }

    private static void workerCompletesUnaryCallWhileCallbackWaits(boolean withDeadline) throws Exception {
        var release = new CompletableFuture<Void>();
        var workerReturned = new CompletableFuture<Void>();
        var listener = new CompletionInterceptor();
        var workers = Executors.newVirtualThreadPerTaskExecutor();
        var server = WebServer.builder()
                .addRouting(GrpcRouting.builder()
                                    .intercept(listener)
                                    .unary(Strings.getDescriptor(), "StringService", "Upper",
                                           (Strings.StringMessage request,
                                            StreamObserver<Strings.StringMessage> response) -> {
                                               workers.submit(() -> {
                                                   try {
                                                       response.onNext(request);
                                                       response.onCompleted();
                                                       workerReturned.complete(null);
                                                   } catch (Throwable t) {
                                                       workerReturned.completeExceptionally(t);
                                                   }
                                               });
                                               // Cleanup can release the callback even when the worker is deadlocked.
                                               CompletableFuture.anyOf(workerReturned, release).join();
                                           }))
                .build()
                .start();
        try {
            var stub = StringServiceGrpc.newBlockingStub(client(server).channel());
            if (withDeadline) {
                stub = stub.withDeadlineAfter(10, TimeUnit.SECONDS);
            }
            var request = Strings.StringMessage.newBuilder().setText("worker completion").build();

            assertThat(stub.upper(request), is(request));
            // Trailers can arrive before onCompleted returns, so observe the worker and listener too.
            workerReturned.get(3, TimeUnit.SECONDS);
            listener.callbackReturned.get(3, TimeUnit.SECONDS);
            listener.terminalReturned.get(3, TimeUnit.SECONDS);
            assertThat("completion callback count", listener.completions.get(), is(1));
            assertThat("cancellation callback count", listener.cancellations.get(), is(0));
            assertThat("terminal callback overlaps application callback", listener.terminalOverlap.get(), is(false));
        } finally {
            release.complete(null);
            workers.shutdown();
            try {
                assertThat("response worker stopped", workers.awaitTermination(3, TimeUnit.SECONDS), is(true));
            } finally {
                workers.shutdownNow();
                server.stop();
            }
        }
    }

    private static GrpcClient client(WebServer server) {
        return GrpcClient.builder()
                .baseUri("http://localhost:" + server.port())
                .tls(tls -> tls.enabled(false))
                .build();
    }

    private static final class CompletionInterceptor implements ServerInterceptor {
        private final CompletableFuture<Void> callbackReturned = new CompletableFuture<>();
        private final CompletableFuture<Void> terminalReturned = new CompletableFuture<>();
        private final AtomicBoolean callbackActive = new AtomicBoolean();
        private final AtomicBoolean terminalOverlap = new AtomicBoolean();
        private final AtomicInteger completions = new AtomicInteger();
        private final AtomicInteger cancellations = new AtomicInteger();

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                   Metadata headers,
                                                                   ServerCallHandler<ReqT, RespT> next) {
            return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(next.startCall(call, headers)) {
                @Override
                public void onMessage(ReqT message) {
                    callbackActive.set(true);
                    try {
                        super.onMessage(message);
                    } finally {
                        callbackActive.set(false);
                    }
                }

                @Override
                public void onHalfClose() {
                    callbackActive.set(true);
                    try {
                        super.onHalfClose();
                    } finally {
                        callbackActive.set(false);
                        callbackReturned.complete(null);
                    }
                }

                @Override
                public void onComplete() {
                    completions.incrementAndGet();
                    if (callbackActive.get()) {
                        terminalOverlap.set(true);
                    }
                    try {
                        super.onComplete();
                    } finally {
                        terminalReturned.complete(null);
                    }
                }

                @Override
                public void onCancel() {
                    cancellations.incrementAndGet();
                    if (callbackActive.get()) {
                        terminalOverlap.set(true);
                    }
                    try {
                        super.onCancel();
                    } finally {
                        terminalReturned.complete(null);
                    }
                }
            };
        }
    }

    private static final class TimeoutInterceptor implements ServerInterceptor {
        private final AtomicReference<String> timeout = new AtomicReference<>();

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                   Metadata headers,
                                                                   ServerCallHandler<ReqT, RespT> next) {
            timeout.set(headers.get(Metadata.Key.of("grpc-timeout", Metadata.ASCII_STRING_MARSHALLER)));
            return next.startCall(call, headers);
        }
    }
}
