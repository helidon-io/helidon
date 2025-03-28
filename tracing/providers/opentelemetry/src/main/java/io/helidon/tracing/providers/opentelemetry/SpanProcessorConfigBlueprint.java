/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Generic configuration for a {@link io.opentelemetry.sdk.trace.SpanProcessor}, linked to a
 * {@link io.opentelemetry.sdk.trace.export.SpanExporter} by its name in the configuration.
 */
@Prototype.Blueprint
@Prototype.Configured
interface SpanProcessorConfigBlueprint {

    /**
     * Name of the span exporter this span processor should use.
     * @return span exporter name
     */
    @Option.Configured
    @Option.Default("@default")
    String spanExporter();

    /**
     * Span processor type.
     * @return span processor type
     */
    @Option.Configured
    @Option.Default(SpanProcessorType.DEFAULT_NAME)
    SpanProcessorType type();

}
