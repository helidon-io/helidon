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
package io.helidon.tracing.providers.opentelemetry;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class TestDataPropagation {

    private final OpenTelemetryDataPropagationProvider provider = new OpenTelemetryDataPropagationProvider();
    private final ExecutorService executor = Contexts.wrap(Executors.newVirtualThreadPerTaskExecutor());

    @Test
    void testSyncPropagation() {
        Context context = Context.create();
        Tracer tracer = OpenTelemetryTracer.builder()
                .serviceName("test-prop")
                .build();
        context.register(tracer);
        Span span = tracer.spanBuilder("test-span").start();
        context.register(span);

        try (Scope scope = span.activate()) {
            context.register(scope);
            Contexts.runInContext(context, () -> {
                OpenTelemetryDataPropagationProvider.OpenTelemetryContext data = provider.data();
                assertThat("Scope before prop ", data.scope(), is(nullValue()));

                provider.propagateData(data);
                assertThat("Scope during prop has been closed", data.scope().isClosed(), is(false));

                provider.clearData(data);
                assertThat("Scope after prop has been closed", data.scope().isClosed(), is(true));
            });
        }
    }

    @Test
    void testAsyncPropagation() {
        Context context = Context.create();
        Tracer tracer = Tracer.global();
        context.register(tracer);
        Span span = tracer.spanBuilder("test-async-span").start();
        context.register(span);
        SpanContext spanContext = span.context();
        AtomicReference<Optional<Span>> asyncSpanRef = new AtomicReference<>();
        try (Scope scope = span.activate()) {
            context.register(scope);
            Contexts.runInContext(context, () ->
            {
                try {
                    asyncSpanRef.set(executor.submit(() -> {
                        return Span.current();
                    }).get(5, TimeUnit.SECONDS));
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        Optional<SpanContext> asyncSpanContext = asyncSpanRef.get().map(Span::context);
        assertThat("Span ID",
                   asyncSpanContext.map(SpanContext::spanId),
                   OptionalMatcher.optionalValue(is(spanContext.spanId())));
    }
}
