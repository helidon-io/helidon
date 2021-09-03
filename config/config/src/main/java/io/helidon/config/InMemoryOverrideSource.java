/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import io.helidon.config.spi.ConfigContent.OverrideContent;
import io.helidon.config.spi.OverrideSource;

/**
 * In-memory implementation of override source.
 */
public class InMemoryOverrideSource implements OverrideSource {

    private final OverrideData overrideData;

    private InMemoryOverrideSource(Builder builder) {
        this.overrideData = builder.overrideData;
    }

    static Builder builder(Map<String, String> overrideValues) {
        return new Builder(overrideValues.entrySet()
                                   .stream()
                                   .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()))
                                   .collect(Collectors.toList()));
    }

    @Override
    public Optional<OverrideContent> load() throws ConfigException {
        return Optional.of(OverrideContent.builder()
                                   .data(overrideData)
                                   .build());
    }

    /**
     * Fluent API builder for {@link io.helidon.config.InMemoryOverrideSource}.
     */
    public static final class Builder implements io.helidon.common.Builder<InMemoryOverrideSource> {

        private OverrideData overrideData;
        private List<Map.Entry<String, String>> overrideWildcards;

        Builder(List<Map.Entry<String, String>> overrideWildcards) {
            Objects.requireNonNull(overrideWildcards, "overrideValues cannot be null");

            this.overrideWildcards = overrideWildcards;
        }

        @Override
        public InMemoryOverrideSource build() {
            if (!overrideWildcards.isEmpty()) {
                overrideData = OverrideSource.OverrideData.createFromWildcards(overrideWildcards);
            } else {
                throw new ConfigException("Override values cannot be empty.");
            }

            return new InMemoryOverrideSource(this);
        }
    }
}
