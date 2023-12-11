/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.util.Optional;

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
     * Constructs a new instance for service loading.
     *
     * @deprecated
     */
    @Deprecated
    public MicrometerPrometheusFormatterProvider() {
    }

    @Override
    public Optional<MeterRegistryFormatter> formatter(MediaType mediaType,
                                                      MetricsConfig metricsConfig,
                                                      MeterRegistry meterRegistry,
                                                      Optional<String> scopeTagName,
                                                      Iterable<String> scopeSelection,
                                                      Iterable<String> nameSelection) {
        return matches(mediaType, MediaTypes.TEXT_PLAIN) || matches(mediaType, MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                ? Optional.of(create(mediaType,
                                     metricsConfig,
                                     meterRegistry,
                                     scopeTagName,
                                     scopeSelection,
                                     nameSelection))
                : Optional.empty();
    }

    private static boolean matches(MediaType a, MediaType b) {
        return a.type().equals(b.type()) && a.subtype().equals(b.subtype());
    }

    private static MicrometerPrometheusFormatter create(MediaType mediaType,
                                                        MetricsConfig metricsConfig,
                                                        MeterRegistry meterRegistry,
                                                        Optional<String> scopeTagName,
                                                        Iterable<String> scopeSelection,
                                                        Iterable<String> nameSelection) {
        MicrometerPrometheusFormatter.Builder builder = MicrometerPrometheusFormatter.builder(meterRegistry)
                .resultMediaType(mediaType)
                .scopeSelection(scopeSelection)
                .meterNameSelection(nameSelection);
        scopeTagName.ifPresent(builder::scopeTagName);
        return builder.build();
    }
}
