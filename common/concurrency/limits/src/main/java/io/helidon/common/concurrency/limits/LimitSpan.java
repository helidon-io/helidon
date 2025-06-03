/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.common.concurrency.limits;

import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;

/**
 * Manages a tracing span related to limit queueing.
 *
 * @param span {@link io.helidon.tracing.Span} tracking the limit-induced wait
 * @param scope {@link io.helidon.tracing.Scope} tracking the activated span
 */
record LimitSpan(Span span, Scope scope) {

    static LimitSpan create(Tracer tracer, String limitName) {
        if (tracer != null) {
            var spanBuilder = tracer.spanBuilder(limitName + "-limit-span");
            Span.current().ifPresent(cs -> cs.context().asParent(spanBuilder));
            var span = spanBuilder.start();
            return new LimitSpan(span, span.activate());
        }
        return new LimitSpan(null, null);
    }

    void close() {
        if (scope != null) {
            scope.close();
        }
        if (span != null) {
            span.end();
        }
    }

    void closeWithError() {
        if (scope != null) {
            scope.close();
        }
        if (span != null) {
            span.status(Span.Status.ERROR);
            span.addEvent("queue limit exceeded");
            span.end();
        }
    }
}
