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
package io.helidon.nima.observe.metrics;

import java.util.Optional;

import io.helidon.common.media.type.MediaType;
import io.helidon.metrics.api.MeterRegistry;

/**
 * This class depends on the Prometheus formatter, which in turn depends on Micrometer and Micrometer Prometheus types.
 * If any of those types are not available at runtime, this class will fail to load and the caller must catch the
 * exception and take appropriate action.
 */
class PrometheusMeterRegistryAccess {

    static Optional<String> scrape(MeterRegistry meterRegistry,
                                   MediaType mediaType,
                                   String scopeTagName,
                                   Iterable<String> scopeSelection,
                                   Iterable<String> meterNameSelection) {

        try {
            MicrometerPrometheusFormatter formatter = MicrometerPrometheusFormatter.builder(meterRegistry)
                    .resultMediaType(mediaType)
                    .scopeSelection(scopeSelection)
                    .meterNameSelection(meterNameSelection)
                    .scopeTagName(scopeTagName)
                    .build();
            return formatter.filteredOutput();
        } catch (ClassCastException ex) {
            return Optional.empty();
        }
    }

    private PrometheusMeterRegistryAccess() {
    }
}
