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

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogBridgeContextTest {
    @Test
    void suppressesRecursivePublicationsAcrossContextsAndExporterDiagnostics() {
        var context = context();
        var other = context();
        var calls = new AtomicInteger();
        assertThat(context.publishing(), is(false));
        context.publish("application", () -> {
            assertThat(context.publishing(), is(true));
            assertThat(other.publishing(), is(false));
            calls.incrementAndGet();
            context.publish("nested", calls::incrementAndGet);
            other.publish("nested-other-context", calls::incrementAndGet);
        });
        context.publish("io.opentelemetry.exporter", calls::incrementAndGet);
        assertThat(calls.get(), is(1));
        assertThat(context.publishing(), is(false));
    }

    @Test
    void failureRestoresGuardForSubsequentPublications() {
        var context = context();
        assertThrows(IllegalStateException.class,
                     () -> context.publish("broken", () -> { throw new IllegalStateException("synthetic"); }));
        assertThat(context.publishing(), is(false));
        var calls = new AtomicInteger();
        context.publish("after-failure", calls::incrementAndGet);
        assertThat(calls.get(), is(1));
    }

    @Test
    void callerCanDeclinePublicationWithoutLeavingAnActivation() {
        var calls = new AtomicInteger();
        var context = new LogBridgeContext(OpenTelemetry.noop()) {
            @Override
            protected void runIfActive(Runnable operation) {
                assertThat(publishing(), is(true));
            }
        };
        context.publish("application", calls::incrementAndGet);
        assertThat(calls.get(), is(0));
        assertThat(context.publishing(), is(false));
    }

    @Test
    void concurrentPublicationsHaveIndependentActivations() throws Exception {
        var context = context();
        var calls = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = IntStream.range(0, 8).mapToObj(_ -> executor.submit(() -> {
                context.publish("application", () -> {
                    assertThat(context.publishing(), is(true));
                    calls.incrementAndGet();
                });
                assertThat(context.publishing(), is(false));
            })).toList();
            for (var task : tasks) {
                task.get(5, TimeUnit.SECONDS);
            }
        }
        assertThat(calls.get(), is(8));
    }

    @Test
    void suppressionSurvivesCapturedContextAndEndsWithItsScope() {
        var context = context();
        var calls = new AtomicInteger();
        Context captured;
        try (var _ = LogBridgeContext.suppressLogging()) {
            context.publish("custom-exporter", calls::incrementAndGet);
            captured = LogBridgeContext.captureContext();
        }
        context.publish("async-exporter", captured, calls::incrementAndGet);
        assertThat(calls.get(), is(0));
        context.publish("application", calls::incrementAndGet);
        assertThat(calls.get(), is(1));
    }

    @Test
    void rejectsNullPublicationParameters() {
        var context = context();
        assertThrows(NullPointerException.class, () -> context.publish(null, () -> { }));
        assertThrows(NullPointerException.class, () -> context.publish("application", null));
    }

    private static LogBridgeContext context() {
        return new LogBridgeContext(OpenTelemetry.noop()) {
            @Override
            protected void runIfActive(Runnable operation) {
                operation.run();
            }
        };
    }
}
