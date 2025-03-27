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

import java.util.Arrays;

import io.helidon.common.config.Config;
import io.helidon.config.EnumMapperProvider;

/**
 * Valid values for OpenTelemetry exporter protocol.
 */
public enum OtlpExporterProtocol {
    /**
     * grpc OpenTelemetry protocol.
     */
    GRPC("grpc", 4317),

    /**
     * http/protobuf OpenTelemetry protocol.
     */
    HTTP_PROTOBUF("http/protobuf", 4318);

    private static final EnumMapperProvider MAPPER_PROVIDER = new EnumMapperProvider();
    static final String DEFAULT_STRING = "grpc";
    static final OtlpExporterProtocol DEFAULT = from(DEFAULT_STRING);
    private final String protocol;
    private final int defaultPort;

    OtlpExporterProtocol(String protocol, int defaultPort) {
        this.protocol = protocol;
        this.defaultPort = defaultPort;
    }

    int defaultPort() {
        return defaultPort;
    }

    /**
     * Attempts to map a config node using both the custom names and also using the normal built-in enum mapping.
     *
     * @param configNode node ostensibly representing an exporter protocol value
     * @return {@code OtlpExporterProtocol} matching the provided config node
     */
    static OtlpExporterProtocol from(Config configNode) {
        return configNode.asString()
                .as(OtlpExporterProtocol::from)
                .orElseGet(() -> configNode.as(OtlpExporterProtocol.class).orElseThrow());
    }

    /**
     * Attempts to map a string to the corresponding {@code OtlpExporterProtocol}.
     *
     * @param protocol protocol string
     * @return corresponding {@code OtlpExporterProtocol} value
     */
    static OtlpExporterProtocol from(String protocol) {
        for (OtlpExporterProtocol otlpExporterProtocol : OtlpExporterProtocol.values()) {
            if (otlpExporterProtocol.protocol.equals(protocol) || otlpExporterProtocol.name().equals(protocol)) {
                return otlpExporterProtocol;
            }
        }
        throw new IllegalArgumentException("Unknown exporter protocol: " + protocol + "; expected one of "
                                                   + Arrays.toString(OtlpExporterProtocol.values()));
    }
}
