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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;

final class ServerSpanLifecycle {
    static final String PROPERTY = ServerSpanLifecycle.class.getName();

    private final AtomicBoolean completed = new AtomicBoolean();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    void failure(Throwable throwable) {
        if (throwable != null) {
            failure.compareAndSet(null, throwable);
        }
    }

    void complete(Span span, Scope scope, boolean responseWritten) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        Throwable throwable = failure.get();
        if (!responseWritten || throwable != null) {
            span.status(Span.Status.ERROR);
        }

        try {
            scope.close();
        } finally {
            if (throwable == null) {
                span.end();
            } else {
                span.end(throwable);
            }
        }
    }
}
