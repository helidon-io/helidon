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

package io.helidon.telemetry.otelconfig;

import java.time.Duration;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration for a batch span processor.
 */
@Prototype.Configured
@Prototype.Blueprint
interface BatchSpanProcessorConfigBlueprint extends SpanProcessorConfigBlueprint {

    /**
     * Delay between consecutive exports.
     * @return delay between consecutive exports
     */
    @Option.Configured
    Optional<Duration> scheduleDelay();

    /**
     * Maximum number of spans retained before discarding excess unexported ones.
     * @return maximum number of spans kept
     */
    @Option.Configured
    Optional<Integer> maxQueueSize();

    /**
     * Maximum number of spans batched for export together. OpenTelemetry requires this value to not exceed
     * the {@link #maxQueueSize()}.
     * @return maximum number of spans batched
     */
    @Option.Configured
    Optional<Integer> maxExportBatchSize();

    /**
     * Maximum time an export can run before being cancelled.
     * @return maximum export time
     */
    @Option.Configured
    Optional<Duration> timeout();
}
