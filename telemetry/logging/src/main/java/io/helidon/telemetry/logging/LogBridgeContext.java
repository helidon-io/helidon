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
package io.helidon.telemetry.logging;

import java.util.Objects;

import io.helidon.common.Api;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;

/**
 * Caller-supplied OpenTelemetry instance and synchronous publication boundary for a logging bridge.
 * The bridge never configures or closes that instance. Adapters may cache logger handles for it;
 * per-event SDK access, formatting, and emission run only inside {@link #publish(String, Runnable)}.
 * <p>
 * Subclasses implement {@link #runIfActive(Runnable)} to admit work and retain its dependencies until the
 * operation returns, or decline it during shutdown. This type suppresses recursive publications and
 * OpenTelemetry diagnostics before invoking that boundary. Native adapters capture producer context for
 * asynchronous delivery; the publication method restores it while invoking the admission boundary.
 */
@Api.Preview
public abstract class LogBridgeContext {
    private static final ScopedValue<LogBridgeContext> PUBLISHING = ScopedValue.newInstance();
    private static final ContextKey<Boolean> SUPPRESSED = ContextKey.named("helidon-logging-suppressed");
    private final OpenTelemetry telemetry;

    /**
     * Constructor for caller implementations of the publication boundary. The selected instance is
     * fixed for the registration's lifetime; replacing it requires a new context and bridge.
     *
     * @param telemetry caller-selected OpenTelemetry instance
     */
    protected LogBridgeContext(OpenTelemetry telemetry) {
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
    }

    /**
     * Suppress bridge capture during an SDK exporter callback. Custom asynchronous exporters must
     * propagate the current OpenTelemetry context to their own tasks.
     *
     * @return scope to close on the calling thread
     */
    public static Scope suppressLogging() {
        return Context.current().with(SUPPRESSED, true).makeCurrent();
    }

    /**
     * Capture the producer context, including bridge suppression, for asynchronous native delivery.
     *
     * @return producer context
     */
    public static Context captureContext() {
        var current = Context.current();
        return PUBLISHING.isBound() ? current.with(SUPPRESSED, true) : current;
    }

    /**
     * OpenTelemetry instance selected and owned by the caller or an external component.
     *
     * @return selected OpenTelemetry instance, never null
     */
    public final OpenTelemetry telemetry() {
        return telemetry;
    }

    /**
     * Whether the current thread is executing this context's publication boundary.
     * A caller that drains admitted publications during shutdown must reject shutdown from within
     * its own publication, since that operation would otherwise wait for itself.
     *
     * @return whether this context is publishing on the current thread
     */
    public final boolean publishing() {
        return PUBLISHING.isBound() && PUBLISHING.get() == this;
    }

    /**
     * Publish through the caller's admission boundary, suppressing recursion and exporter diagnostics.
     * The operation runs synchronously at most once; it must not retain native events for later use.
     *
     * @param loggerName native logger name, or an empty string when unavailable
     * @param operation formatting and emission operation
     */
    public final void publish(String loggerName, Runnable operation) {
        publish(loggerName, captureContext(), operation);
    }

    /**
     * Publish using the context captured when the native event was created.
     *
     * @param loggerName native logger name or empty string
     * @param producerContext captured producer context
     * @param operation synchronous formatting and emission
     */
    public final void publish(String loggerName, Context producerContext, Runnable operation) {
        Objects.requireNonNull(loggerName, "loggerName");
        Objects.requireNonNull(producerContext, "producerContext");
        Objects.requireNonNull(operation, "operation");
        if (PUBLISHING.isBound() || Boolean.TRUE.equals(producerContext.get(SUPPRESSED))
                || Boolean.TRUE.equals(Context.current().get(SUPPRESSED)) || loggerName.startsWith("io.opentelemetry")) {
            return;
        }
        ScopedValue.where(PUBLISHING, this).run(() -> {
            if (Context.current() == producerContext) {
                runIfActive(operation);
            } else {
                try (var _ = producerContext.makeCurrent()) {
                    runIfActive(operation);
                }
            }
        });
    }

    /**
     * Admit publication work and retain the OpenTelemetry instance and other required resources until
     * it returns. The implementation may suppress the operation, for example after stopping admission.
     * Do not shut down or drain resources needed by this operation from the operation itself.
     * <p>
     * Implementations must be thread-safe and invoke the operation synchronously at most once.
     * Exceptions may propagate; any reservations must be released in a {@code finally} block.
     *
     * @param operation formatting and emission operation
     */
    protected abstract void runIfActive(Runnable operation);
}
