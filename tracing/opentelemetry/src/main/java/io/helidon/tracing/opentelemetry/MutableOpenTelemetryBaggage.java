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
package io.helidon.tracing.opentelemetry;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.baggage.BaggageEntry;
import io.opentelemetry.api.baggage.BaggageEntryMetadata;

class MutableOpenTelemetryBaggage implements Baggage {

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
        BaggageEntry entry = values.get(entryKey);
        return entry != null ? entry.getValue() : null;
    }

    @Override
    public BaggageBuilder toBuilder() {
        return new Builder(values);
    }

    void baggage(String key, String value) {
        values.put(key, new HBaggageEntry(value, new HBaggageEntryMetadata("")));
    }

    record HBaggageEntry(String value, BaggageEntryMetadata metadata) implements BaggageEntry {

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public BaggageEntryMetadata getMetadata() {
            return metadata;
        }
    }

    static class Builder implements BaggageBuilder {

        private final Map<String, BaggageEntry> values = new HashMap<>();

        private Builder(Map<String, BaggageEntry> values) {
            this.values.putAll(values);
        }

        @Override
        public BaggageBuilder put(String key, String value, BaggageEntryMetadata entryMetadata) {
            values.put(key, new HBaggageEntry(value, entryMetadata));
            return this;
        }

        @Override
        public BaggageBuilder remove(String key) {
            values.remove(key);
            return this;
        }

        @Override
        public Baggage build() {
            return new MutableOpenTelemetryBaggage(this);
        }
    }

    record HBaggageEntryMetadata(String metadata) implements BaggageEntryMetadata {
        @Override
        public String getValue() {
            return metadata;
        }
    }
}
