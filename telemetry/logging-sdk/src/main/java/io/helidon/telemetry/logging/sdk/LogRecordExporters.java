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
import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.telemetry.logging.LogBridgeContext;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;

/**
 * SDK log-exporter integration with native logging bridges.
 */
@Api.Preview
public final class LogRecordExporters {
    private LogRecordExporters() {
    }

    /**
     * Decorate an exporter to suppress bridge capture during its callbacks. Install this as the
     * outermost exporter decorator, before constructing the SDK. The SDK retains lifecycle ownership.
     * Asynchronous exporter implementations must propagate the current OpenTelemetry context to their tasks.
     *
     * @param delegate exporter to decorate
     * @return exporter with bridge suppression; repeated decoration returns the same instance
     */
    public static LogRecordExporter suppressLogging(LogRecordExporter delegate) {
        Objects.requireNonNull(delegate, "delegate");
        return delegate instanceof SuppressedExporter ? delegate : new SuppressedExporter(delegate);
    }

    private static final class SuppressedExporter implements LogRecordExporter {
        private final LogRecordExporter delegate;

        private SuppressedExporter(LogRecordExporter delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletableResultCode export(Collection<LogRecordData> records) {
            Objects.requireNonNull(records, "records");
            try (var _ = LogBridgeContext.suppressLogging()) {
                return delegate.export(records);
            }
        }

        @Override
        public CompletableResultCode flush() {
            try (var _ = LogBridgeContext.suppressLogging()) {
                return delegate.flush();
            }
        }

        @Override
        public CompletableResultCode shutdown() {
            try (var _ = LogBridgeContext.suppressLogging()) {
                return delegate.shutdown();
            }
        }

        @Override
        public void close() {
            try (var _ = LogBridgeContext.suppressLogging()) {
                delegate.close();
            }
        }
    }
}
