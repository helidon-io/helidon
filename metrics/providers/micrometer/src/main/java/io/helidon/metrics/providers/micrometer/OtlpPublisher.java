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

package io.helidon.metrics.providers.micrometer;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.RuntimeType;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;

/**
 * Metrics publisher for OTLP output.
 */
public class OtlpPublisher implements MicrometerMetricsPublisher,
                                      RuntimeType.Api<OtlpPublisherConfig> {

    private final OtlpPublisherConfig config;

    private OtlpPublisher(OtlpPublisherConfig config) {
        this.config = config;
    }

    /**
     * Returns a builder for composing an OTLP publisher.
     *
     * @return new builder
     */
    public static OtlpPublisherConfig.Builder builder() {
        return OtlpPublisherConfig.builder();
    }

    /**
     * Creates a default OTLP publisher.
     *
     * @return default OTLP publisher
     */
    public static OtlpPublisher create() {
        return builder().build();
    }

    /**
     * Creates a new OTLP publisher using the provided configuration.
     *
     * @param config OTLP publisher configuration
     *
     * @return new OTLP publisher
     */
    public static OtlpPublisher create(OtlpPublisherConfig config) {
        return new OtlpPublisher(config);
    }

    /**
     * Creates a new OTLP publisher using a new builder and applying a consumer of the builder.
     *
     * @param consumer code to process a builder
     *
     * @return new OLTP publisher
     */
    public static OtlpPublisher create(Consumer<OtlpPublisherConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    @Override
    public boolean enabled() {
        return config.enabled();
    }

    @Override
    public String name() {
        return OtlpPublisherProvider.TYPE;
    }

    @Override
    public String type() {
        return OtlpPublisherProvider.TYPE;
    }

    @Override
    public OtlpPublisherConfig prototype() {
        return config;
    }

    @Override
    public Supplier<MeterRegistry> registry() {

        var otlpConfig = new OtlpConfig() {

            @Override
            public String prefix() {
                return config.prefix().orElse(OtlpConfig.super.prefix());
            }

            @Override
            public String url() {
                return config.url().orElse(OtlpConfig.super.url());
            }

            @Override
            public Duration step() {
                return config.interval().orElse(OtlpConfig.super.step());
            }

            @Override
            public String get(String key) {
                return null;
            }
        };

        return () -> new OtlpMeterRegistry(otlpConfig, Clock.SYSTEM);

    }
}
