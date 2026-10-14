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

package io.helidon.metrics.publishers.otlp;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.http.HeaderValues;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.providers.helidon.HelidonMetricsPublisher;

/**
 * Publishes Helidon metrics using OTLP HTTP/JSON and cumulative aggregation.
 * Each registry owns a separate publishing session and closes it with the registry.
 */
@Api.Preview
public class OtlpPublisher implements HelidonMetricsPublisher, RuntimeType.Api<OtlpPublisherConfig> {
    private static final Set<String> RESERVED_HEADERS = Set.of("content-type", "content-length", "content-encoding",
                                                               "transfer-encoding", "host", "connection");

    private final OtlpPublisherConfig config;

    private OtlpPublisher(OtlpPublisherConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        var endpoint = config.endpoint();
        if (!("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme()))
                || endpoint.getHost() == null
                || endpoint.getRawUserInfo() != null
                || endpoint.getRawFragment() != null
                || endpoint.getPort() > 65535) {
            throw new IllegalArgumentException("OTLP endpoint must be an absolute HTTP(S) URI without user info or a fragment");
        }
        validateDuration(config.interval(), "interval");
        validateDuration(config.timeout(), "timeout");
        try {
            long maxRequestBytes = config.maxRequestSize().toBytes();
            if (maxRequestBytes <= 0 || maxRequestBytes > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("OTLP maximum request size must be between 1 and "
                                                           + Integer.MAX_VALUE + " bytes");
            }
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("OTLP maximum request size is too large", e);
        }
        if (config.serviceName().isBlank()) {
            throw new IllegalArgumentException("OTLP service name must not be blank");
        }
        config.headers().forEach((name, value) -> {
            if (RESERVED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("OTLP request header is controlled by the publisher: " + name);
            }
            HeaderValues.create(name, value);
        });
    }

    /**
     * Creates a builder for an OTLP publisher.
     *
     * @return new builder
     */
    public static OtlpPublisherConfig.Builder builder() {
        return OtlpPublisherConfig.builder();
    }

    /**
     * Creates a publisher with default configuration.
     *
     * @return publisher
     */
    public static OtlpPublisher create() {
        return builder().build();
    }

    /**
     * Creates a publisher from its configuration node.
     *
     * @param config publisher configuration node
     * @return publisher
     */
    public static OtlpPublisher create(Config config) {
        return builder().config(Objects.requireNonNull(config, "config")).build();
    }

    /**
     * Creates a publisher from a configuration prototype.
     *
     * @param config publisher configuration
     * @return publisher
     */
    public static OtlpPublisher create(OtlpPublisherConfig config) {
        return new OtlpPublisher(config);
    }

    /**
     * Creates a publisher by customizing a builder.
     *
     * @param consumer builder customization
     * @return publisher
     */
    public static OtlpPublisher create(Consumer<OtlpPublisherConfig.Builder> consumer) {
        return builder().update(Objects.requireNonNull(consumer, "consumer")).build();
    }

    @Override
    public boolean enabled() {
        return config.enabled();
    }

    @Override
    public String name() {
        return config.name().orElse(OtlpPublisherProvider.TYPE);
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
    public Session start(MeterRegistry registry, MetricsConfig metricsConfig) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(metricsConfig, "metricsConfig");
        if (!enabled() || !metricsConfig.enabled()) {
            return () -> { };
        }
        return new OtlpSession(registry, metricsConfig, config);
    }

    private static void validateDuration(Duration duration, String property) {
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("OTLP " + property + " must be positive");
        }
        try {
            duration.toNanos();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("OTLP " + property + " is too large", e);
        }
    }
}
