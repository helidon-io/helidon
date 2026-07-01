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

import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.grpc.core.ContextKeys;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcStreamsTest {
    @Test
    void serverStreamingClosesResponseBeforeCompleting() throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        CountDownLatch terminal = new CountDownLatch(1);

        GrpcStreams.serverStreaming(() -> Stream.of("response")
                .onClose(() -> events.add("close")), observer(events, terminal));

        assertThat("stream completed", terminal.await(10, TimeUnit.SECONDS), is(true));
        assertThat(events, is(List.of("response", "close", "complete")));
    }

    @Test
    void bidirectionalClosesResponseBeforeCompleting() throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        CountDownLatch terminal = new CountDownLatch(1);
        StreamObserver<String> requests = GrpcStreams.bidirectional(
                ignored -> Stream.of("response").onClose(() -> events.add("close")), observer(events, terminal));

        requests.onCompleted();

        assertThat("stream completed", terminal.await(10, TimeUnit.SECONDS), is(true));
        assertThat(events, is(List.of("response", "close", "complete")));
    }

    @Test
    void closeFailureDoesNotFollowCompletionWithError() throws Exception {
        CountDownLatch terminal = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        var observer = new StreamObserver<String>() {
            @Override
            public void onNext(String ignored) {
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                terminal.countDown();
            }

            @Override
            public void onCompleted() {
                terminal.countDown();
            }
        };

        GrpcStreams.serverStreaming(() -> Stream.of("response").onClose(() -> {
            throw new IllegalStateException("close failed");
        }), observer);

        assertThat("stream terminated", terminal.await(10, TimeUnit.SECONDS), is(true));
        assertThat(error.get(), instanceOf(IllegalStateException.class));
    }

    @Test
    void responseExhaustionDoesNotWaitForReadiness() throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        TestServerObserver<String> observer = new TestServerObserver<>(events, true, true, false);

        GrpcStreams.serverStreaming(() -> Stream.of("response")
                .onClose(() -> events.add("close")), observer);

        assertThat("stream completed", observer.terminal.await(10, TimeUnit.SECONDS), is(true));
        assertThat(events, is(List.of("response", "close", "complete")));
    }

    @Test
    void serverStreamingRetainsHelidonGrpcContext() throws Exception {
        Context context = Context.create();
        context.register("test-value", "helidon");
        AtomicReference<Context> threadContext = new AtomicReference<>();
        AtomicReference<Context> grpcContext = new AtomicReference<>();
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch terminal = new CountDownLatch(1);

        io.grpc.Context.current()
                .withValue(ContextKeys.HELIDON_CONTEXT, context)
                .run(() -> GrpcStreams.serverStreaming(() -> {
                    threadContext.set(Contexts.context().orElse(null));
                    grpcContext.set(ContextKeys.HELIDON_CONTEXT.get());
                    threadName.set(Thread.currentThread().getName());
                    return Stream.empty();
                }, observer(new CopyOnWriteArrayList<>(), terminal)));

        assertThat("stream completed", terminal.await(10, TimeUnit.SECONDS), is(true));
        assertThat(threadContext.get(), is(context));
        assertThat(grpcContext.get(), is(context));
        assertThat(threadName.get(), startsWith("helidon-grpc-server-stream-"));
    }

    @Test
    void cancellationUnblocksRequestStream() throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        TestServerObserver<String> observer = new TestServerObserver<>(events, true, false, false);
        CountDownLatch handlerEntered = new CountDownLatch(1);
        CountDownLatch handlerExited = new CountDownLatch(1);

        GrpcStreams.clientStreaming(requests -> {
            handlerEntered.countDown();
            try {
                requests.forEach(ignored -> { });
                return "response";
            } finally {
                handlerExited.countDown();
            }
        }, observer);

        assertThat("handler entered", handlerEntered.await(10, TimeUnit.SECONDS), is(true));
        observer.cancelByPeer();

        assertThat("handler exited", handlerExited.await(10, TimeUnit.SECONDS), is(true));
        assertThat(events, is(List.of()));
    }

    @Test
    void requestDeliveredBeforeTerminalErrorIsNotDiscarded() throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        CountDownLatch terminal = new CountDownLatch(1);
        CountDownLatch handlerEntered = new CountDownLatch(1);
        CountDownLatch consume = new CountDownLatch(1);
        AtomicReference<String> firstRequest = new AtomicReference<>();
        IllegalStateException failure = new IllegalStateException("request failed");

        StreamObserver<String> requests = GrpcStreams.clientStreaming(stream -> {
            handlerEntered.countDown();
            try {
                consume.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
            var iterator = stream.iterator();
            firstRequest.set(iterator.next());
            iterator.hasNext();
            return "response";
        }, observer(events, terminal));

        assertThat("handler entered", handlerEntered.await(10, TimeUnit.SECONDS), is(true));
        requests.onNext("request");
        requests.onError(failure);
        consume.countDown();

        assertThat("call terminated", terminal.await(10, TimeUnit.SECONDS), is(true));
        assertThat(firstRequest.get(), is("request"));
        assertThat(events, is(List.of("error:" + failure)));
    }

    @Test
    void cancellationInterruptsClientResponseSend() throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        CountDownLatch responseSendEntered = new CountDownLatch(1);
        CountDownLatch responseSendExited = new CountDownLatch(1);
        CountDownLatch responseSendInterrupted = new CountDownLatch(1);
        CountDownLatch never = new CountDownLatch(1);
        TestServerObserver<String> observer = new TestServerObserver<>(events, true, false, false, _ -> {
            responseSendEntered.countDown();
            try {
                never.await();
            } catch (InterruptedException e) {
                responseSendInterrupted.countDown();
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } finally {
                responseSendExited.countDown();
            }
        });

        StreamObserver<String> requests = GrpcStreams.clientStreaming(_ -> "response", observer);
        requests.onCompleted();
        assertThat("response send entered", responseSendEntered.await(10, TimeUnit.SECONDS), is(true));

        observer.cancelByPeer();

        assertThat("response send interrupted", responseSendInterrupted.await(10, TimeUnit.SECONDS), is(true));
        assertThat("response send exited", responseSendExited.await(10, TimeUnit.SECONDS), is(true));
        assertThat("cancelled call did not terminate through observer", observer.terminal.await(1, TimeUnit.SECONDS), is(false));
        assertThat(events, is(List.of()));
    }

    @Test
    void cancellationInterruptsBlockedResponseStream() throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        TestServerObserver<String> observer = new TestServerObserver<>(events, true, false, false);
        CountDownLatch responseRequested = new CountDownLatch(1);
        CountDownLatch responseClosed = new CountDownLatch(1);
        CountDownLatch handlerInterrupted = new CountDownLatch(1);
        CountDownLatch never = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();

        GrpcStreams.serverStreaming(() -> Stream.generate(() -> {
            responseRequested.countDown();
            try {
                never.await();
            } catch (InterruptedException e) {
                interrupted.set(true);
                handlerInterrupted.countDown();
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
            return "response";
        }).onClose(responseClosed::countDown), observer);

        assertThat("response requested", responseRequested.await(10, TimeUnit.SECONDS), is(true));
        observer.cancelByPeer();

        assertThat("response stream closed", responseClosed.await(10, TimeUnit.SECONDS), is(true));
        assertThat("handler interrupted", handlerInterrupted.await(10, TimeUnit.SECONDS), is(true));
        assertThat("handler thread interrupted", interrupted.get(), is(true));
        assertThat(events, is(List.of()));
    }

    @Test
    void cancellationClosesResponseSourceThatIgnoresInterruption() throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        TestServerObserver<String> observer = new TestServerObserver<>(events, true, false, false);
        CountDownLatch sourceEntered = new CountDownLatch(1);
        CountDownLatch sourceRelease = new CountDownLatch(1);
        CountDownLatch closeEntered = new CountDownLatch(1);
        CountDownLatch closeRelease = new CountDownLatch(1);
        CountDownLatch sourceClosed = new CountDownLatch(1);
        AtomicInteger closeCount = new AtomicInteger();
        Stream<String> source = StreamSupport.stream(new Spliterator<String>() {
            @Override
            public boolean tryAdvance(Consumer<? super String> action) {
                sourceEntered.countDown();
                while (sourceRelease.getCount() != 0) {
                    try {
                        sourceRelease.await();
                    } catch (InterruptedException ignored) {
                        // Simulate a response source whose cleanup hook, rather than interruption, releases it.
                    }
                }
                return false;
            }

            @Override
            public Spliterator<String> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return 0;
            }

            @Override
            public int characteristics() {
                return ORDERED | NONNULL;
            }
        }, false).onClose(() -> {
            closeCount.incrementAndGet();
            closeEntered.countDown();
            try {
                closeRelease.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                sourceRelease.countDown();
                sourceClosed.countDown();
            }
        });

        GrpcStreams.serverStreaming(() -> source, observer);
        assertThat("response source entered", sourceEntered.await(10, TimeUnit.SECONDS), is(true));

        observer.cancelByPeer();

        assertThat("response close entered", closeEntered.await(10, TimeUnit.SECONDS), is(true));
        assertThat("response close remains isolated from cancellation", sourceClosed.getCount(), is(1L));
        closeRelease.countDown();
        assertThat("response source closed", sourceClosed.await(10, TimeUnit.SECONDS), is(true));
        assertThat("response source closed once", closeCount.get(), is(1));
        assertThat(events, is(List.of()));
    }

    @Test
    void cancellationDuringResponseCleanupDoesNotComplete() throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        TestServerObserver<String> observer = new TestServerObserver<>(events, true, false, false);
        CountDownLatch closeEntered = new CountDownLatch(1);
        CountDownLatch closeRelease = new CountDownLatch(1);

        GrpcStreams.serverStreaming(() -> Stream.<String>empty().onClose(() -> {
            closeEntered.countDown();
            while (closeRelease.getCount() != 0) {
                try {
                    closeRelease.await();
                } catch (InterruptedException ignored) {
                    // Simulate cleanup that cannot finish until its resource is explicitly released.
                }
            }
        }), observer);

        assertThat("response cleanup entered", closeEntered.await(10, TimeUnit.SECONDS), is(true));
        observer.cancelByPeer();
        closeRelease.countDown();

        assertThat("cancelled response did not complete", observer.terminal.await(1, TimeUnit.SECONDS), is(false));
        assertThat(events, is(List.of()));
    }

    @Test
    void cancellationRegistrationFailureIsSynchronous() {
        AtomicBoolean handlerInvoked = new AtomicBoolean();
        TestServerObserver<String> observer = new TestServerObserver<>(new CopyOnWriteArrayList<>(),
                                                                       true,
                                                                       false,
                                                                       true);

        assertThrows(IllegalStateException.class,
                     () -> GrpcStreams.serverStreaming(() -> {
                         handlerInvoked.set(true);
                         return Stream.empty();
                     }, observer));

        assertThat(handlerInvoked.get(), is(false));
    }

    private static StreamObserver<String> observer(List<String> events, CountDownLatch terminal) {
        return new StreamObserver<>() {
            @Override
            public void onNext(String value) {
                events.add(value);
            }

            @Override
            public void onError(Throwable throwable) {
                events.add("error:" + throwable);
                terminal.countDown();
            }

            @Override
            public void onCompleted() {
                events.add("complete");
                terminal.countDown();
            }
        };
    }

    private static final class TestServerObserver<T> extends ServerCallStreamObserver<T> {
        private final List<String> events;
        private final AtomicBoolean ready;
        private final boolean stallAfterMessage;
        private final boolean failCancelRegistration;
        private final Consumer<T> onNext;
        private final CountDownLatch terminal = new CountDownLatch(1);
        private volatile boolean cancelled;
        private volatile Runnable onCancel;

        private TestServerObserver(List<String> events,
                                   boolean ready,
                                   boolean stallAfterMessage,
                                   boolean failCancelRegistration) {
            this(events, ready, stallAfterMessage, failCancelRegistration, _ -> { });
        }

        private TestServerObserver(List<String> events,
                                   boolean ready,
                                   boolean stallAfterMessage,
                                   boolean failCancelRegistration,
                                   Consumer<T> onNext) {
            this.events = events;
            this.ready = new AtomicBoolean(ready);
            this.stallAfterMessage = stallAfterMessage;
            this.failCancelRegistration = failCancelRegistration;
            this.onNext = onNext;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setOnCancelHandler(Runnable onCancel) {
            if (failCancelRegistration) {
                throw new IllegalStateException("call already initialized");
            }
            this.onCancel = onCancel;
        }

        @Override
        public void setCompression(String compression) {
        }

        @Override
        public boolean isReady() {
            return ready.get();
        }

        @Override
        public void setOnReadyHandler(Runnable onReady) {
        }

        @Override
        public void disableAutoRequest() {
            disableAutoInboundFlowControl();
        }

        @Override
        public void disableAutoInboundFlowControl() {
        }

        @Override
        public void request(int count) {
        }

        @Override
        public void setMessageCompression(boolean enable) {
        }

        @Override
        public void onNext(T value) {
            onNext.accept(value);
            events.add(value.toString());
            if (stallAfterMessage) {
                ready.set(false);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            events.add("error:" + throwable);
            terminal.countDown();
        }

        @Override
        public void onCompleted() {
            events.add("complete");
            terminal.countDown();
        }

        private void cancelByPeer() {
            cancelled = true;
            onCancel.run();
        }
    }
}
