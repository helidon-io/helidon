/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

package io.helidon.tracing.providers.jaeger;

import io.helidon.tracing.providers.opentelemetry.OpenTelemetryDataPropagationProvider;

/**
 * A data propagation provider for Jaeger. Makes sure span are properly propagated
 * across threads managed by {@link io.helidon.common.context.ContextAwareExecutorService}.
 * <p>
 *     Because the Jaeger client uses OpenTelemetry, our data propagation for Jaeger is identical
 *     to that for OTel.
 * </p>
 */
public class JaegerDataPropagationProvider extends OpenTelemetryDataPropagationProvider {

    @Override
    public JaegerContext data() {
        return new JaegerContext(super.data());
    }

    public static class JaegerContext extends OpenTelemetryDataPropagationProvider.OpenTelemetryContext {
        JaegerContext(OpenTelemetryContext delegate) {
            super(delegate.tracer(), delegate.span());
        }
    }
}
