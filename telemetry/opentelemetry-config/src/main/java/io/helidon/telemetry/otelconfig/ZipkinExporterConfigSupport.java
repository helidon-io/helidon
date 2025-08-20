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

import io.helidon.builder.api.Prototype;
import io.helidon.common.LazyValue;

import io.opentelemetry.exporter.zipkin.ZipkinSpanExporterBuilder;

class ZipkinExporterConfigSupport {

    private ZipkinExporterConfigSupport() {
    }

    static class BuilderDecorator implements Prototype.BuilderDecorator<ZipkinExporterConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(ZipkinExporterConfig.BuilderBase<?, ?> target) {

            LazyValue<ZipkinSpanExporterBuilder> builder = LazyValue.create(ZipkinSpanExporterBuilder::new);

            target.compression().ifPresent(v -> builder.get().setCompression(v.lowerCase()));
            target.endpoint().ifPresent(v -> builder.get().setEndpoint(v.toASCIIString()));
            target.timeout().ifPresent(v -> builder.get().setReadTimeout(v));
            target.sender().ifPresent(v -> builder.get().setSender(v));
            target.localIpAddressSupplier().ifPresent(v -> builder.get().setLocalIpAddressSupplier(v));
            target.meterProvider().ifPresent(v -> builder.get().setMeterProvider(v));

        }
    }

}
