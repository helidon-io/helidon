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

import java.time.Duration;
import java.util.Map;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.common.configurable.Resource;

import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Superset of settings used by the various span exporters. Specific exporters might ignore some or all
 * of the settings.
 */
@Prototype.Blueprint
@Prototype.Configured("span-exporters")
@Prototype.CustomMethods(SpanExporterConfigSupport.class)
interface SpanExporterConfigBlueprint {

//    @Prototype.FactoryMethod
//    static SpanExporterConfig create(Config config) {
//        int a = 0;
//        return null;
//    }

    /**
     * Type of span exporter.
     *
     * @return type of span exporter
     */
    @Option.Configured("type")
    @Option.Default(ExporterType.DEFAULT_NAME)
    ExporterType exporterType();

//    /**
//     * OTLP exporter protocol.
//     *
//     * @return OTLP exporter protocol
//     */
//    @Option.Configured(OtlpExporterProtocol.DEFAULT_NAME)
//    OtlpExporterProtocol exporterProtocol();

//    @Option.Configured
//    Duration timeout();
//
//    @Option.Configured("protocol")
//    String collectorProtocol();
//
//    @Option.Configured("host")
//    String collectorHost();
//
//    @Option.Configured("port")
//    int collectorPort();
//
//    @Option.Configured("path")
//    String collectorPath();
//
//    @Option.Configured
//    String compression();
//
//    @Option.Configured
//    Duration exporterTimeout();

//    /**
//     * Headers added to each outbound transmission of span data.
//     *
//     * @return headers
//     */
//    @Option.Configured
//    @Option.Singular
//    Map<String, String> headers();
//
//    @Option.Configured
//    Resource privateKey();
//
//    @Option.Configured
//    Resource clientCertificate();
//
//    @Option.Configured
//    Resource trustedCertificate();



}
