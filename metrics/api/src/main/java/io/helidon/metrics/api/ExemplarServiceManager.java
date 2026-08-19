/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
package io.helidon.metrics.api;

import java.util.List;
import java.util.StringJoiner;
import java.util.function.Predicate;

import io.helidon.metrics.spi.ExemplarService;
import io.helidon.service.registry.Service;

/**
 * Loads the {@link io.helidon.metrics.spi.ExemplarService} instance (if any) with the most urgent priority.
 */
@Service.Singleton
class ExemplarServiceManager {

    static final String INACTIVE_LABEL = "";
    private final List<ExemplarService> exemplarServices;

    ExemplarServiceManager(List<ExemplarService> exemplarServices) {
        this.exemplarServices = exemplarServices;
    }

    /**
     * Returns a labeled sample using the current exemplar label (e.g., trace ID), if available.
     *
     * @param value sample value
     * @return labeled sample
     */
    Sample.Labeled labeled(double value) {
        if (exemplarServices.isEmpty()) {
            return new LabeledSample(value, INACTIVE_LABEL, 0);
        }
        String label = exemplarServices.stream()
                .map(ExemplarService::label)
                .filter(Predicate.not(String::isBlank))
                .collect(ExemplarServiceManager::labelsStringJoiner, StringJoiner::add, StringJoiner::merge)
                .toString();
        return new LabeledSample(value, label, System.currentTimeMillis());
    }

    private static StringJoiner labelsStringJoiner() {
        // A StringJoiner that suppresses the prefix and suffix if no strings were added
        return new StringJoiner(",", "{", "}").setEmptyValue("");
    }
}
