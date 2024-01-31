/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tracing.providers.opentelemetry;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import io.helidon.tracing.WritableBaggage;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.baggage.BaggageEntry;
import io.opentelemetry.api.baggage.BaggageEntryMetadata;

/**
 * Implementation of the Helidon {@link io.helidon.tracing.WritableBaggage} interface as well as the OpenTelemetry
 * {@link io.opentelemetry.api.baggage.Baggage} interface so a single instance can be used in both roles.
 */
class MutableOpenTelemetryBaggage implements Baggage, WritableBaggage {

    private final Map<String, BaggageEntry> values = new LinkedHashMap<>();

    MutableOpenTelemetryBaggage() {
    }

    private MutableOpenTelemetryBaggage(Builder builder) {
        values.putAll(builder.values);
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public void forEach(BiConsumer<? super String, ? super BaggageEntry> consumer) {
        values.forEach(consumer);
    }

    @Override
    public Map<String, BaggageEntry> asMap() {
        return Collections.unmodifiableMap(values);
    }

    @Override
    public String getEntryValue(String entryKey) {
        Objects.requireNonNull(entryKey, "baggage key cannot be null");
        BaggageEntry entry = values.get(entryKey);
        return entry != null ? entry.getValue() : null;
    }

    @Override
    public BaggageBuilder toBuilder() {
        return new Builder(values);
    }

    void baggage(String key, String value) {
        Objects.requireNonNull(key, "baggage key cannot be null");
        Objects.requireNonNull(value, "baggage value cannot be null");
        values.put(key, new HBaggageEntry(value, new HBaggageEntryMetadata("")));
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key).getValue());
    }

    @Override
    public Set<String> keys() {
        return values.keySet();
    }

    @Override
    public boolean containsKey(String key) {
        Objects.requireNonNull(key, "baggage key cannot be null");
        return values.containsKey(key);
    }

    @Override
    public WritableBaggage set(String key, String value) {
        baggage(key, value);
        return this;
    }

    static class HBaggageEntry implements BaggageEntry {

        private final String value;
        private final BaggageEntryMetadata metadata;

        HBaggageEntry(String value, BaggageEntryMetadata metadata) {
            this.value = value;
            this.metadata = metadata;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public BaggageEntryMetadata getMetadata() {
            return metadata;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof HBaggageEntry that)) {
                return false;
            }
            return Objects.equals(value, that.value) && Objects.equals(metadata, that.metadata);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, metadata);
        }
    }

    static class Builder implements BaggageBuilder {

        private final Map<String, BaggageEntry> values = new HashMap<>();

        private Builder(Map<String, BaggageEntry> values) {
            this.values.putAll(values);
        }

        @Override
        public BaggageBuilder put(String key, String value, BaggageEntryMetadata entryMetadata) {
            Objects.requireNonNull(key, "baggage key cannot be null");
            Objects.requireNonNull(value, "baggage value cannot be null");
            values.put(key, new HBaggageEntry(value, entryMetadata));
            return this;
        }

        @Override
        public BaggageBuilder remove(String key) {
            Objects.requireNonNull(key, "baggage key cannot be null");
            values.remove(key);
            return this;
        }

        @Override
        public Baggage build() {
            return new MutableOpenTelemetryBaggage(this);
        }
    }

    static class HBaggageEntryMetadata implements BaggageEntryMetadata {

        private final String metadata;

        HBaggageEntryMetadata(String metadata) {
            this.metadata = metadata;
        }
        @Override
        public String getValue() {
            return metadata;
        }
    }
}
