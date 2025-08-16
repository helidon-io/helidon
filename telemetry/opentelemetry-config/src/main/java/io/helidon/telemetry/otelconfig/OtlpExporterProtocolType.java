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
 * Legal values for OTLP exporters.
 */
public enum OtlpExporterProtocolType {
    /**
     * http/proto protocol type.
     */
    HTTP_PROTO("http/proto"),

    /**
     * grpc protocol type.
     */
    GRPC("grpc");

    private final String protocol;

    static final OtlpExporterProtocolType DEFAULT = HTTP_PROTO;

    OtlpExporterProtocolType(String protocol) {
        this.protocol = protocol;
    }

    /**
     * Converts from a string that should match the protocol name to the corresponding protocol type enum.
     *
     * @param protocol string to match
     * @return matching type
     */
    static OtlpExporterProtocolType from(String protocol) {
        for (OtlpExporterProtocolType value : OtlpExporterProtocolType.values()) {
            if (value.protocol.equals(protocol) || value.name().equals(protocol)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown protocol: " + protocol + "; expected one of "
                                                   + Arrays.toString(OtlpExporterProtocolType.values()));
    }

    /**
     * Maps a config node's string value to the corresponding type.
     *
     * @param config config node
     * @return matching type
     */
    static OtlpExporterProtocolType from(Config config) {
        return from(config.asString().get());
    }
}
