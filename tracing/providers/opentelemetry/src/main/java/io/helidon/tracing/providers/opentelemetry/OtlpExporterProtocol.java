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
    GRPC("grpc", 4317, ""),

    /**
     * http/protobuf OpenTelemetry protocol.
     */
    HTTP_PROTOBUF("http/protobuf", 4318, "v1/span");

    private static final EnumMapperProvider MAPPER_PROVIDER = new EnumMapperProvider();
    static final String DEFAULT_STRING = "grpc";
    static final String DEFAULT_NAME = "GRPC";
    static final OtlpExporterProtocol DEFAULT = GRPC;
    private final String otelConfigValue;
    private final int defaultPort;
    private final String defaultPath;

    OtlpExporterProtocol(String otelConfigValue, int defaultPort, String defaultPath) {
        this.otelConfigValue = otelConfigValue;
        this.defaultPort = defaultPort;
        this.defaultPath = defaultPath;
    }

    String defaultProtocol() {
        return "http";
    }

    int defaultPort() {
        return defaultPort;
    }

    String defaultHost() {
        return "localhost";
    }

    String defaultPath() {
        return defaultPath;
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
                .orElse(OtlpExporterProtocol.DEFAULT);
    }

    /**
     * Attempts to map a string to the corresponding {@code OtlpExporterProtocol}.
     *
     * @param protocol protocol string
     * @return corresponding {@code OtlpExporterProtocol} value
     */
    static OtlpExporterProtocol from(String protocol) {
        for (OtlpExporterProtocol candidate : OtlpExporterProtocol.values()) {
            if (candidate.otelConfigValue.equals(protocol) || candidate.name().equals(protocol)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown exporter protocol: " + protocol + "; expected one of "
                                                   + Arrays.toString(OtlpExporterProtocol.values()));
    }
}
