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

package io.helidon.webclient.tests.http3;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.quic.QuicCloseCommand;
import io.helidon.quic.QuicConnectionException;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.http3.Http3RawTestServer;

import org.junit.jupiter.api.Test;

import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3RequestProducerFailureTest {
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration RESPONSE_PROPAGATION_TIMEOUT = Duration.ofSeconds(1);
    private static final String PRODUCER_FAILURE = "local request producer failed";
    private static final String CONNECTION_ID_PATH = "/connection-id";
    private static final String CONNECTION_FAILURE_PATH = "/connection-failure";
    private static final String CONCURRENT_PATH = "/concurrent";
    private static final String PRODUCER_FAILURE_PATH = "/producer-failure";

    @Test
    void shouldPreserveConnectionFailureWhenStreamingProducerClosesOutput() throws Exception {
        AtomicReference<RuntimeException> writeFailure = new AtomicReference<>();
        AtomicReference<RuntimeException> producerFailure = new AtomicReference<>();
        try (Http3RawTestServer server = Http3RawTestServer.create((_, connection, _, _) -> {
                 connection.terminate(QuicCloseCommand.application(Http3ErrorCode.INTERNAL_ERROR.code(),
                                                                    "test connection failure"));
                 return null;
             })) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .build();

            try {
                RuntimeException failure = assertThrows(RuntimeException.class, () -> {
                    try (Http3ClientResponse _ = client.post(CONNECTION_FAILURE_PATH)
                            .sendExpectContinue(false)
                            .outputStream(outputStream -> {
                                try {
                                    try (outputStream) {
                                        byte[] chunk = new byte[16 * 1024];
                                        for (int i = 0; i < 1024; i++) {
                                            try {
                                                outputStream.write(chunk);
                                            } catch (RuntimeException e) {
                                                writeFailure.compareAndSet(null, e);
                                                throw e;
                                            }
                                        }
                                    }
                                } catch (RuntimeException e) {
                                    producerFailure.set(e);
                                    throw e;
                                }
                            })) {
                    }
                });

                RuntimeException observedWriteFailure = writeFailure.get();
                assertThat(observedWriteFailure, notNullValue());
                assertThat(observedWriteFailure, instanceOf(QuicConnectionException.class));
                assertThat(producerFailure.get(), sameInstance(observedWriteFailure));

                List<Throwable> failureGraph = failureGraph(failure);
                assertThat(failureGraph.stream().anyMatch(QuicConnectionException.class::isInstance), is(true));
                assertThat(failureGraph.stream().anyMatch(it -> it instanceof IllegalArgumentException
                                && "Self-suppression not permitted".equals(it.getMessage())),
                           is(false));
                assertThat(failureGraph(producerFailure.get()).stream().anyMatch(it -> it instanceof IllegalArgumentException
                                && "Self-suppression not permitted".equals(it.getMessage())),
                           is(false));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldFailRequestWhenProducerFailsAfterResponseHeaders() throws Exception {
        CountDownLatch responseHeadersDispatched = new CountDownLatch(1);
        CountDownLatch allowProducerFailure = new CountDownLatch(1);
        CountDownLatch producerFailureObserved = new CountDownLatch(1);

        try (Http3RawTestServer server = Http3RawTestServer.create((_, _, _, stream) -> {
            stream.writeResponseHeaders(Status.OK_200.code(),
                                        WritableHeaders.create()
                                                .add(HeaderValues.create(HeaderNames.CONTENT_LENGTH, "0")),
                                        true);
            responseHeadersDispatched.countDown();
            try {
                if (!producerFailureObserved.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("Timed out waiting for the local request producer to fail.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for the local request producer to fail.", e);
            }
            return null;
        })) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .build();
            CompletableFuture<Throwable> requestOutcome = new CompletableFuture<>();
            CountDownLatch requestReturned = new CountDownLatch(1);

            try {
                Thread.ofVirtual().start(() -> {
                    try (Http3ClientResponse _ = client.post(PRODUCER_FAILURE_PATH)
                            .sendExpectContinue(false)
                            .outputStream(outputStream -> {
                                try {
                                    outputStream.write(1);
                                    outputStream.flush();
                                    if (!responseHeadersDispatched.await(AWAIT_TIMEOUT.toMillis(),
                                                                         TimeUnit.MILLISECONDS)) {
                                        throw new IllegalStateException("Timed out waiting for response headers.");
                                    }
                                    if (!allowProducerFailure.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                                        throw new IllegalStateException(
                                                "Timed out waiting to fail the request producer.");
                                    }
                                    throw new IllegalArgumentException(PRODUCER_FAILURE);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException(
                                            "Interrupted while coordinating the request producer.", e);
                                } finally {
                                    producerFailureObserved.countDown();
                                }
                            })) {
                        requestOutcome.complete(null);
                    } catch (Throwable t) {
                        requestOutcome.complete(t);
                    } finally {
                        requestReturned.countDown();
                    }
                });

                assertThat("Server should dispatch response headers",
                           responseHeadersDispatched.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                           is(true));
                boolean returnedBeforeProducerFailure = requestReturned.await(RESPONSE_PROPAGATION_TIMEOUT.toMillis(),
                                                                               TimeUnit.MILLISECONDS);
                allowProducerFailure.countDown();

                Throwable failure = requestOutcome.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                assertThat("Request must not return while its producer is still active",
                           returnedBeforeProducerFailure,
                           is(false));
                assertThat("Request should expose the local producer failure", failure, notNullValue());
                Throwable cause = failure;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                assertThat(cause, instanceOf(IllegalArgumentException.class));
                assertThat(cause.getMessage(), is(PRODUCER_FAILURE));
            } finally {
                allowProducerFailure.countDown();
                client.closeResource();
            }
        }
    }

    @Test
    void shouldRunRequestProducerInServiceRequestContext() throws Exception {
        String classifier = "http3-request-context";
        String expected = "context-value";
        Context context = Context.create();
        context.register(classifier, expected);
        AtomicReference<String> observed = new AtomicReference<>();

        try (Http3RawTestServer server = Http3RawTestServer.create((_, _, _, _) ->
                Http3RawTestServer.text(Status.OK_200.code(), "ok"))) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .build();

            try {
                Contexts.runInContext(context, () -> {
                    try (Http3ClientResponse response = client.post("/context").outputStream(outputStream -> {
                        observed.set(Contexts.context()
                                             .flatMap(current -> current.get(classifier, String.class))
                                             .orElse(null));
                        outputStream.close();
                    })) {
                        assertThat(response.status(), is(Status.OK_200));
                    }
                });
            } finally {
                client.closeResource();
            }
        }

        assertThat(observed.get(), is(expected));
    }

    @Test
    void shouldDispatchRequestWithSingleThreadRequestExecutor() throws Exception {
        ExecutorService requestExecutor = Executors.newSingleThreadExecutor();
        try (Http3RawTestServer server = Http3RawTestServer.create((_, _, _, _) ->
                Http3RawTestServer.text(Status.OK_200.code(), "ok"))) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .executor(requestExecutor)
                    .tls(server.clientTlsHttp3())
                    .build();

            try {
                CompletableFuture<String> responseBody = CompletableFuture.supplyAsync(() -> {
                    try (Http3ClientResponse response = client.post("/single-thread")
                            .readContinueTimeout(Duration.ofSeconds(30))
                            .outputStream(outputStream -> {
                                outputStream.write(new byte[16 * 1024]);
                                outputStream.close();
                            })) {
                        return response.as(String.class);
                    }
                });
                assertThat(responseBody.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is("ok"));
            } finally {
                client.closeResource();
            }
        } finally {
            requestExecutor.shutdownNow();
            requestExecutor.awaitTermination(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    @Test
    void shouldCompleteRequestWhenRequestExecutorRejectsProducer() throws Exception {
        RejectedExecutionException rejection = new RejectedExecutionException("test request executor rejection");
        RejectingExecutorService requestExecutor = new RejectingExecutorService(rejection);
        try (Http3RawTestServer server = Http3RawTestServer.create((_, _, _, _) ->
                Http3RawTestServer.text(Status.OK_200.code(), "unexpected"))) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .executor(requestExecutor)
                    .tls(server.clientTlsHttp3())
                    .build();

            try {
                RuntimeException failure = assertThrows(RuntimeException.class,
                                                        () -> client.get("/rejected").request());
                Throwable cause = failure;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                assertThat(cause, is(rejection));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldKeepMultiplexedSessionAfterLocalProducerFailure() throws Exception {
        CountDownLatch concurrentRequestOpened = new CountDownLatch(1);
        CountDownLatch allowConcurrentResponse = new CountDownLatch(1);
        CountDownLatch producerRequestOpened = new CountDownLatch(1);
        CountDownLatch producerFailureObserved = new CountDownLatch(1);
        CountDownLatch responseStopChecked = new CountDownLatch(1);
        AtomicReference<String> producerConnectionId = new AtomicReference<>();
        AtomicBoolean responseStopObserved = new AtomicBoolean();

        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, _, stream) -> {
            try {
                return switch (request.path().orElseThrow()) {
                    case CONNECTION_ID_PATH ->
                            Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId());
                    case CONCURRENT_PATH -> {
                        concurrentRequestOpened.countDown();
                        if (!allowConcurrentResponse.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                            throw new IllegalStateException("Timed out waiting to complete the concurrent request.");
                        }
                        yield Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId());
                    }
                    case PRODUCER_FAILURE_PATH -> {
                        producerConnectionId.set(connection.childSocketId());
                        producerRequestOpened.countDown();
                        if (!producerFailureObserved.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                            throw new IllegalStateException("Timed out waiting for the request producer to fail.");
                        }
                        long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
                        while (!stream.stopSendingReceived() && System.nanoTime() < deadline) {
                            TimeUnit.MILLISECONDS.sleep(1);
                        }
                        responseStopObserved.set(stream.stopSendingReceived());
                        responseStopChecked.countDown();
                        yield null;
                    }
                    default -> Http3RawTestServer.text(Status.NOT_FOUND_404.code(), request.path().orElseThrow());
                };
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while coordinating the HTTP/3 test server.", e);
            }
        })) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .build();

            try {
                String connectionId;
                try (Http3ClientResponse response = client.get(CONNECTION_ID_PATH).request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    connectionId = response.as(String.class);
                }

                CompletableFuture<String> concurrentResponse = new CompletableFuture<>();
                Thread.ofVirtual().start(() -> {
                    try (Http3ClientResponse response = client.get(CONCURRENT_PATH).request()) {
                        concurrentResponse.complete(response.as(String.class));
                    } catch (Throwable t) {
                        concurrentResponse.completeExceptionally(t);
                    }
                });
                assertThat("Concurrent request should open on the established session",
                           concurrentRequestOpened.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                           is(true));

                RuntimeException failure = assertThrows(RuntimeException.class, () -> {
                    try (Http3ClientResponse _ = client.post(PRODUCER_FAILURE_PATH)
                            .sendExpectContinue(false)
                            .outputStream(outputStream -> {
                                try {
                                    outputStream.write(1);
                                    outputStream.flush();
                                    if (!producerRequestOpened.await(AWAIT_TIMEOUT.toMillis(),
                                                                     TimeUnit.MILLISECONDS)) {
                                        throw new IllegalStateException(
                                                "Timed out waiting for the producer request to open.");
                                    }
                                    throw new IllegalArgumentException(PRODUCER_FAILURE);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException(
                                            "Interrupted while coordinating the request producer.", e);
                                } finally {
                                    producerFailureObserved.countDown();
                                }
                            })) {
                        // The local producer failure must fail this request.
                    }
                });
                Throwable cause = failure;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                assertThat(cause, instanceOf(IllegalArgumentException.class));
                assertThat(cause.getMessage(), is(PRODUCER_FAILURE));
                assertThat("Producer failure should occur on the established connection",
                           producerConnectionId.get(),
                           is(connectionId));
                assertThat("Server should observe response-direction cancellation",
                           responseStopChecked.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                           is(true));
                assertThat("A local producer failure should cancel the unused response direction",
                           responseStopObserved.get(),
                           is(true));

                allowConcurrentResponse.countDown();
                assertThat("An unrelated multiplexed request should survive the local producer failure",
                           concurrentResponse.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                           is(connectionId));

                try (Http3ClientResponse response = client.get(CONNECTION_ID_PATH).request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat("A healthy session should remain cached after a local producer failure",
                               response.as(String.class),
                               is(connectionId));
                }
            } finally {
                allowConcurrentResponse.countDown();
                producerFailureObserved.countDown();
                client.closeResource();
            }
        }
    }

    @Test
    void shouldNotInvokeProducerAfterClientClosesBeforeQueuedExecution() throws Exception {
        try (Http3RawTestServer server = Http3RawTestServer.create((request, _, _, _) ->
                Http3RawTestServer.text(Status.OK_200.code(), request.path().orElseThrow()))) {
            PausingExecutorService executor = new PausingExecutorService();
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .executor(executor)
                    .tls(server.clientTlsHttp3())
                    .build();
            AtomicInteger producerInvocations = new AtomicInteger();
            CompletableFuture<Throwable> requestOutcome = new CompletableFuture<>();

            try {
                try (Http3ClientResponse response = client.get(CONNECTION_ID_PATH).request()) {
                    assertThat(response.status(), is(Status.OK_200));
                }

                executor.pause();
                Thread.ofVirtual().start(() -> {
                    try (Http3ClientResponse _ = client.post(PRODUCER_FAILURE_PATH)
                            .outputStream(outputStream -> {
                                producerInvocations.incrementAndGet();
                                outputStream.close();
                            })) {
                        requestOutcome.complete(null);
                    } catch (Throwable t) {
                        requestOutcome.complete(t);
                    }
                });

                executor.taskPaused.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                client.closeResource();
                executor.resume();

                assertThat(requestOutcome.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), notNullValue());
                assertThat("A finished stream must not invoke its queued request producer",
                           producerInvocations.get(),
                           is(0));
            } finally {
                client.closeResource();
                executor.resume();
                executor.shutdownNow();
                executor.awaitTermination(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    private static List<Throwable> failureGraph(Throwable failure) {
        List<Throwable> failures = new ArrayList<>();
        failures.add(failure);
        for (int i = 0; i < failures.size(); i++) {
            Throwable current = failures.get(i);
            Throwable cause = current.getCause();
            if (cause != null && !failures.contains(cause)) {
                failures.add(cause);
            }
            for (Throwable suppressed : current.getSuppressed()) {
                if (!failures.contains(suppressed)) {
                    failures.add(suppressed);
                }
            }
        }
        return failures;
    }

    private static final class PausingExecutorService extends AbstractExecutorService {
        private final ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        private final ReentrantLock lock = new ReentrantLock();
        private final List<Runnable> pausedTasks = new ArrayList<>();
        private final CompletableFuture<Void> taskPaused = new CompletableFuture<>();
        private boolean paused;

        private void pause() {
            lock.lock();
            try {
                paused = true;
            } finally {
                lock.unlock();
            }
        }

        private void resume() {
            List<Runnable> tasks;
            lock.lock();
            try {
                paused = false;
                tasks = List.copyOf(pausedTasks);
                pausedTasks.clear();
            } finally {
                lock.unlock();
            }
            tasks.forEach(delegate::execute);
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            lock.lock();
            try {
                if (paused) {
                    pausedTasks.add(command);
                    taskPaused.complete(null);
                    return;
                }
            } finally {
                lock.unlock();
            }
            delegate.execute(command);
        }
    }

    private static final class RejectingExecutorService extends AbstractExecutorService {
        private final RejectedExecutionException rejection;
        private boolean shutdown;

        private RejectingExecutorService(RejectedExecutionException rejection) {
            this.rejection = rejection;
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            throw rejection;
        }
    }
}
