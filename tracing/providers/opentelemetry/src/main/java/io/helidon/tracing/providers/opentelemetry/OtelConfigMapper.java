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

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.Weight;
import io.helidon.config.Config;
import io.helidon.config.spi.ConfigMapper;
import io.helidon.config.spi.ConfigMapperProvider;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Config mappers for OpenTelemetry-related types.
 * <p>
 * Span exporters and span processors in particular have different implementations, selected by {@code type} settings in their
 * respective configuration blueprints. When the config system needs to create an exporter or processor based on configuration
 * this mapper uses the configured type indicator to construct the correct concrete implementation.
 */
@Weight(110d)
public class OtelConfigMapper implements ConfigMapperProvider {

    @Override
    public Map<Class<?>, Function<Config, ?>> mappers() {
        return Map.of(SpanExporter.class, OtelConfigMapper::createSpanExporter,
                      SpanProcessorConfig.class, OtelConfigMapper::createSpanProcessorConfig,
                      OtlpExporterProtocol.class, OtelConfigMapper::createOtlpExporterProtocol,
                      ContextPropagation.class, OtelConfigMapper::createContextPropagation);
    }

    @Override
    public Map<GenericType<?>, BiFunction<Config, ConfigMapper, ?>> genericTypeMappers() {
        // Sometimes the generated code seems to try to look up an enum type using a generic type, so we have
        // provide this method as well as the more straight-forward mapper.
        return Map.of(GenericType.create(OtlpExporterProtocol.class), OtelConfigMapper::createOtlpExporterProtocol,
                      GenericType.create(ContextPropagation.class), OtelConfigMapper::createContextPropagation);
    }

    private static SpanExporter createSpanExporter(Config spanExporterConfig) {
        ExporterType exporterType = spanExporterConfig.get("type").as(ExporterType.class).orElse(ExporterType.DEFAULT);

        return switch (exporterType) {
            case OTLP -> {
                OtlpExporterProtocol exporterProtocol = spanExporterConfig.get("exporter-protocol").map(OtlpExporterProtocol::from)
                        .orElse(OtlpExporterProtocol.GRPC);
                yield switch (exporterProtocol) {
                    case GRPC -> spanExporterConfig.map(OtlpSpanExporterConfigSupport::createGrpcSpanExporter).get();
                    case HTTP_PROTOBUF -> spanExporterConfig.map(OtlpSpanExporterConfigSupport::createHttpProtobufSpanExporter).get();
                };
            }
            case ZIPKIN -> spanExporterConfig.map(ZipkinSpanExporterConfigSupport::createGrpcSpanExporter).get();

            // The remaining cases have no parameter settings, so bypass spanExporterConfig and just create the appropriate subtype.
            case CONSOLE -> LoggingSpanExporter.create();
            case LOGGING_OTLP -> OtlpJsonLoggingSpanExporter.create();

        };
    }

    private static SpanProcessorConfig createSpanProcessorConfig(Config config) {
        SpanProcessorType type = config.get("span-processor-type").as(SpanProcessorType.class).orElseThrow();


        return switch (type) {
            case SIMPLE -> SpanProcessorConfig.create(config);
            case BATCH -> BatchSpanProcessorConfig.create(config);
        };
    }

    private static OtlpExporterProtocol createOtlpExporterProtocol(Config config) {
        return OtlpExporterProtocol.from(config.asString().orElseThrow());
    }

    private static OtlpExporterProtocol createOtlpExporterProtocol(Config config, ConfigMapper configMapper) {
        return createOtlpExporterProtocol(config);
    }

    private static ContextPropagation createContextPropagation(Config config) {
        return ContextPropagation.from(config.asString().orElseThrow());
    }

    private static ContextPropagation createContextPropagation(Config config, ConfigMapper configMapper) {
        return createContextPropagation(config);
    }
}
