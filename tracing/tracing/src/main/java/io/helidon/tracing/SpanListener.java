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
package io.helidon.tracing;

/**
 * A listener notified of span lifecycle events.
 * <p>
 * All methods are {@code default} no-op methods, allowing concrete implementations
 * to implement only the methods that are relevant for them.
 * <p>
 * Helidon invokes the applicable methods of a listener in the following order:
 * <table>
 *     <caption style="text-align:left">Order of Invocation of Listener Methods</caption>
 *
 *     <tbody>
 *         <tr>
 *         <th style="text-align:left;width:15%">Method</th>
 *         <th style="text-align:left">When invoked</th>
 *     </tr>
 *     <tr>
 *         <td>{@linkplain #starting(io.helidon.tracing.Span.Builder) starting}</td>
 *         <td>Before a span is started from its builder.</td>
 *     </tr>
 *     <tr>
 *         <td>{@linkplain #started(io.helidon.tracing.Span) started}</td>
 *         <td>After a span has started.</td>
 *     </tr>
 *     <tr>
 *         <td>{@linkplain #activated(io.helidon.tracing.Span, io.helidon.tracing.Scope)
 *         activated} †</td>
 *         <td>After a span has been activated, creating a new scope in the process. </td>
 *     </tr>
 *     <tr>
 *         <td>{@linkplain #closed(io.helidon.tracing.Span, io.helidon.tracing.Scope) closed} †</td>
 *         <td>After a scope has been closed.</td>
 *     </tr>
 *     <tr>
 *         <td>{@linkplain #ended(io.helidon.tracing.Span) ended (successful)} *</td>
 *         <td>After a span has ended successfully.</td>
 *     </tr>
 *     <tr>
 *         <td>{@linkplain #ended(io.helidon.tracing.Span, Throwable) ended (unsuccessful)} *</td>
 *         <td>After a span has ended unsuccessfully. </td>
 *     </tr>
 *     </tbody>
 *      <tfoot>
 *         <tr>
 *             <td colspan="2">† Not all spans are activated; it is up to the application or library code that creates and manages the span.
 *              As a result Helidon might not invoke your listener's {@code activated} and {@code closed} methods for
 *              every span.
 *              <p>
 *              * The successful or unsuccessful nature of a span's end is not about whether the tracing or telemetry
 *              system failed to end the span. Rather, it indicates whether the code that ended the span indicated some error in
 *              the processing which the span represents.
 *             </td>
 *         </tr>
 *     </tfoot>
 * </table>
 * <p>
 *     When Helidon invokes the listener methods it passes implementations of the key span types ({@link Span.Builder},
 *     {@link Span}, {@link io.helidon.tracing.Scope}) which <em>do not</em> support lifecycle state changes.
 *     If a listener tries to start or end or activate a span,
 *     for example, Helidon throws an {@link java.lang.UnsupportedOperationException}.
 */
public interface SpanListener {

    /**
     * Invoked just prior to a {@linkplain io.helidon.tracing.Span span} being started from its
     * {@linkplain io.helidon.tracing.Span.Builder builder}.
     *
     * @param spanBuilder the {@link Span.Builder} for the builder about to be used to start a span
     * @throws java.lang.UnsupportedOperationException if the listener tries to start the span
     */
    default void starting(Span.Builder<?> spanBuilder) {
    }

    /**
     * Invoked just after a {@linkplain io.helidon.tracing.Span span} has been started.
     *
     * @param span {@link io.helidon.tracing.Span} for the newly-started span
     * @throws java.lang.UnsupportedOperationException if the listener tries to end the span
     */
    default void started(Span span) {
    }

    /**
     * Invoked just after a {@linkplain io.helidon.tracing.Span span} has been activated.
     *
     * @param span  the just-activated {@link io.helidon.tracing.Span}
     * @param scope the just-created {@linkplain io.helidon.tracing.Scope scope} which resulted from activating
     *              the span
     * @throws java.lang.UnsupportedOperationException if the listener tries to close the scope or end the span
     */
    default void activated(Span span, Scope scope) {
    }

    /**
     * Invoked just after a {@linkplain io.helidon.tracing.Scope scope} has been closed.
     *
     * @param span  {@link io.helidon.tracing.Span} for which a {@linkplain io.helidon.tracing.Scope scope} was closed
     * @param scope the just-closed {@link io.helidon.tracing.Scope}
     * @throws java.lang.UnsupportedOperationException if the listener tries to end the span
     */
    default void closed(Span span, Scope scope) {
    }

    /**
     * Invoked just after a {@linkplain io.helidon.tracing.Span span} has been ended successfully.
     *
     * @param span the just-ended {@link io.helidon.tracing.Span}
     */
    default void ended(Span span) {
    }

    /**
     * Invoked just after a {@linkplain io.helidon.tracing.Span span} has been ended unsuccessfully.
     *
     * @param span the just-ended {@link io.helidon.tracing.Span}
     * @param t    {@link java.lang.Throwable} indicating the problem associated with the ended span
     */
    default void ended(Span span, Throwable t) {
    }
}
