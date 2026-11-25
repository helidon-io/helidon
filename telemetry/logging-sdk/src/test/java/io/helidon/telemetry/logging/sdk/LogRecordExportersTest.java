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
package io.helidon.telemetry.logging.sdk;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.telemetry.logging.LogBridgeContext;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogRecordExportersTest {
    @Test
    void exporterFailureRestoresPublicationAndNullIsRejected() {
        var context = new LogBridgeContext(OpenTelemetry.noop()) {
            @Override
            protected void runIfActive(Runnable operation) {
                operation.run();
            }
        };
        var delegate = new LogRecordExporter() {
            @Override
            public CompletableResultCode export(Collection<LogRecordData> records) {
                throw new IllegalStateException("Synthetic exporter failure");
            }
            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.ofFailure();
            }
            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.ofSuccess();
            }
        };
        var wrapper = LogRecordExporters.suppressLogging(delegate);
        assertThrows(IllegalStateException.class, () -> wrapper.export(List.of()));
        var calls = new AtomicInteger();
        context.publish("after-failure", calls::incrementAndGet);
        assertThat(calls.get(), is(1));
        assertThat(wrapper.flush().isSuccess(), is(false));
        assertThrows(NullPointerException.class, () -> LogRecordExporters.suppressLogging(null));
    }

    @Test
    void exporterCallbacksSuppressBridgeCaptureAndPreserveLifecycleResults() {
        var published = new AtomicInteger();
        var invoked = new AtomicInteger();
        var context = new LogBridgeContext(OpenTelemetry.noop()) {
            @Override
            protected void runIfActive(Runnable operation) {
                operation.run();
            }
        };
        var result = CompletableResultCode.ofSuccess();
        var delegate = new LogRecordExporter() {
            private CompletableResultCode invoke() {
                invoked.incrementAndGet();
                context.publish("custom-exporter", published::incrementAndGet);
                return result;
            }
            @Override
            public CompletableResultCode export(Collection<LogRecordData> records) {
                return invoke();
            }
            @Override
            public CompletableResultCode flush() {
                return invoke();
            }
            @Override
            public CompletableResultCode shutdown() {
                return invoke();
            }
            @Override
            public void close() { invoke(); }
        };
        var guarded = LogRecordExporters.suppressLogging(delegate);
        assertThat(guarded.export(List.of()), sameInstance(result));
        assertThat(guarded.flush(), sameInstance(result));
        assertThat(guarded.shutdown(), sameInstance(result));
        guarded.close();
        assertThat(invoked.get(), is(4));
        assertThat(published.get(), is(0));
        assertThat(LogRecordExporters.suppressLogging(guarded), sameInstance(guarded));
        context.publish("after-export", published::incrementAndGet);
        assertThat(published.get(), is(1));
    }
}
