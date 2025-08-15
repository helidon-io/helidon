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

import java.util.Arrays;

import io.helidon.common.config.Config;

/**
 * Span Processor type. Batch is default for production.
 */
enum SpanProcessorType {
    /**
     * Simple Span Processor.
     */
    SIMPLE("simple"),
    /**
     * Batch Span Processor.
     */
    BATCH("batch");

    static final SpanProcessorType DEFAULT = BATCH;
    static final String DEFAULT_NAME = "BATCH";

    private final String processorType;

    SpanProcessorType(String processorType) {
        this.processorType = processorType;
    }

    static SpanProcessorType from(String value) {
        for (SpanProcessorType spanProcessorType : SpanProcessorType.values()) {
            if (spanProcessorType.processorType.equals(value) || spanProcessorType.name().equals(value)) {
                return spanProcessorType;
            }
        }
        throw new IllegalArgumentException("Unknown span processor type: " + value + "; expected one of "
                                                   + Arrays.toString(SpanProcessorType.values()));

    }

    static SpanProcessorType from(Config config) {
        return from(config.asString().get());
    }
}

