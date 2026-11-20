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
package io.helidon.telemetry.logging.log4j2;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.helidon.telemetry.logging.LogBridgeContext;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;

final class BridgeTestSupport extends LogBridgeContext implements AutoCloseable {
    final List<LogRecordData> records;
    private final OpenTelemetrySdk sdk;

    BridgeTestSupport() {
        this(true);
    }

    BridgeTestSupport(boolean enabled) {
        this(new CopyOnWriteArrayList<>(), enabled);
    }

    private BridgeTestSupport(List<LogRecordData> records, boolean enabled) {
        this(records, sdk(records, enabled));
    }

    private BridgeTestSupport(List<LogRecordData> records, OpenTelemetrySdk sdk) {
        super(sdk);
        this.records = records;
        this.sdk = sdk;
    }

    private static OpenTelemetrySdk sdk(List<LogRecordData> records, boolean enabled) {
        var builder = SdkLoggerProvider.builder();
        if (enabled) {
            builder.addLogRecordProcessor(SimpleLogRecordProcessor.create(new LogRecordExporter() {
                @Override
                public CompletableResultCode export(Collection<LogRecordData> logs) {
                    records.addAll(logs);
                    return CompletableResultCode.ofSuccess();
                }

                @Override
                public CompletableResultCode flush() {
                    return CompletableResultCode.ofSuccess();
                }

                @Override
                public CompletableResultCode shutdown() {
                    return CompletableResultCode.ofSuccess();
                }
            }));
        }
        return OpenTelemetrySdk.builder().setLoggerProvider(builder.build()).build();
    }

    @Override
    protected void runIfActive(Runnable operation) {
        operation.run();
    }

    @Override
    public void close() {
        sdk.close();
    }
}
