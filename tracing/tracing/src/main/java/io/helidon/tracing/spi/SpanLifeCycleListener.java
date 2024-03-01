/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tracing.spi;

import io.helidon.tracing.SpanInfo;

/**
 * Behavior of listeners notified of span life cycle events.
 */
public interface SpanLifeCycleListener {

    /**
     * Invoked just prior to a {@linkplain io.helidon.tracing.Span span} being started from its
     * {@linkplain io.helidon.tracing.Span.Builder builder}.
     *
     * @param spanBuilder the {@link SpanInfo.BuilderInfo} for the builder about to be used to start a span
     */
    default void beforeStart(SpanInfo.BuilderInfo<?> spanBuilder) {
    }

    /**
     * Invoked just after a {@linkplain io.helidon.tracing.Span span} has been started.
     *
     * @param span {@link io.helidon.tracing.SpanInfo} for the newly-started span
     */
    default void afterStart(SpanInfo span) {
    }

    /**
     * Invoked just after a {@linkplain io.helidon.tracing.Span span} has been activated.
     *
     * @param span  {@link io.helidon.tracing.SpanInfo} for the just-activated span
     * @param scope {@link SpanInfo.ScopeInfo} for the {@linkplain io.helidon.tracing.Scope scope} which resulted from activating the
     *              span
     */
    default void afterActivate(SpanInfo span, SpanInfo.ScopeInfo scope) {
    }

    /**
     * Invoked just after a {@linkplain io.helidon.tracing.Scope scope} has been closed.
     *
     * @param span  {@link io.helidon.tracing.SpanInfo} for the span for which a {@linkplain io.helidon.tracing.Scope scope} was closed
     * @param scope {@link SpanInfo.ScopeInfo} for the just-closed {@linkplain io.helidon.tracing.Scope scope}
     */
    default void afterClose(SpanInfo span, SpanInfo.ScopeInfo scope) {
    }

    /**
     * Invoked just after a {@linkplain io.helidon.tracing.Span span} has been ended successfully.
     *
     * @param span {@link io.helidon.tracing.SpanInfo} for the just-ended span
     */
    default void afterEnd(SpanInfo span) {
    }

    /**
     * Invoked just after a {@linkplain io.helidon.tracing.Span span} has been ended unsuccessfully.
     *
     * @param span {@link io.helidon.tracing.SpanInfo} for the just-ended span
     * @param t {@link java.lang.Throwable} indicating the problem associated with the ended span
     */
    default void afterEnd(SpanInfo span, Throwable t) {
    }
}
