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

package io.helidon.webclient.http3;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.MediaContext;
import io.helidon.webclient.api.ClientUri;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3ClientResponseImplTest {

    @Test
    void cleanupRunsBeforeSuccessfulLifecycleCompletion() {
        List<String> events = new ArrayList<>();
        CompletableFuture<Void> complete = new CompletableFuture<>();
        complete.whenComplete((_, _) -> events.add("complete"));
        Http3ClientResponseImpl response = response(InputStream.nullInputStream(),
                                                    complete,
                                                    new CompletableFuture<>(),
                                                    () -> events.add("cleanup"),
                                                    _ -> { });

        response.close();
        response.close();

        assertThat(events, contains("cleanup", "complete"));
        assertThat(complete.isCompletedExceptionally(), is(false));
    }

    @Test
    void cleanupFailureFailsLifecycle() throws Exception {
        IllegalStateException cleanupFailure = new IllegalStateException("simulated cleanup failure");
        AtomicInteger cleanupCalls = new AtomicInteger();
        CompletableFuture<Void> complete = new CompletableFuture<>();
        CompletableFuture<ClientResponseTrailers> trailers = new CompletableFuture<>();
        Http3ClientResponseImpl response = response(InputStream.nullInputStream(),
                                                    complete,
                                                    trailers,
                                                    () -> {
                                                        cleanupCalls.incrementAndGet();
                                                        throw cleanupFailure;
                                                    },
                                                    _ -> { });

        assertThat(assertThrows(IllegalStateException.class, response::close), sameInstance(cleanupFailure));
        response.close();

        ExecutionException completeFailure = assertThrows(ExecutionException.class, complete::get);
        ExecutionException trailerFailure = assertThrows(ExecutionException.class, trailers::get);
        assertThat(completeFailure.getCause(), sameInstance(cleanupFailure));
        assertThat(trailerFailure.getCause(), sameInstance(cleanupFailure));
        assertThat(cleanupCalls.get(), is(1));
    }

    @Test
    void readIOExceptionFailsLifecycleAndRunsFailureCleanupOnce() throws Exception {
        IOException readCause = new IOException("simulated read failure");
        InputStream inputStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw readCause;
            }
        };
        AtomicInteger normalCleanupCalls = new AtomicInteger();
        AtomicInteger failureCleanupCalls = new AtomicInteger();
        AtomicReference<Throwable> observedFailure = new AtomicReference<>();
        CompletableFuture<Void> complete = new CompletableFuture<>();
        CompletableFuture<ClientResponseTrailers> trailers = new CompletableFuture<>();
        Http3ClientResponseImpl response = response(inputStream,
                                                    complete,
                                                    trailers,
                                                    normalCleanupCalls::incrementAndGet,
                                                    failure -> {
                                                        failureCleanupCalls.incrementAndGet();
                                                        observedFailure.set(failure);
                                                    });

        UncheckedIOException readFailure = assertThrows(UncheckedIOException.class,
                                                        () -> response.entity().inputStream().read());
        response.close();

        ExecutionException completeFailure = assertThrows(ExecutionException.class, complete::get);
        ExecutionException trailerFailure = assertThrows(ExecutionException.class, trailers::get);
        assertThat(readFailure.getCause(), sameInstance(readCause));
        assertThat(observedFailure.get(), sameInstance(readFailure));
        assertThat(completeFailure.getCause(), sameInstance(readFailure));
        assertThat(trailerFailure.getCause(), sameInstance(readFailure));
        assertThat(failureCleanupCalls.get(), is(1));
        assertThat(normalCleanupCalls.get(), is(0));
    }

    @Test
    void interruptedTrailerWaitFailsLifecycleAndRunsFailureCleanupOnce() throws Exception {
        AtomicInteger normalCleanupCalls = new AtomicInteger();
        AtomicInteger failureCleanupCalls = new AtomicInteger();
        AtomicReference<Throwable> observedFailure = new AtomicReference<>();
        CompletableFuture<Void> complete = new CompletableFuture<>();
        CompletableFuture<ClientResponseTrailers> trailers = new CompletableFuture<>();
        Http3ClientResponseImpl response = response(InputStream.nullInputStream(),
                                                    complete,
                                                    trailers,
                                                    normalCleanupCalls::incrementAndGet,
                                                    failure -> {
                                                        failureCleanupCalls.incrementAndGet();
                                                        observedFailure.set(failure);
                                                    });
        response.serviceEntityConsumed();

        IllegalStateException trailerFailure;
        try {
            Thread.currentThread().interrupt();
            trailerFailure = assertThrows(IllegalStateException.class, response::trailers);
            assertThat(Thread.currentThread().isInterrupted(), is(true));
        } finally {
            Thread.interrupted();
        }
        response.close();

        ExecutionException completeFailure = assertThrows(ExecutionException.class, complete::get);
        ExecutionException failedTrailers = assertThrows(ExecutionException.class, trailers::get);
        assertThat(trailerFailure.getCause(), instanceOf(InterruptedException.class));
        assertThat(observedFailure.get(), sameInstance(trailerFailure));
        assertThat(completeFailure.getCause(), sameInstance(trailerFailure));
        assertThat(failedTrailers.getCause(), sameInstance(trailerFailure));
        assertThat(failureCleanupCalls.get(), is(1));
        assertThat(normalCleanupCalls.get(), is(0));
    }

    @Test
    void exceptionalTrailersFailLifecycleAndRunFailureCleanupOnce() throws Exception {
        IllegalStateException expected = new IllegalStateException("simulated trailer failure");
        AtomicInteger normalCleanupCalls = new AtomicInteger();
        AtomicInteger failureCleanupCalls = new AtomicInteger();
        AtomicReference<Throwable> observedFailure = new AtomicReference<>();
        CompletableFuture<Void> complete = new CompletableFuture<>();
        CompletableFuture<ClientResponseTrailers> trailers = CompletableFuture.failedFuture(expected);
        Http3ClientResponseImpl response = response(InputStream.nullInputStream(),
                                                    complete,
                                                    trailers,
                                                    normalCleanupCalls::incrementAndGet,
                                                    failure -> {
                                                        failureCleanupCalls.incrementAndGet();
                                                        observedFailure.set(failure);
                                                    });
        response.serviceEntityConsumed();

        IllegalStateException actual = assertThrows(IllegalStateException.class, response::trailers);
        response.close();

        ExecutionException completeFailure = assertThrows(ExecutionException.class, complete::get);
        assertThat(actual, sameInstance(expected));
        assertThat(observedFailure.get(), sameInstance(expected));
        assertThat(completeFailure.getCause(), sameInstance(expected));
        assertThat(failureCleanupCalls.get(), is(1));
        assertThat(normalCleanupCalls.get(), is(0));
    }

    private static Http3ClientResponseImpl response(InputStream inputStream,
                                                    CompletableFuture<Void> complete,
                                                    CompletableFuture<ClientResponseTrailers> trailers,
                                                    Runnable closeAction,
                                                    Consumer<Throwable> readFailureAction) {
        return new Http3ClientResponseImpl(Duration.ofSeconds(1),
                                           Http3Client.PROTOCOL_ID,
                                           Status.OK_200,
                                           ClientRequestHeaders.create(WritableHeaders.create()),
                                           ClientResponseHeaders.create(WritableHeaders.create()),
                                           trailers,
                                           inputStream,
                                           MediaContext.create(),
                                           ClientUri.create(URI.create("https://localhost/response")),
                                           complete,
                                           closeAction,
                                           readFailureAction,
                                           1024);
    }
}
