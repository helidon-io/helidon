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
package io.helidon.webserver.observe.metrics;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.spi.MeterRegistryFormatterProvider;

/**
 * JSON formatter provider.
 */
public class JsonMeterRegistryFormatterProvider implements MeterRegistryFormatterProvider {

    /**
     * Creates a new instance for service loading.
     */
    public JsonMeterRegistryFormatterProvider() {
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
        return mediaType.type().equals(MediaTypes.APPLICATION_JSON.type())
                && mediaType.subtype().equals(MediaTypes.APPLICATION_JSON.subtype())
                ? Optional.of(create(metricsConfig, meterRegistry, tagSelection, nameSelection))
                : Optional.empty();
    }

    /**
     * No-op, will be removed.
     *
     * @param mediaType media type of the desired output
     * @param metricsConfig metrics configuration
     * @param meterRegistry meter registry from which to gather data
     * @param scopeTagName ignored; must not be {@code null}
     * @param scopeSelection ignored; must not be {@code null}
     * @param nameSelection meter names to format; empty means no name-based restriction
     * @return compatible formatter; empty if none
     * @deprecated No-op, will be removed.
     */
    @Deprecated(since = "27.0.0", forRemoval = true)
    @Override
    public Optional<MeterRegistryFormatter> formatter(MediaType mediaType,
                                                      MetricsConfig metricsConfig,
                                                      MeterRegistry meterRegistry,
                                                      Optional<String> scopeTagName,
                                                      Iterable<String> scopeSelection,
                                                      Iterable<String> nameSelection) {
        Objects.requireNonNull(scopeTagName);
        Objects.requireNonNull(scopeSelection);
        return formatter(mediaType, metricsConfig, meterRegistry, Map.of(), nameSelection);
    }

    private JsonFormatter create(MetricsConfig metricsConfig,
                                 MeterRegistry meterRegistry,
                                 Map<String, Collection<String>> tagSelection,
                                 Iterable<String> nameSelection) {
        return JsonFormatter.builder(metricsConfig, meterRegistry)
                .tagSelection(tagSelection)
                .meterNameSelection(nameSelection)
                .build();
    }
}
