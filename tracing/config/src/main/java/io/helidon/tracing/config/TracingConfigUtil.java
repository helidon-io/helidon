/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tracing.config;

import io.helidon.common.context.Contexts;

/**
 * Utility to get the current tracing configuration.
 * The tracing configuration must be registered in current {@link io.helidon.common.context.Context}.
 * This can be achieved either through configuration of the global context and registering it with a server component,
 * or by using a server specific approach, such as {@code Routing.Builder#register(WebTracingConfig)}.
 */
public final class TracingConfigUtil {
    /**
     * Qualifier for outbound {@code io.opentracing.SpanContext} as registered with {@link io.helidon.common.context.Context}.
     */
    public static final Object OUTBOUND_SPAN_QUALIFIER = OutboundSpanQualifier.class;

    private TracingConfigUtil() {
    }

    /**
     * Get the configuration of a single span from current {@link io.helidon.common.context.Context}.
     *
     * @param component component tracing this span
     * @param spanName name of the span to trace
     * @return span configuration, including configuration of span logs
     */
    public static SpanTracingConfig spanConfig(String component, String spanName) {
        return Contexts.context()
                .flatMap(ctx -> ctx.get(TracingConfig.class))
                .map(tracedConfig -> tracedConfig.spanConfig(component, spanName))
                .orElse(SpanTracingConfig.ENABLED);
    }

    /**
     * Get the configuration of a single span from current {@link io.helidon.common.context.Context}.
     *
     * @param component component tracing this span
     * @param spanName name of the span to trace
     * @param defaultEnabled whether tracing should be enabled by default
     * @return span configuration, including configuration of span logs
     */
    public static SpanTracingConfig spanConfig(String component, String spanName, boolean defaultEnabled) {
        return Contexts.context()
                .flatMap(ctx -> ctx.get(TracingConfig.class))
                .map(tracingConfig -> tracingConfig.component(component, defaultEnabled))
                .map(tracingComponent -> tracingComponent.span(spanName, defaultEnabled))
                .orElseGet(() -> defaultEnabled ? SpanTracingConfig.ENABLED : SpanTracingConfig.DISABLED);
    }

    private static final class OutboundSpanQualifier {
    }
}
