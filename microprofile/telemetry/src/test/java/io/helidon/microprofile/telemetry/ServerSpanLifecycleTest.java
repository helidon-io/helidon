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
package io.helidon.microprofile.telemetry;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import io.helidon.tracing.Baggage;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.WritableBaggage;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class ServerSpanLifecycleTest {

    @Test
    void successfulFinishedEventEndsSpanWithoutWebServerCallback() {
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle();
        RecordingSpan span = new RecordingSpan();
        RecordingScope requestScope = new RecordingScope();

        lifecycle.requestFiltered(requestScope);
        lifecycle.requestFinished(span, true);

        assertThat("Span ended", span.endCount.get(), is(1));
        assertThat("Span status", span.status, is(Span.Status.UNSET));
        assertThat("Request scope closed", requestScope.closeCount.get(), is(1));
    }

    @Test
    void unsuccessfulFinishedEventEndsSpanWithError() {
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle();
        RecordingSpan span = new RecordingSpan();

        lifecycle.requestFinished(span, false);

        assertThat("Span ended", span.endCount.get(), is(1));
        assertThat("Span status", span.status, is(Span.Status.ERROR));
        assertThat("Span failure", span.failure, nullValue());
    }

    @Test
    void repeatedFinishedEventsEndSpanOnce() {
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle();
        RecordingSpan span = new RecordingSpan();
        RecordingScope requestScope = new RecordingScope();

        lifecycle.requestFiltered(requestScope);
        lifecycle.requestFiltered(requestScope);
        IntStream.range(0, 100).parallel().forEach(index -> lifecycle.requestFinished(span, index < 100));

        assertThat("Span ended once", span.endCount.get(), is(1));
        assertThat("Request scope closed once", requestScope.closeCount.get(), is(1));
    }

    @Test
    void resourceMethodCanFinishBeforeRequest() {
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle();
        RecordingSpan span = new RecordingSpan();
        RecordingScope requestScope = new RecordingScope();

        lifecycle.requestFiltered(requestScope);
        lifecycle.resourceMethodStarted(span);
        lifecycle.resourceMethodFinished(span);

        assertThat("Request scope closed", requestScope.closeCount.get(), is(1));
        assertThat("Resource scope closed", span.resourceScope.closeCount.get(), is(1));
        assertThat("Span remains open", span.endCount.get(), is(0));

        lifecycle.requestFinished(span, true);

        assertThat("Span ended", span.endCount.get(), is(1));
    }

    @Test
    void requestCanFinishBeforeResourceMethod() {
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle();
        RecordingSpan span = new RecordingSpan();
        RecordingScope requestScope = new RecordingScope();

        lifecycle.requestFiltered(requestScope);
        lifecycle.resourceMethodStarted(span);
        lifecycle.requestFinished(span, true);

        assertThat("Request scope closed", requestScope.closeCount.get(), is(1));
        assertThat("Resource scope remains open", span.resourceScope.closeCount.get(), is(0));
        assertThat("Span remains open", span.endCount.get(), is(0));

        lifecycle.resourceMethodFinished(span);

        assertThat("Resource scope closed", span.resourceScope.closeCount.get(), is(1));
        assertThat("Span ended", span.endCount.get(), is(1));
    }

    @Test
    void responseFailureIsCapturedOnlyAfterResponseProcessingStarts() {
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle();
        RecordingSpan span = new RecordingSpan();
        RuntimeException mappedResourceFailure = new RuntimeException("mapped");
        RuntimeException responseFailure = new RuntimeException("response");

        lifecycle.responseFailure(mappedResourceFailure);
        lifecycle.responseProcessingStarted();
        lifecycle.responseFailure(responseFailure);
        lifecycle.requestFinished(span, true);

        assertThat("Response failure recorded", span.failure, is(responseFailure));
        assertThat("Span status", span.status, is(Span.Status.ERROR));
    }

    @Test
    void writerFailureIsCaptured() {
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle();
        RecordingSpan span = new RecordingSpan();
        RuntimeException writerFailure = new RuntimeException("writer");

        lifecycle.writerFailure(writerFailure);
        lifecycle.requestFinished(span, false);

        assertThat("Writer failure recorded", span.failure, is(writerFailure));
        assertThat("Span status", span.status, is(Span.Status.ERROR));
    }

    private static final class RecordingSpan implements Span {
        private static final SpanContext CONTEXT = new SpanContext() {
            @Override
            public String traceId() {
                return "trace";
            }

            @Override
            public String spanId() {
                return "span";
            }

            @Override
            public void asParent(Builder<?> spanBuilder) {
                Objects.requireNonNull(spanBuilder);
            }

            @Override
            public Baggage baggage() {
                return null;
            }
        };

        private final RecordingScope resourceScope = new RecordingScope();
        private final AtomicInteger endCount = new AtomicInteger();
        private volatile Status status = Status.UNSET;
        private volatile Throwable failure;

        @Override
        public Span tag(String key, String value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            return this;
        }

        @Override
        public Span tag(String key, Boolean value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            return this;
        }

        @Override
        public Span tag(String key, Number value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            return this;
        }

        @Override
        public void status(Status status) {
            this.status = status;
        }

        @Override
        public SpanContext context() {
            return CONTEXT;
        }

        @Override
        public void addEvent(String name, Map<String, ?> attributes) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(attributes);
        }

        @Override
        public void end() {
            endCount.incrementAndGet();
        }

        @Override
        public void end(Throwable t) {
            failure = t;
            endCount.incrementAndGet();
        }

        @Override
        public Scope activate() {
            return resourceScope;
        }

        @Override
        @SuppressWarnings("removal")
        public Span baggage(String key, String value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            return this;
        }

        @Override
        @SuppressWarnings("removal")
        public Optional<String> baggage(String key) {
            Objects.requireNonNull(key);
            return Optional.empty();
        }

        @Override
        public WritableBaggage baggage() {
            return null;
        }
    }

    private static final class RecordingScope implements Scope {
        private final Thread owner = Thread.currentThread();
        private final AtomicInteger closeCount = new AtomicInteger();

        @Override
        public void close() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Scope closed from a thread other than its owner");
            }
            closeCount.incrementAndGet();
        }

        @Override
        public boolean isClosed() {
            return closeCount.get() > 0;
        }
    }
}
