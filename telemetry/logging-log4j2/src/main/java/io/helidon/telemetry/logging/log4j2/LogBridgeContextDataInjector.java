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

import java.util.List;

import io.helidon.common.Api;
import io.helidon.telemetry.logging.LogBridgeContext;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppenderContextDataInjector;
import org.apache.logging.log4j.core.ContextDataInjector;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.impl.ContextDataFactory;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.StringMap;

/**
 * Captures producer context before native asynchronous delivery. Selected by Log4j's component
 * properties at bootstrap; an application-provided injector must delegate to this implementation.
 */
@Api.Internal
public final class LogBridgeContextDataInjector implements ContextDataInjector {
    private static final String CONTEXT = "helidon.otel.logging.context";
    private final ContextDataInjector delegate = new OpenTelemetryAppenderContextDataInjector();

    /**
     * Constructor required by Log4j's context-data injector factory.
     */
    public LogBridgeContextDataInjector() {
    }

    @Override
    public StringMap injectContextData(List<Property> properties, StringMap reusable) {
        return capture(delegate.injectContextData(properties, reusable));
    }

    @Override
    public ReadOnlyStringMap rawContextData() {
        return capture(ContextDataFactory.createContextData(delegate.rawContextData()));
    }

    static Context context(LogEvent event) {
        Object value = event.getContextData().getValue(CONTEXT);
        return value instanceof Context context ? context : LogBridgeContext.captureContext();
    }

    private static StringMap capture(StringMap data) {
        var context = LogBridgeContext.captureContext();
        if (context == Context.root()) {
            return data;
        }
        var result = data.isFrozen() ? ContextDataFactory.createContextData(data) : data;
        result.putValue(CONTEXT, context);
        return result;
    }
}
