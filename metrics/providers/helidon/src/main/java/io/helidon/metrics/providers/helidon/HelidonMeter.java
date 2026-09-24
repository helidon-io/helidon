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

package io.helidon.metrics.providers.helidon;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.Tag;

abstract class HelidonMeter implements Meter {
    private final Meter.Id id;
    private final Type type;
    private final Optional<String> baseUnit;
    private final Optional<String> description;
    private volatile boolean deleted;

    HelidonMeter(Meter.Id id, Type type, AbstractBuilder<?, ?> builder) {
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
        this.baseUnit = Objects.requireNonNull(builder).baseUnit();
        this.description = builder.description();
    }

    @Override
    public Meter.Id id() {
        return id;
    }

    @Override
    public Optional<String> baseUnit() {
        return baseUnit;
    }

    @Override
    public Optional<String> description() {
        return description;
    }

    @Override
    public Type type() {
        return type;
    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        return Objects.requireNonNull(c).cast(this);
    }

    boolean isDeleted() {
        return deleted;
    }

    void markAsDeleted() {
        deleted = true;
    }

    String stringPrefix() {
        return getClass().getSimpleName() + "[id=" + id + ", ";
    }

    abstract static class AbstractBuilder<B extends Meter.Builder<B, M>, M extends Meter>
            implements Meter.Builder<B, M> {
        private final String name;
        private final SortedMap<String, String> tags = new TreeMap<>();
        private String description;
        private String baseUnit;
        private String origin;

        AbstractBuilder(String name) {
            this.name = Objects.requireNonNull(name);
        }

        B from(Meter.Builder<?, ?> builder) {
            Objects.requireNonNull(builder);
            tags(HelidonTypes.tagsFrom(builder.tags()));
            builder.description().ifPresent(this::description);
            builder.baseUnit().ifPresent(this::baseUnit);
            builder.origin().ifPresent(this::origin);
            return identity();
        }

        @Override
        public B tags(Iterable<Tag> tags) {
            this.tags.clear();
            Objects.requireNonNull(tags).forEach(tag -> {
                Objects.requireNonNull(tag);
                this.tags.put(tag.key(), tag.value());
            });
            return identity();
        }

        @Override
        public B addTag(Tag tag) {
            Objects.requireNonNull(tag);
            tags.put(tag.key(), tag.value());
            return identity();
        }

        @Override
        public B description(String description) {
            // Existing meter builders accept null for unspecified optional metadata.
            this.description = description;
            return identity();
        }

        @Override
        public B baseUnit(String baseUnit) {
            // Unitless built-in meters pass null, as supported by the existing providers.
            this.baseUnit = baseUnit;
            return identity();
        }

        @Override
        public B origin(String origin) {
            this.origin = Objects.requireNonNull(origin);
            return identity();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Map<String, String> tags() {
            return new TreeMap<>(tags);
        }

        @Override
        public Optional<String> description() {
            return Optional.ofNullable(description);
        }

        @Override
        public Optional<String> baseUnit() {
            return Optional.ofNullable(baseUnit);
        }

        @Override
        public Optional<String> origin() {
            return Optional.ofNullable(origin);
        }

        @Override
        public <R> R unwrap(Class<? extends R> c) {
            return Objects.requireNonNull(c).cast(this);
        }

        HelidonMeterId id() {
            return new HelidonMeterId(name, tags);
        }

        abstract Class<? extends Meter> meterType();
    }
}
