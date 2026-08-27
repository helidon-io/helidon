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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;

final class ServerSpanLifecycle {
    static final String PROPERTY = ServerSpanLifecycle.class.getName();

    private static final int REQUEST_SCOPE_CLOSED = 1;
    private static final int RESOURCE_METHOD_STARTED = 1 << 1;
    private static final int RESOURCE_METHOD_FINISHED = 1 << 2;
    private static final int RESPONSE_PROCESSING_STARTED = 1 << 3;
    private static final int REQUEST_FINISHED = 1 << 4;
    private static final int RESPONSE_WRITTEN = 1 << 5;
    private static final int SPAN_ENDED = 1 << 6;

    private final AtomicInteger state = new AtomicInteger();
    private final AtomicReference<Scope> resourceScope = new AtomicReference<>();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    void requestFiltered(Scope requestScope) {
        int previous = state.getAndUpdate(value -> value | REQUEST_SCOPE_CLOSED);
        if ((previous & REQUEST_SCOPE_CLOSED) == 0) {
            requestScope.close();
        }
    }

    void resourceMethodStarted(Span span) {
        int previous = state.getAndUpdate(value -> value | RESOURCE_METHOD_STARTED);
        if ((previous & RESOURCE_METHOD_STARTED) != 0) {
            return;
        }

        Scope scope = span.activate();
        resourceScope.set(scope);
        if ((state.get() & RESOURCE_METHOD_FINISHED) != 0) {
            closeResourceScope();
        }
    }

    void resourceMethodFinished(Span span) {
        closeResourceScope();
        state.getAndUpdate(value -> value | RESOURCE_METHOD_FINISHED);
        tryEnd(span);
    }

    void responseProcessingStarted() {
        state.getAndUpdate(value -> value | RESPONSE_PROCESSING_STARTED);
    }

    void responseFailure(Throwable throwable) {
        if ((state.get() & RESPONSE_PROCESSING_STARTED) != 0 && throwable != null) {
            failure.compareAndSet(null, throwable);
        }
    }

    void writerFailure(Throwable throwable) {
        if (throwable != null) {
            failure.compareAndSet(null, throwable);
        }
    }

    void requestFinished(Span span, boolean responseWritten) {
        int completion = REQUEST_FINISHED | (responseWritten ? RESPONSE_WRITTEN : 0);
        state.getAndUpdate(value -> (value & REQUEST_FINISHED) == 0 ? value | completion : value);
        tryEnd(span);
    }

    private void closeResourceScope() {
        Scope scope = resourceScope.getAndSet(null);
        if (scope != null) {
            scope.close();
        }
    }

    private void tryEnd(Span span) {
        while (true) {
            int current = state.get();
            if ((current & SPAN_ENDED) != 0
                    || (current & REQUEST_FINISHED) == 0
                    || ((current & RESOURCE_METHOD_STARTED) != 0 && (current & RESOURCE_METHOD_FINISHED) == 0)) {
                return;
            }
            if (state.compareAndSet(current, current | SPAN_ENDED)) {
                end(span, current);
                return;
            }
        }
    }

    private void end(Span span, int completionState) {
        Throwable throwable = failure.get();
        if ((completionState & RESPONSE_WRITTEN) == 0 || throwable != null) {
            span.status(Span.Status.ERROR);
        }
        if (throwable == null) {
            span.end();
        } else {
            span.end(throwable);
        }
    }
}
