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
package io.helidon.tracing.providers.tests;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

/**
 * Derived as closely as possible from a user-supplied reproducer. Comments are ours, added for explanation or questions.
 */
class TestTracerAndSpanPropagation {
        private static final System.Logger LOGGER = System
                .getLogger(TestTracerAndSpanPropagation.class.getName());

        @Test
        void concurrentVirtualThreadUseCanary() {
            Contexts.runInContext(Context.create(), this::actualTest);
        }

        private void actualTest() {

            final var tracer = buildTracer();
            LOGGER.log(System.Logger.Level.INFO, "Tracer {0}", tracer);
            assertThat("Tracer is enabled", tracer.enabled(), is(true));
            assertThat("Tracer in use is the global tracer", tracer, sameInstance(Tracer.global()));
            final var rootSpan = tracer.spanBuilder(getClass().getSimpleName()).start();
            LOGGER.log(System.Logger.Level.INFO, "traceId: {0}", rootSpan.context().traceId());
            try (var ignored = rootSpan.activate()) {
                assertThat("rootSpan activate",
                           Span.current().map(Span::context).map(SpanContext::spanId),
                           OptionalMatcher.optionalValue(is(rootSpan.context().spanId())));

                final var ff = new CompletableFuture[1];
                try (var executor = Contexts.wrap(Executors.newVirtualThreadPerTaskExecutor())) {
                    final var futures = new CompletableFuture[5];
                    for (int i = 0; i < 5; i++) {
                        futures[i] = CompletableFuture
                                .runAsync(new ChildAction(tracer, rootSpan), executor);
                    }
                    for (final var f : futures) {
                        ff[0] = f;
                        f.get(1, TimeUnit.SECONDS);
                    }
                    LOGGER.log(System.Logger.Level.INFO, "all futures complete");

                } catch (ExecutionException | TimeoutException | InterruptedException e) {
                    LOGGER.log(System.Logger.Level.ERROR, "Failure: f={0}", ff[0], e);
                    rootSpan.end(e);
                    throw new RuntimeException(e);
                }
                rootSpan.end();
                LOGGER.log(System.Logger.Level.INFO, "ended rootSpan");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                LOGGER.log(System.Logger.Level.ERROR, "Error on sleep", e);
                throw new RuntimeException(e);
            }
            LOGGER.log(System.Logger.Level.INFO, "test ended");
        }

        record ChildAction(Tracer globalTracer, Span rootSpan) implements Runnable {

            @Override public void run() {
                final var thread = Thread.currentThread();
                assertThat("isVirtual false in ChildAction", thread.isVirtual(), is(true));
                final var threadName = String.valueOf(thread);
                LOGGER.log(System.Logger.Level.INFO, "Running {0}", threadName);

                final var tracer = Objects.requireNonNull(Tracer.global(), "global NOT in ChildAction");
                assertThat("tracer NOT preserved in ChildAction", tracer, is(sameInstance(globalTracer)));
                assertThat("Current span from test NOT present in ChildAction",
                           Span.current().map(Span::context).map(SpanContext::spanId),
                           OptionalMatcher.optionalValue(is(rootSpan.context().spanId())));

                final var span = Span.current().get();
                final var spanContext = span.context();
                final var childSpan = tracer.spanBuilder(threadName)
                        .parent(spanContext).start();
                try (var ignored = childSpan.activate()) {

                    assertThat("childSpan NOT activated",
                               Span.current().map(Span::context).map(SpanContext::spanId),
                               OptionalMatcher.optionalValue(is(childSpan.context().spanId())));
                    Thread.sleep(10);
                    span.end();

                } catch (InterruptedException e) {
                    span.end(e);
                    throw new RuntimeException(e);
                } finally {
                    LOGGER.log(System.Logger.Level.INFO, "Ended {0}", threadName);
                }
            }
        }

        private Tracer buildTracer() {
            return Tracer.global();

            // Below used when I have to test completely standalone, Or I get:
            // java.lang.IllegalStateException: GlobalOpenTelemetry.set has already been called.
            // GlobalOpenTelemetry.set must be called only once before any calls to GlobalOpenTelemetry.get.
            // If you are using the OpenTelemetrySdk, use OpenTelemetrySdkBuilder.buildAndRegisterGlobal instead.
            // Previous invocation set to cause of this exception.

            //        final var config = Config.just(MapConfigSource.create(Map.ofEntries(
            //                entry("tracing.enabled", "true"),
            //                entry("tracing.global", "true"),
            //                entry("tracing.service", "propData409"),
            //                entry("tracing.log-spans", "false"),
            //                entry("tracing.protocol", "http"),
            //                entry("tracing.host", "localhost"),
            //                entry("tracing.port", "14250"),
            //                entry("tracing.path", "/api/traces"),
            //                entry("tracing.propagation", "b3,w3c"),
            //                entry("tracing.sampler-type", "const"),
            //                entry("tracing.sampler-param", "1"),
            //                entry("tracing.expand-exception-logs", "true")
            //        )));
            //        return TracerBuilder.create("propData409")
            //                .enabled(true).registerGlobal(true)
            //                .collectorProtocol("http")
            //                .collectorHost("localhost")
            //                .collectorPath("/api/traces")
            //                .config(config).build();
        }
}
