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
 * Types of compression supported by OpenTelemetry.
 */
enum CompressionType {

    /**
     * GZIP compression.
     */
    GZIP("gzip"),

    /**
     * No compression.
     */
    NONE("none");

    private final String value;

    CompressionType(String value) {
        this.value = value;
    }

    String value() {
        return value;
    }

    static CompressionType from(String value) {
        for (CompressionType compressionType : CompressionType.values()) {
            if (compressionType.value.equals(value) || compressionType.name().equals(value)) {
                return compressionType;
            }
        }
        throw new IllegalArgumentException("Unknown compression type: " + value + "; expected one of "
                                           + Arrays.toString(CompressionType.values()));
    }

    static CompressionType from(Config config) {
        return from(config.asString().get());
    }
}
