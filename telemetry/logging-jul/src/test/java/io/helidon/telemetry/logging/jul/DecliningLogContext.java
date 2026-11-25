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
package io.helidon.telemetry.logging.jul;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.telemetry.logging.LogBridgeContext;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerBuilder;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;

/** Counts SDK interactions while publication admission is closed. */
final class DecliningLogContext extends LogBridgeContext {
    DecliningLogContext(AtomicInteger calls) {
        super(new OpenTelemetry() {
            @Override
            public TracerProvider getTracerProvider() {
                return TracerProvider.noop();
            }

            @Override
            public ContextPropagators getPropagators() {
                return ContextPropagators.noop();
            }

            @Override
            public LoggerProvider getLogsBridge() {
                calls.incrementAndGet();
                return _ -> new LoggerBuilder() {
                    @Override
                    public LoggerBuilder setSchemaUrl(String schemaUrl) {
                        return this;
                    }

                    @Override
                    public LoggerBuilder setInstrumentationVersion(String version) {
                        return this;
                    }

                    @Override
                    public Logger build() {
                        calls.incrementAndGet();
                        return new Logger() {
                            @Override
                            public boolean isEnabled(Severity severity) {
                                calls.incrementAndGet();
                                return false;
                            }

                            @Override
                            public boolean isEnabled(Severity severity, Context context) {
                                calls.incrementAndGet();
                                return false;
                            }

                            @Override
                            public LogRecordBuilder logRecordBuilder() {
                                calls.incrementAndGet();
                                return LoggerProvider.noop().get("test").logRecordBuilder();
                            }
                        };
                    }
                };
            }
        });
    }

    @Override
    protected void runIfActive(Runnable operation) {
        // The lifecycle owner has already stopped admitting publications.
    }
}
