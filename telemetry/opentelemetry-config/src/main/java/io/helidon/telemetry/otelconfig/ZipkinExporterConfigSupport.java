/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import io.helidon.builder.api.Prototype;
import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.tracing.exporter.zipkin.ZipkinSpanExporter;
import io.helidon.tracing.exporter.zipkin.ZipkinSpanExporterConfig;

import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Compatibility support for the deprecated Helidon Zipkin HTTP exporter.
 */
@SuppressWarnings({"deprecation", "removal"})
class ZipkinExporterConfigSupport {

    private ZipkinExporterConfigSupport() {
    }

    static SpanExporter createExporter(Config config) {
        var builder = ZipkinSpanExporter.builder();
        var zipkinConfig = ZipkinExporterConfig.create(config);
        zipkinConfig.compression().map(CompressionType::lowerCase).ifPresent(builder::compression);
        zipkinConfig.encoder().ifPresent(builder::encoder);
        zipkinConfig.endpoint().ifPresent(builder::endpoint);
        zipkinConfig.timeout().ifPresent(builder::timeout);
        zipkinConfig.sender().ifPresent(builder::sender);
        zipkinConfig.localIpAddressSupplier().ifPresent(builder::localIpAddressSupplier);
        zipkinConfig.meterProvider().ifPresent(builder::meterProvider);
        return builder.build();
    }

    static class BuilderDecorator implements Prototype.BuilderDecorator<ZipkinExporterConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(ZipkinExporterConfig.BuilderBase<?, ?> target) {

            LazyValue<ZipkinSpanExporterConfig.Builder> builder = LazyValue.create(ZipkinSpanExporter::builder);

            target.compression().ifPresent(v -> builder.get().compression(v.lowerCase()));
            target.endpoint().ifPresent(v -> builder.get().endpoint(v));
            target.timeout().ifPresent(v -> builder.get().timeout(v));
            target.sender().ifPresent(v -> builder.get().sender(v));
            target.localIpAddressSupplier().ifPresent(v -> builder.get().localIpAddressSupplier(v));
            target.meterProvider().ifPresent(v -> builder.get().meterProvider(v));
            target.encoder().ifPresent(v -> builder.get().encoder(v));
            builder.get().buildPrototype();

        }
    }

}
