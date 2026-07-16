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

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Exception;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcClientMethodDescriptor;
import io.helidon.webclient.grpc.GrpcServiceClient;
import io.helidon.webclient.grpc.GrpcServiceDescriptor;
import io.helidon.webserver.CloseConnectionException;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.grpc.GrpcStreams;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Verifies that the Helidon gRPC webclient sends the {@code te: trailers} header on the wire.
 *
 * <p>The gRPC-over-HTTP/2 spec lists {@code TE} in the Call-Definition as a non-optional field.
 * Intermediaries use it to decide whether to forward HTTP/2 trailers end-to-end, so its absence
 * causes proxies to silently drop {@code grpc-status} and trailing metadata.
 *
 * <p>This test uses a server-side {@link ServerInterceptor} to capture the raw {@link Metadata}
 * as received by the server, which proves the header survived the full client encoding pipeline,
 * not just that it was added to a {@link io.helidon.http.WritableHeaders} in-process.
 */
@ServerTest
class GrpcTeTrailersTest {

    private static final Metadata.Key<String> TE_KEY =
            Metadata.Key.of("te", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> ERROR_DETAIL_KEY =
            Metadata.Key.of("error-detail", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> GRPC_STATUS_KEY =
            Metadata.Key.of("grpc-status", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> GRPC_MESSAGE_KEY =
            Metadata.Key.of("grpc-message", Metadata.ASCII_STRING_MARSHALLER);
    private static final int FLOOD_MESSAGE_COUNT = 128;
    private static final int BIDI_MESSAGE_COUNT = 128;
    private static final Strings.StringMessage FLOOD_MESSAGE = Strings.StringMessage.newBuilder()
            .setText("x".repeat(128 * 1024))
            .build();

    private static final AtomicReference<Metadata> capturedMetadata = new AtomicReference<>();
    private static final AtomicInteger floodMessages = new AtomicInteger();
    private static final AtomicInteger echoMessages = new AtomicInteger();
    private static final AtomicReference<CountDownLatch> floodStarted = new AtomicReference<>();
    private static final AtomicReference<CountDownLatch> floodCompleted = new AtomicReference<>();
    private static final AtomicReference<CountDownLatch> resetRelease = new AtomicReference<>();
    private static final AtomicReference<CountDownLatch> resetThrown = new AtomicReference<>();
    private static final AtomicReference<CountDownLatch> timeoutRelease = new AtomicReference<>();
    private static final AtomicReference<CountDownLatch> pausedStarted = new AtomicReference<>();
    private static final AtomicReference<CountDownLatch> pausedRelease = new AtomicReference<>();
    private static final AtomicInteger pausedConsumed = new AtomicInteger();

    private final GrpcClient grpcClient;
    private final GrpcServiceDescriptor serviceDescriptor;
    private final int serverPort;

    GrpcTeTrailersTest(WebServer server) {
        this.serverPort = server.port();
        this.grpcClient = GrpcClient.builder()
                .tls(t -> t.enabled(false))
                .baseUri("http://localhost:" + serverPort)
                .build();
        this.serviceDescriptor = GrpcServiceDescriptor.builder()
                .serviceName("StringService")
                .putMethod("Upper",
                        GrpcClientMethodDescriptor.unary("StringService", "Upper")
                                .requestType(Strings.StringMessage.class)
                                .responseType(Strings.StringMessage.class)
                                .build())
                .putMethod("Split",
                        GrpcClientMethodDescriptor.serverStreaming("StringService", "Split")
                                .requestType(Strings.StringMessage.class)
                                .responseType(Strings.StringMessage.class)
                                .build())
                .putMethod("Flood",
                        GrpcClientMethodDescriptor.serverStreaming("StringService", "Flood")
                                .requestType(Strings.StringMessage.class)
                                .responseType(Strings.StringMessage.class)
                                .build())
                .putMethod("ResetAfterOne",
                        GrpcClientMethodDescriptor.serverStreaming("StringService", "ResetAfterOne")
                                .requestType(Strings.StringMessage.class)
                                .responseType(Strings.StringMessage.class)
                                .build())
                .putMethod("PauseAfterTwo",
                        GrpcClientMethodDescriptor.serverStreaming("StringService", "PauseAfterTwo")
                                .requestType(Strings.StringMessage.class)
                                .responseType(Strings.StringMessage.class)
                                .build())
                .putMethod("Echo",
                        GrpcClientMethodDescriptor.bidirectional("StringService", "Echo")
                                .requestType(Strings.StringMessage.class)
                                .responseType(Strings.StringMessage.class)
                                .build())
                .putMethod("PausedEcho",
                        GrpcClientMethodDescriptor.bidirectional("StringService", "PausedEcho")
                                .requestType(Strings.StringMessage.class)
                                .responseType(Strings.StringMessage.class)
                                .build())
                .build();
    }

    @SetUpRoute
    static void setUpRoute(GrpcRouting.Builder routing) {
        routing.intercept(new MetadataCapturingInterceptor())
                .unary(Strings.getDescriptor(), "StringService", "Upper", GrpcTeTrailersTest::upper)
                .serverStream(Strings.getDescriptor(), "StringService", "Split", GrpcTeTrailersTest::fail)
                .serverStream(Strings.getDescriptor(), "StringService", "Flood", GrpcTeTrailersTest::flood)
                .serverStream(Strings.getDescriptor(), "StringService", "ResetAfterOne", GrpcTeTrailersTest::resetAfterOne)
                .serverStream(Strings.getDescriptor(), "StringService", "PauseAfterTwo", GrpcTeTrailersTest::pauseAfterTwo)
                .bidi(Strings.getDescriptor(), "StringService", "Echo", GrpcTeTrailersTest::echo)
                .bidi(Strings.getDescriptor(), "StringService", "PausedEcho", GrpcTeTrailersTest::pausedEcho);
    }

    @Test
    void teTrailersHeaderIsSentToServer() {
        grpcClient.serviceClient(serviceDescriptor)
                .unary("Upper", Strings.StringMessage.newBuilder().setText("hello").build());

        Metadata metadata = capturedMetadata.get();
        assertThat("server interceptor was not called", metadata, notNullValue());
        assertThat(metadata.get(TE_KEY), is("trailers"));
    }

    @Test
    void resourceStreamsValidateArgumentsBeforeMethodLookup() {
        GrpcServiceClient client = grpcClient.serviceClient(serviceDescriptor);
        AtomicInteger closed = new AtomicInteger();

        assertThrows(NullPointerException.class, () -> client.serverStreaming("Missing", null));
        assertThrows(NullPointerException.class, () -> client.clientStreaming("Missing", null));
        assertThrows(NullPointerException.class, () -> client.bidirectional("Missing", null));
        assertThrows(NullPointerException.class,
                     () -> client.clientStreaming(null, Stream.empty().onClose(closed::incrementAndGet)));
        assertThrows(NullPointerException.class,
                     () -> client.bidirectional(null, Stream.empty().onClose(closed::incrementAndGet)));
        assertThat("a null method name is rejected before request ownership transfers", closed.get(), is(0));
    }

    @Test
    void peerStatusDescriptionAndTrailingMetadataArePreserved() {
        StatusRuntimeException error = assertThrows(StatusRuntimeException.class,
                                                    () -> grpcClient.serviceClient(serviceDescriptor)
                                                            .<Strings.StringMessage, Strings.StringMessage>serverStream(
                                                                    "Split",
                                                                    Strings.StringMessage.getDefaultInstance())
                                                            .hasNext());

        assertThat(error.getStatus().toString(), error.getStatus().getCode(), is(Status.Code.PERMISSION_DENIED));
        assertThat(error.getStatus().getDescription(), is("permission denied by test"));
        assertThat(error.getTrailers(), notNullValue());
        assertThat(error.getTrailers().get(ERROR_DETAIL_KEY), is("policy-42"));
        assertThat(error.getTrailers().containsKey(GRPC_STATUS_KEY), is(false));
        assertThat(error.getTrailers().containsKey(GRPC_MESSAGE_KEY), is(false));
    }

    @Test
    void responseFloodStopsAtHttp2FlowControlWithoutClientDemand() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        floodMessages.set(0);
        floodStarted.set(started);
        floodCompleted.set(completed);

        try (Stream<Strings.StringMessage> ignored = grpcClient.serviceClient(serviceDescriptor)
                .serverStreaming("Flood", Strings.StringMessage.getDefaultInstance())) {
            assertThat("server stream started", started.await(10, TimeUnit.SECONDS), is(true));
            assertThat("server must not drain the flood without client demand",
                       completed.await(2, TimeUnit.SECONDS),
                       is(false));
            assertThat("server response production must be flow controlled",
                       floodMessages.get() < FLOOD_MESSAGE_COUNT,
                       is(true));
        }
    }

    @Test
    void responseDemandTimeoutRemainsCancelled() throws Exception {
        CountDownLatch callClosed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Status> closedStatus = new AtomicReference<>();
        timeoutRelease.set(release);
        GrpcClient timeoutClient = GrpcClient.builder()
                .tls(t -> t.enabled(false))
                .baseUri("http://localhost:" + serverPort)
                .protocolConfig(it -> it.nextRequestWaitTime(Duration.ofMillis(50)))
                .build();
        GrpcServiceDescriptor timeoutServiceDescriptor = GrpcServiceDescriptor.builder(serviceDescriptor)
                .addInterceptor(new ClientInterceptor() {
                    @Override
                    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                                               CallOptions callOptions,
                                                                               Channel next) {
                        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
                            @Override
                            public void start(Listener<RespT> responseListener, Metadata headers) {
                                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(
                                        responseListener) {
                                    @Override
                                    public void onClose(Status status, Metadata trailers) {
                                        super.onClose(status, trailers);
                                        closedStatus.set(status);
                                        callClosed.countDown();
                                    }
                                }, headers);
                            }
                        };
                    }
                })
                .build();

