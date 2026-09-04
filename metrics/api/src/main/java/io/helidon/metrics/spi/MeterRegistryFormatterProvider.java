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
package io.helidon.metrics.spi;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.media.type.MediaType;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.metrics.api.MetricsConfig;

/**
 * Behavior for providers of meter registry formatters, which (if then can) furnish a formatter given a
 * {@link io.helidon.common.media.type.MediaType}.
 *
 * <p>
 *     We use a provider approach so code can obtain and run formatters that might depend heavily on particular implementations
 *     without the calling code having to share that heavy dependency.
 * </p>
 */
public interface MeterRegistryFormatterProvider {

    /**
     * Returns, if possible, a {@link io.helidon.metrics.api.MeterRegistryFormatter} capable of preparing output according to
     * the specified {@link io.helidon.common.media.type.MediaType} and selections.
     *
     * @param mediaType media type of the desired output
     * @param metricsConfig {@link io.helidon.metrics.api.MetricsConfig} to influence the formatting
     * @param meterRegistry {@link io.helidon.metrics.api.MeterRegistry} from which to gather data
     * @param tagSelections tag names and accepted values to format; empty means no tag-based restriction
     * @param nameSelection meter names to format; empty means no name-based restriction
     * @return compatible formatter; empty if none
     * @since 27.0.0
     */
    @SuppressWarnings("removal")
    default Optional<MeterRegistryFormatter> formatter(MediaType mediaType,
                                                       MetricsConfig metricsConfig,
                                                       MeterRegistry meterRegistry,
                                                       Map<String, Collection<String>> tagSelections,
                                                       Iterable<String> nameSelection) {
        Objects.requireNonNull(mediaType);
        Objects.requireNonNull(metricsConfig);
        Objects.requireNonNull(meterRegistry);
        Objects.requireNonNull(tagSelections);
        Objects.requireNonNull(nameSelection);
        if (!tagSelections.isEmpty()) {
            return Optional.empty();
        }
        return formatter(mediaType,
                         metricsConfig,
                         meterRegistry,
                         Optional.empty(),
                         List.of(),
                         nameSelection);
    }

    /**
     * No-op, will be removed.
     *
     * @param mediaType media type of the desired output
     * @param metricsConfig {@link io.helidon.metrics.api.MetricsConfig} to influence the formatting
     * @param meterRegistry {@link io.helidon.metrics.api.MeterRegistry} from which to gather data
     * @param scopeTagName ignored; must not be {@code null}
     * @param scopeSelection ignored; must not be {@code null}
     * @param nameSelection meter names to format; empty means no name-based restriction
     * @return compatible formatter; empty if none
     * @deprecated No-op, will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    Optional<MeterRegistryFormatter> formatter(MediaType mediaType,
                                               MetricsConfig metricsConfig,
                                               MeterRegistry meterRegistry,
                                               Optional<String> scopeTagName,
                                               Iterable<String> scopeSelection,
                                               Iterable<String> nameSelection);
}
