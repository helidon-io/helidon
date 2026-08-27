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

import java.io.IOException;

import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;

import jakarta.annotation.Priority;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;

/**
 * Makes a response-write-inclusive automatic server span current during entity materialization.
 */
@Provider
@Priority(Integer.MIN_VALUE)
final class HelidonTelemetryWriterInterceptor implements WriterInterceptor {

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException {
        ServerSpanLifecycle lifecycle = (ServerSpanLifecycle) context.getProperty(ServerSpanLifecycle.PROPERTY);
        Span span = (Span) context.getProperty(HelidonTelemetryContainerFilter.SPAN);
        if (lifecycle == null || span == null) {
            context.proceed();
            return;
        }

        if (isCurrent(span)) {
            proceed(context, lifecycle);
            return;
        }

        try (Scope ignored = span.activate()) {
            proceed(context, lifecycle);
        }
    }

    private static void proceed(WriterInterceptorContext context, ServerSpanLifecycle lifecycle) throws IOException {
        try {
            context.proceed();
        } catch (IOException | RuntimeException | Error e) {
            lifecycle.writerFailure(e);
            throw e;
        }
    }

    private static boolean isCurrent(Span span) {
        return Span.current()
                .map(current -> current.context().spanId().equals(span.context().spanId()))
                .orElse(false);
    }
}
