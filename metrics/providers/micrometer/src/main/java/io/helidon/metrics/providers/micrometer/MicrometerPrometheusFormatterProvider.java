/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.spi.MeterRegistryFormatterProvider;

/**
 * Micrometer (and Prometheus, particularly) specific formatter.
 */
public class MicrometerPrometheusFormatterProvider implements MeterRegistryFormatterProvider {

    /**
     * Required public constructor for {@link java.util.ServiceLoader}.
     */
    @Api.Internal
    public MicrometerPrometheusFormatterProvider() {
    }

    /**
     * Returns a formatter, if possible, ignoring the scope-specific arguments.
     *
     * @param mediaType media type of the desired output
     * @param metricsConfig metrics configuration
     * @param meterRegistry meter registry from which to gather data
     * @param ignoredScopeTagName ignored; must not be {@code null}
     * @param ignoredScopeSelection ignored; must not be {@code null}
     * @param nameSelection meter names to format; empty means no name-based restriction
     * @return compatible formatter; empty if none
     * @deprecated Use {@link #formatter(MediaType, MetricsConfig, MeterRegistry, Map, Iterable)}. Scope-specific arguments
     * are ignored and this method will be removed.
     */
    @Override
    @Deprecated(since = "27.0.0", forRemoval = true)
    public Optional<MeterRegistryFormatter> formatter(MediaType mediaType,
                                                      MetricsConfig metricsConfig,
                                                      MeterRegistry meterRegistry,
                                                      Optional<String> ignoredScopeTagName,
                                                      Iterable<String> ignoredScopeSelection,
                                                      Iterable<String> nameSelection) {
        Objects.requireNonNull(ignoredScopeTagName);
        Objects.requireNonNull(ignoredScopeSelection);
        return formatter(mediaType, metricsConfig, meterRegistry, Map.of(), nameSelection);
    }

    @Override
    public Optional<MeterRegistryFormatter> formatter(MediaType mediaType,
                                                      MetricsConfig metricsConfig,
                                                      MeterRegistry meterRegistry,
                                                      Map<String, Collection<String>> tagSelection,
                                                      Iterable<String> nameSelection) {
        Objects.requireNonNull(mediaType);
        Objects.requireNonNull(metricsConfig);
        Objects.requireNonNull(meterRegistry);
        Objects.requireNonNull(tagSelection);
        Objects.requireNonNull(nameSelection);
        return (matches(mediaType, MediaTypes.TEXT_PLAIN) || matches(mediaType, MediaTypes.APPLICATION_OPENMETRICS_TEXT))
                && MicrometerPrometheusFormatter.prometheusMeterRegistry(meterRegistry).isPresent()
                ? Optional.of(create(mediaType,
                                     meterRegistry,
                                     tagSelection,
                                     nameSelection))
                : Optional.empty();
    }

    private static boolean matches(MediaType a, MediaType b) {
        return a.type().equals(b.type()) && a.subtype().equals(b.subtype());
    }

    private static MicrometerPrometheusFormatter create(MediaType mediaType,
                                                        MeterRegistry meterRegistry,
                                                        Map<String, Collection<String>> tagSelection,
                                                        Iterable<String> nameSelection) {
        return MicrometerPrometheusFormatter.builder(meterRegistry)
                .resultMediaType(mediaType)
                .tagSelection(tagSelection)
                .meterNameSelection(nameSelection)
                .build();
    }
}
