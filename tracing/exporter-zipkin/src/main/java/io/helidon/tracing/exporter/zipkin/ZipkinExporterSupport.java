/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.tracing.exporter.zipkin;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;
import io.helidon.webclient.api.ClientUri;

import zipkin2.Span;

@SuppressWarnings("removal")
final class ZipkinExporterSupport {
    private ZipkinExporterSupport() {
    }

    static Duration readTimeout(Duration timeout) {
        long millis;
        try {
            millis = timeout.toMillis();
        } catch (ArithmeticException ignored) {
            millis = Long.MAX_VALUE;
        }
        // Upstream also interprets positive sub-millisecond durations, truncated to zero, as unbounded.
        return Duration.ofMillis(millis == 0 ? Integer.MAX_VALUE : Math.min(millis, Integer.MAX_VALUE));
    }

    static boolean hasHost(URI endpoint) {
        if (endpoint.getRawAuthority() == null) {
            return false;
        }
        try {
            return !ClientUri.create(endpoint).host().isBlank();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static void validate(ZipkinSpanExporterConfig.BuilderBase<?, ?> builder, Errors.Collector errors) {
        URI endpoint = null;
        if (builder.endpoint().isBlank()) {
            errors.fatal("endpoint must not be blank");
        }
        try {
            endpoint = URI.create(builder.endpoint());
        } catch (IllegalArgumentException e) {
            errors.fatal("endpoint must be a syntactically valid URI");
        }
        if (endpoint != null && builder.sender().isEmpty()) {
            if (!"http".equalsIgnoreCase(endpoint.getScheme()) && !"https".equalsIgnoreCase(endpoint.getScheme())) {
                errors.fatal("endpoint must use the http or https scheme");
            }
            if (!hasHost(endpoint)) {
                errors.fatal("endpoint must contain a host with a valid optional port");
            }
            if (endpoint.getPort() < -1 || endpoint.getPort() == 0 || endpoint.getPort() > 65535) {
                errors.fatal("endpoint port must be between 1 and 65535 when specified");
            }
        }
        if (builder.timeout().isNegative()) {
            errors.fatal("timeout must not be negative");
        }
        if (!"gzip".equals(builder.compression()) && !"none".equals(builder.compression())) {
            errors.fatal("compression must be gzip or none");
        }
    }

    static final class Decorator implements Prototype.BuilderDecorator<ZipkinSpanExporterConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(ZipkinSpanExporterConfig.BuilderBase<?, ?> builder) {
            Errors.Collector errors = Errors.collector();
            validate(builder, errors);
            errors.collect().checkValid();
        }
    }

    static final class CustomMethods {
        private CustomMethods() {
        }

        /**
         * Use a legacy Zipkin encoder.
         *
         * @param builder exporter builder
         * @param encoder legacy encoder
         */
        @Prototype.BuilderMethod
        static void encoder(ZipkinSpanExporterConfig.BuilderBase<?, ?> builder, zipkin2.codec.BytesEncoder<Span> encoder) {
            builder.encoder(new BytesEncoderAdapter(Objects.requireNonNull(encoder, "encoder")));
        }

        /**
         * Set the collector URI.
         *
         * @param builder exporter builder
         * @param endpoint collector URI
         */
        @Prototype.BuilderMethod
        static void endpoint(ZipkinSpanExporterConfig.BuilderBase<?, ?> builder, URI endpoint) {
            builder.endpoint(Objects.requireNonNull(endpoint, "endpoint").toASCIIString());
        }
    }
}
