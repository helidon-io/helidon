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
 * <p>
 * The interface declares default empty implementations for each method, allowing concrete implementations of the
 * interface to implement only the methods that are relevant for them.
 * <p>
 * Helidon locates and invokes span life cycle listeners using Java service loading. Make sure you create the file
 * {@code META-INF/services/io.helidon.tracing.spi.SpanLifeCycleListener} and add a line containing the fully-qualified
 * type name for each listener you implement.
 * <p>
 * Helidon invokes the applicable methods of a life cycle listener in the following order:
 * <table>
 *     <caption style="text-align:left">Order of Invocation of Listener Methods</caption>
 *
 *     <tbody>
 *         <tr>
 *         <th style="text-align:left;width:15%">Method</th>
 *         <th style="text-align:left">When invoked</th>
 *     </tr>
 *     <tr>
 *         <td>{@linkplain #beforeStart(io.helidon.tracing.SpanInfo.BuilderInfo) beforeStart}</td>
 *         <td>Before a span is started from its builder.</td>
 *     </tr>
 *     <tr>
 *         <td>{@linkplain #afterStart(io.helidon.tracing.SpanInfo) afterStart}</td>
 *         <td>After a span has started.</td>
 *     </tr>
 *     <tr>
 *         <td>{@linkplain #afterActivate(io.helidon.tracing.SpanInfo, io.helidon.tracing.SpanInfo.ScopeInfo)
 *         afterActivate †}</td>
 *         <td>After a span has been activated, creating a new scope in the process. </td>
 *     </tr>
 *     <tr>
 *         <td>{@linkplain #afterClose(io.helidon.tracing.SpanInfo, io.helidon.tracing.SpanInfo.ScopeInfo) afterClose †}</td>
 *         <td>After a scope has been closed.</td>
 *     </tr>
 *     <tr>
 *         <td>{@linkplain #afterEnd(io.helidon.tracing.SpanInfo) afterEnd (successful) *}</td>
 *         <td>After a span has ended successfully.</td>
 *     </tr>
 *     <tr>
 *         <td>{@linkplain #afterEnd(io.helidon.tracing.SpanInfo, Throwable) afterEnd (unsuccessful) *}</td>
 *         <td>After a span has ended unsuccessfully. </td>
 *     </tr>
 *     </tbody>
 *      <tfoot>
 *         <tr>
 *             <td colspan="2">† Not all spans are activated; it is up to the application or library code that creates and manages the span.
 *              As a result Helidon might not invoke your listener's {@code afterActivate} and {@code afterClose} methods for
 *              every span.
 *              <p>
 *              * The successful or unsuccessful nature of a span's end is not about whether the tracing or telemetry
 *              system failed to end the span. Rather, it indicates whether the code that ended the span indicated some error in
 *              the processing which the span represents.
 *             </td>
 *         </tr>
 *     </tfoot>
 * </table>
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
     * @param scope {@link SpanInfo.ScopeInfo} for the {@linkplain io.helidon.tracing.Scope scope} which resulted from activating
     *              the
     *              span
     */
    default void afterActivate(SpanInfo span, SpanInfo.ScopeInfo scope) {
    }

    /**
     * Invoked just after a {@linkplain io.helidon.tracing.Scope scope} has been closed.
     *
     * @param span  {@link io.helidon.tracing.SpanInfo} for the span for which a {@linkplain io.helidon.tracing.Scope scope} was
     *              closed
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
     * @param t    {@link java.lang.Throwable} indicating the problem associated with the ended span
     */
    default void afterEnd(SpanInfo span, Throwable t) {
    }
}
