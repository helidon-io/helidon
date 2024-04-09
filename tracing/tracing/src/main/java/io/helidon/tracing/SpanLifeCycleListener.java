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

import java.util.Map;

/**
 * Behavior of listeners notified of span life cycle events.
 * <p>
 * The interface declares default empty implementations for each method, allowing concrete implementations of the
 * interface to implement only the methods that are relevant for them.
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
 *         <td>{@linkplain #beforeStart(io.helidon.tracing.Span.Builder) beforeStart}</td>
 *         <td>Before a span is started from its builder.</td>
 *     </tr>
 *     <tr>
 *         <td>{@linkplain #afterStart(io.helidon.tracing.Span) afterStart}</td>
 *         <td>After a span has started.</td>
 *     </tr>
 *     <tr>
 *         <td>{@linkplain #afterActivate(io.helidon.tracing.Span, io.helidon.tracing.Scope)
 *         afterActivate} †</td>
 *         <td>After a span has been activated, creating a new scope in the process. </td>
 *     </tr>
 *     <tr>
 *         <td>{@linkplain #afterClose(io.helidon.tracing.Span, io.helidon.tracing.Scope) afterClose} †</td>
 *         <td>After a scope has been closed.</td>
 *     </tr>
 *     <tr>
 *         <td>{@linkplain #afterEnd(io.helidon.tracing.Span) afterEnd (successful)} *</td>
 *         <td>After a span has ended successfully.</td>
 *     </tr>
 *     <tr>
 *         <td>{@linkplain #afterEnd(io.helidon.tracing.Span, Throwable) afterEnd (unsuccessful)} *</td>
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
 * <p>
 *     When Helidon invokes the listener methods it passes implementations of the key span types ({@link Span.Builder},
 *     {@link Span}, {@link io.helidon.tracing.Scope}) which <em>do not</em> support lifecycle state changes.
 *     If a lifecycle listener tries to start or end or activate a span,
 *     for example, Helidon throws an {@link java.lang.UnsupportedOperationException}.
 */
public interface SpanLifeCycleListener {

    /**
     * Invoked after a {@link Span.Builder} is created.
     *
     * @param tracer the {@link io.helidon.tracing.Tracer} which created the new {@code Span.Builder}
     * @param spanBuilder the new {@code Span.Builder}
     * @param name the name assigned to the new {@code Span.Builder}
     */
    default void newSpanBuilder(Tracer tracer, Span.Builder<?> spanBuilder, String name) {
    }

    /**
     * Invoked when a tag is set on a {@link Span.Builder}.
     *
     * @param spanBuilder the span builder on which the tag is set
     * @param tag the tag assigned to the span builder
     */
    default void tag(Span.Builder<?> spanBuilder, Tag<?> tag) {
    }

    /**
     * Invoked when a tag is set on a {@link Span.Builder}.
     *
     * @param spanBuilder the span builder on which the tag is set
     * @param key tag key
     * @param value tag value
     */
    default void tag(Span.Builder<?> spanBuilder, String key, String value) {
    }

    /**
     * Invoked when a tag is set on a {@link Span.Builder}.
     *
     * @param spanBuilder the span builder on which the tag is set
     * @param key tag key
     * @param value tag value
     */
    default void tag(Span.Builder<?> spanBuilder, String key, Boolean value) {
    }

    /**
     * Invoked when a tag is set on a {@link Span.Builder}.
     *
     * @param spanBuilder the span builder on which the tag is set
     * @param key tag key
     * @param value tag value
     */
    default void tag(Span.Builder<?> spanBuilder, String key, Number value) {
    }

    /**
     * Invoked when a tag is set on a {@link io.helidon.tracing.Span}.
     *
     * @param span the span on which the tag is set
     * @param tag the tag assigned to the span
     */
    default void tag(Span span, Tag<?> tag) {
    }