        try (Stream<Strings.StringMessage> responses = timeoutClient.serviceClient(timeoutServiceDescriptor)
                .serverStreaming("PauseAfterTwo", Strings.StringMessage.getDefaultInstance())) {
            try {
                Iterator<Strings.StringMessage> iterator = responses.iterator();
                assertThat(iterator.next().getText(), is("one"));
                assertThat("client call closed after response demand timeout",
                           callClosed.await(10, TimeUnit.SECONDS),
                           is(true));
                assertThat(closedStatus.get().getCode(), is(Status.Code.CANCELLED));

                StatusRuntimeException failure = assertTimeoutPreemptively(Duration.ofSeconds(10),
                                                                           () -> assertThrows(StatusRuntimeException.class,
                                                                                              iterator::hasNext));
                assertThat(failure.getStatus().getCode(), is(Status.Code.CANCELLED));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void resetBetweenResponseDemandsIsReportedAsFailure() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch thrown = new CountDownLatch(1);
        resetRelease.set(release);
        resetThrown.set(thrown);

        try (Stream<Strings.StringMessage> responses = grpcClient.serviceClient(serviceDescriptor)
                .serverStreaming("ResetAfterOne", Strings.StringMessage.getDefaultInstance())) {
            Iterator<Strings.StringMessage> iterator = responses.iterator();
            assertThat(iterator.next().getText(), is("one"));

            release.countDown();
            assertThat("server reset initiated", thrown.await(10, TimeUnit.SECONDS), is(true));
            Thread.sleep(500);

            StatusRuntimeException failure = assertThrows(StatusRuntimeException.class, iterator::hasNext);
            assertThat(failure.getStatus().getCode(), is(Status.Code.INTERNAL));
            assertThat(failure.getStatus().getDescription(), containsString("Reset of"));
        }
    }

    @Test
    void resetBeforeResponseHeadersIsReportedAsFailure() {
        Strings.StringMessage request = Strings.StringMessage.newBuilder()
                .setText("reset-before-headers")
                .build();

        try (Stream<Strings.StringMessage> responses = grpcClient.serviceClient(serviceDescriptor)
                .serverStreaming("ResetAfterOne", request)) {
            StatusRuntimeException failure = assertThrows(StatusRuntimeException.class,
                                                          responses.iterator()::hasNext);
            assertThat(failure.getStatus().getCode(), is(Status.Code.CANCELLED));
            assertThat(failure.getStatus().getDescription(), containsString("Reset of"));
        }
    }

    @Test
    void bidirectionalStreamCompletesAfterResponses() {
        Strings.StringMessage hello = Strings.StringMessage.newBuilder().setText("hello").build();
        Strings.StringMessage world = Strings.StringMessage.newBuilder().setText("world").build();

        for (int i = 0; i < 10; i++) {
            try (Stream<Strings.StringMessage> responses = grpcClient.serviceClient(serviceDescriptor)
                    .bidirectional("Echo", Stream.of(hello, world))) {
                assertThat(responses.map(Strings.StringMessage::getText).toList(), is(List.of("hello", "world")));
            }
        }
    }

    @Test
    void bidirectionalStreamCompletesAfterFlowControlledResponses() {
        Strings.StringMessage request = Strings.StringMessage.newBuilder()
                .setText("x".repeat(1024))
                .build();
        AtomicInteger responsesReceived = new AtomicInteger();
        echoMessages.set(0);

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            try (Stream<Strings.StringMessage> responses = grpcClient.serviceClient(serviceDescriptor)
                    .bidirectional("Echo", Stream.generate(() -> request).limit(BIDI_MESSAGE_COUNT))) {
                responses.forEach(ignored -> responsesReceived.incrementAndGet());
            }
        }, () -> "server messages: " + echoMessages.get() + ", client messages: " + responsesReceived.get());
        assertThat("server messages", echoMessages.get(), is(BIDI_MESSAGE_COUNT));
        assertThat(responsesReceived.get(), is(BIDI_MESSAGE_COUNT));
    }

    @Test
    void bidirectionalStreamCompletesWithSingleThreadClientExecutor() {
        Strings.StringMessage first = Strings.StringMessage.newBuilder().setText("first").build();
        Strings.StringMessage second = Strings.StringMessage.newBuilder().setText("second").build();
        CountDownLatch firstResponse = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger();
        Stream<Strings.StringMessage> requestStream = Stream.generate(() -> {
            if (requests.getAndIncrement() == 0) {
                return first;
            }
            try {
                if (!firstResponse.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("First response was not received.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while awaiting the first response.", e);
            }
            return second;
        }).limit(2);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            GrpcClient boundedClient = GrpcClient.builder()
                    .tls(t -> t.enabled(false))
                    .baseUri("http://localhost:" + serverPort)
                    .executor(executor)
                    .build();

            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                try (Stream<Strings.StringMessage> responses = boundedClient.serviceClient(serviceDescriptor)
                        .bidirectional("Echo", requestStream)) {
                    assertThat(responses.peek(_ -> firstResponse.countDown())
                                       .map(Strings.StringMessage::getText)
                                       .toList(),
                               is(List.of("first", "second")));
                }
            });
        }
    }

    @Test
    void invalidStreamingMethodClosesTransferredRequestSource() {
        AtomicReference<Boolean> sourceClosed = new AtomicReference<>(false);
        Stream<Strings.StringMessage> requests = Stream.<Strings.StringMessage>empty()
                .onClose(() -> sourceClosed.set(true));

        assertThrows(NoSuchElementException.class,
                     () -> grpcClient.serviceClient(serviceDescriptor).bidirectional("Missing", requests));
        assertThat(sourceClosed.get(), is(true));
    }

    @Test
    void bidirectionalRequestsStopWithoutServerDemand() throws InterruptedException {
        Strings.StringMessage request = Strings.StringMessage.newBuilder()
                .setText("x".repeat(32 * 1024))
                .build();
        AtomicInteger requestsProduced = new AtomicInteger();
        AtomicInteger responsesReceived = new AtomicInteger();
        AtomicBoolean requestProductionResumed = new AtomicBoolean();
        AtomicBoolean firstResponseReceived = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Stream<Strings.StringMessage>> responseStream = new AtomicReference<>();
        AtomicReference<CountDownLatch> nextRequestProduced = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch requestProductionStarted = new CountDownLatch(1);
        CountDownLatch postReleaseProgress = new CountDownLatch(2);
        pausedStarted.set(started);
        pausedRelease.set(release);
        pausedConsumed.set(0);

        Thread call = Thread.startVirtualThread(() -> {
            try (Stream<Strings.StringMessage> responses = grpcClient.serviceClient(serviceDescriptor)
                    .bidirectional("PausedEcho",
                                   Stream.generate(() -> {
                                       requestsProduced.incrementAndGet();
                                       requestProductionStarted.countDown();
                                       CountDownLatch requestProduced = nextRequestProduced.get();
                                       if (requestProduced != null) {
                                           requestProduced.countDown();
                                       }
                                       if (release.getCount() == 0
                                               && requestProductionResumed.compareAndSet(false, true)) {
                                           postReleaseProgress.countDown();
                                       }
                                       return request;
                                   }).limit(BIDI_MESSAGE_COUNT))) {
                responseStream.set(responses);
                try {
                    Iterator<Strings.StringMessage> iterator = responses.iterator();
                    while (iterator.hasNext()) {
                        iterator.next();
                        responsesReceived.incrementAndGet();
                        if (firstResponseReceived.compareAndSet(false, true)) {
                            postReleaseProgress.countDown();
                        }
                    }
                } finally {
                    responseStream.compareAndSet(responses, null);
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        });

        boolean postReleaseProgressObserved;
        boolean callCompleted;
        try {
            try {
                assertThat("paused endpoint started", started.await(10, TimeUnit.SECONDS), is(true));
                assertThat("client request production started",
                           requestProductionStarted.await(10, TimeUnit.SECONDS),
                           is(true));

                boolean requestProductionStopped = false;
                long stopDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                do {
                    CountDownLatch requestProduced = new CountDownLatch(1);
                    nextRequestProduced.set(requestProduced);
                    requestProductionStopped = !requestProduced.await(1, TimeUnit.SECONDS);
                } while (!requestProductionStopped
                        && requestsProduced.get() < BIDI_MESSAGE_COUNT
                        && System.nanoTime() < stopDeadline);
                nextRequestProduced.set(null);

                assertThat("client request production must stop without server demand; produced="
                                   + requestsProduced.get(),
                           requestProductionStopped,
                           is(true));
                assertThat("client request production must stop without server demand; produced="
                                   + requestsProduced.get(),
                           requestsProduced.get() < BIDI_MESSAGE_COUNT,
                           is(true));
            } finally {
                release.countDown();
            }
            postReleaseProgressObserved = postReleaseProgress.await(30, TimeUnit.SECONDS);
            if (postReleaseProgressObserved) {
                call.join(TimeUnit.SECONDS.toMillis(10));
            }
            callCompleted = !call.isAlive();
        } finally {
            if (call.isAlive()) {
                Stream<Strings.StringMessage> responses = responseStream.get();
                if (responses != null) {
                    Thread close = Thread.startVirtualThread(responses::close);
                    close.join(TimeUnit.SECONDS.toMillis(10));
                    if (close.isAlive()) {
                        close.interrupt();
                    }
                    call.join(TimeUnit.SECONDS.toMillis(10));
                }
            }
            if (call.isAlive()) {
                call.interrupt();
                call.join(TimeUnit.SECONDS.toMillis(10));
            }
        }

        assertThat("request production and response consumption resumed after server demand; produced="
                           + requestsProduced.get()
                           + ", consumed=" + pausedConsumed.get()
                           + ", responses=" + responsesReceived.get(),
                   postReleaseProgressObserved,
                   is(true));
        assertThat("request production resumed after server demand", requestProductionResumed.get(), is(true));
        assertThat("response received after server demand", firstResponseReceived.get(), is(true));
        assertThat("bidirectional call completed before cleanup; produced=" + requestsProduced.get()
                           + ", consumed=" + pausedConsumed.get()
                           + ", responses=" + responsesReceived.get(),
                   callCompleted,
                   is(true));
        assertThat("bidirectional call failure", failure.get(), nullValue());
    }

    private static void upper(Strings.StringMessage req, StreamObserver<Strings.StringMessage> observer) {
        observer.onNext(Strings.StringMessage.newBuilder()
                .setText(req.getText().toUpperCase(Locale.ROOT))
                .build());
        observer.onCompleted();
    }

    private static void fail(Strings.StringMessage req, StreamObserver<Strings.StringMessage> observer) {
        Metadata trailers = new Metadata();
        trailers.put(ERROR_DETAIL_KEY, "policy-42");
        observer.onError(Status.PERMISSION_DENIED
                                 .withDescription("permission%20denied%20by%20test")
                                 .asRuntimeException(trailers));
    }

    private static void flood(Strings.StringMessage req, StreamObserver<Strings.StringMessage> observer) {
        floodStarted.get().countDown();
        for (int i = 0; i < FLOOD_MESSAGE_COUNT; i++) {
            floodMessages.incrementAndGet();
            observer.onNext(FLOOD_MESSAGE);
        }
        observer.onCompleted();
        floodCompleted.get().countDown();
    }

    private static void resetAfterOne(Strings.StringMessage req, StreamObserver<Strings.StringMessage> observer) {
        if (req.getText().equals("reset-before-headers")) {
            throw new CloseConnectionException("Reset before response headers for test",
                                               new Http2Exception(Http2ErrorCode.CANCEL,
                                                                  "Reset before response headers for test"));
        }
        observer.onNext(Strings.StringMessage.newBuilder().setText("one").build());
        try {
            resetRelease.get().await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        resetThrown.get().countDown();
        throw new CloseConnectionException("Reset response stream for test");
    }

    private static void pauseAfterTwo(Strings.StringMessage req, StreamObserver<Strings.StringMessage> observer) {
        observer.onNext(Strings.StringMessage.newBuilder().setText("one").build());
        observer.onNext(Strings.StringMessage.newBuilder().setText("two").build());
        try {
            timeoutRelease.get().await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        observer.onCompleted();
    }

    private static StreamObserver<Strings.StringMessage> echo(StreamObserver<Strings.StringMessage> observer) {
        return GrpcStreams.bidirectional(requests -> requests.peek(ignored -> echoMessages.incrementAndGet()), observer);
    }

    private static StreamObserver<Strings.StringMessage> pausedEcho(StreamObserver<Strings.StringMessage> observer) {
        return GrpcStreams.bidirectional(requests -> {
            pausedStarted.get().countDown();
            try {
                if (!pausedRelease.get().await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release paused gRPC request stream");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to release paused gRPC request stream", e);
            }
            return requests.peek(ignored -> pausedConsumed.incrementAndGet());
        }, observer);
    }

    private static class MetadataCapturingInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            capturedMetadata.set(headers);
            return next.startCall(call, headers);
        }
    }
}