    /**
     * Invokes when a tag is set on a {@link io.helidon.tracing.Span}.
     *
     * @param span the span on which the tag is set
     * @param key tag key
     * @param value tag value
     */
    default void tag(Span span, String key, String value) {
    }

    /**
     * Invokes when a tag is set on a {@link io.helidon.tracing.Span}.
     *
     * @param span the span on which the tag is set
     * @param key tag key
     * @param value tag value
     */
    default void tag(Span span, String key, Boolean value) {
    }

    /**
     * Invokes when a tag is set on a {@link io.helidon.tracing.Span}.
     *
     * @param span the span on which the tag is set
     * @param key tag key
     * @param value tag value
     */
    default void tag(Span span, String key, Number value) {
    }

    /**
     * Invoked when an event is added to a {@link io.helidon.tracing.Span}.
     *
     * @param span the span to which the event is added
     * @param message event message
     * @param attributes event attributes
     */
    default void addEvent(Span span, String message, Map<String, ?> attributes) {
    }

    /**
     * Invoked just prior to a {@linkplain io.helidon.tracing.Span span} being started from its
     * {@linkplain io.helidon.tracing.Span.Builder builder}.
     *
     * @param spanBuilder the {@link Span.Builder} for the builder about to be used to start a span
     * @throws java.lang.UnsupportedOperationException if the listener tries to start the span
     */
    default void beforeStart(Span.Builder<?> spanBuilder) throws UnsupportedOperationException {
    }

    /**
     * Invoked just after a {@linkplain io.helidon.tracing.Span span} has been started.
     *
     * @param span {@link io.helidon.tracing.Span} for the newly-started span
     * @throws java.lang.UnsupportedOperationException if the listener tries to end the span
     */
    default void afterStart(Span span) throws UnsupportedOperationException {
    }

    /**
     * Invoked just after a {@linkplain io.helidon.tracing.Span span} has been activated.
     * <p>
     *     Callers should normally catch the {@link io.helidon.tracing.UnsupportedActivationException}, retrieve the
     *     {@link io.helidon.tracing.Scope} from it, and then close that {@code Scope}.
     *     Helidon activates the scope before invoking span life cycle listeners, so Helidon has added the span and baggage
     *     to the current tracing context by the time Helidon throws this exception. In the absence of this exception being
     *     thrown, the {@link io.helidon.tracing.Span#activate()} method returns the {@code Scope} so the caller can close it.
     *     But when Helidon throws this exception due to an error in a listener, the caller has no access to the {@code Scope}
     *     return value and needs to use the {@link UnsupportedActivationException#scope()} method to retrieve it.
     *
     * @param span  the just-activated {@link io.helidon.tracing.Span}
     * @param scope the just-created {@linkplain io.helidon.tracing.Scope scope} which resulted from activating
     *              the span
     * @throws io.helidon.tracing.UnsupportedActivationException if the listener tries to close the scope or end the span
     */
    default void afterActivate(Span span, Scope scope) throws UnsupportedActivationException {
    }

    /**
     * Invoked just after a {@linkplain io.helidon.tracing.Scope scope} has been closed.
     *
     * @param span  {@link io.helidon.tracing.Span} for which a {@linkplain io.helidon.tracing.Scope scope} was closed
     * @param scope the just-closed {@link io.helidon.tracing.Scope}
     * @throws java.lang.UnsupportedOperationException if the listener tries to end the span
     */
    default void afterClose(Span span, Scope scope) throws UnsupportedOperationException {
    }

    /**
     * Invoked just after a {@linkplain io.helidon.tracing.Span span} has been ended successfully.
     *
     * @param span the just-ended {@link io.helidon.tracing.Span}
     */
    default void afterEnd(Span span) {
    }

    /**
     * Invoked just after a {@linkplain io.helidon.tracing.Span span} has been ended unsuccessfully.
     *
     * @param span the just-ended {@link io.helidon.tracing.Span}
     * @param t    {@link java.lang.Throwable} indicating the problem associated with the ended span
     */
    default void afterEnd(Span span, Throwable t) {
    }
}
