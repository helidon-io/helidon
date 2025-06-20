/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.TreeMap;

import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Wrapper;

import io.micrometer.core.instrument.Timer;

/**
 * Adapter to Micrometer meter for Helidon metrics.
 */
class MMeter<M extends io.micrometer.core.instrument.Meter> implements Meter {

    private final M delegate;
    private final Meter.Id id;

    private String scope;
    private boolean isDeleted = false;

    protected MMeter(Meter.Id id, M delegate, Builder<?, ?, ?, ?> builder) {
        this(id, delegate, builder.scope);
    }

    protected MMeter(Meter.Id id, M delegate, Optional<String> scope) {
        this(id, delegate, scope.orElse(null));
    }

    protected MMeter(Meter.Id id, M delegate) {
        this(id, delegate, (String) null);
    }

    private MMeter(Meter.Id id, M delegate, String scope) {
        this.delegate = delegate;
        this.id = id;
        this.scope = scope;
    }

    @SuppressWarnings("unchecked")
    static <M extends io.micrometer.core.instrument.Meter,
            HM extends MMeter<M>> HM create(Meter.Id id,
                                            io.micrometer.core.instrument.Meter meter,
                                            Optional<String> scope) {
        if (meter instanceof io.micrometer.core.instrument.Counter counter) {
            return (HM) MCounter.create(id, counter, scope);
        }
        if (meter instanceof io.micrometer.core.instrument.DistributionSummary summary) {
            return (HM) MDistributionSummary.create(id, summary, scope);
        }
        if (meter instanceof io.micrometer.core.instrument.Gauge gauge) {
            return (HM) MGauge.create(id, gauge, scope);
        }
        if (meter instanceof io.micrometer.core.instrument.FunctionCounter fCounter) {
            return (HM) MFunctionalCounter.create(id, fCounter, scope);
        }
        if (meter instanceof Timer timer) {
            return (HM) MTimer.create(id, timer, scope);
        }
        return null;
    }

    @Override
    public Meter.Id id() {
        return id;
    }

    @Override
    public Optional<String> baseUnit() {
        return Optional.ofNullable(delegate.getId().getBaseUnit());
    }

    @Override
    public Optional<String> description() {
        return Optional.ofNullable(delegate.getId().getDescription());
    }

    @Override
    public Type type() {
        return Meter.Type.valueOf(delegate.getId()
                                          .getType()
                                          .name());
    }

    @Override
    public Optional<String> scope() {
        return Optional.ofNullable(scope);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MMeter<?> mMeter = (MMeter<?>) o;
        return Objects.equals(delegate, mMeter.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate);
    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        return c.cast(delegate);
    }

    protected M delegate() {
        return delegate;
    }

    protected void scope(String scope) {
        this.scope = scope;
    }

    protected boolean isDeleted() {
        return isDeleted;
    }

    protected void markAsDeleted() {
        isDeleted = true;
    }

    protected StringJoiner stringJoiner() {
        return new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("scope='" + (scope == null ? "" : scope) + "'");
    }

    /**
     * Builder for a wrapping meter around a Micrometer meter.
     *
     * @param <B>  type of the Micrometer builder (there is no common supertype for all Micrometer meter builders)
     * @param <M>  type of the Micrometer meter to build
     * @param <HB> type of the Helidon meter builder which wraps the Micrometer meter builder
     * @param <HM> type of the Helidon meter which wraps the Micrometer meter
     */
    abstract static class Builder<B,
            M extends io.micrometer.core.instrument.Meter,
            HB extends Builder<B, M, HB, HM>,
            HM extends MMeter<M>>
            implements Wrapper {

        private final String name;
        private final B delegate;

        private final Map<String, String> tags = new TreeMap<>();

        private String scope;
        private String description;
        private String baseUnit;

        protected Builder(String name, B delegate) {
            this.name = name;
            this.delegate = delegate;
        }

        HB from(Meter.Builder<?, ?> neutralBuilder) {
            neutralBuilder.description().ifPresent(this::description);
            neutralBuilder.baseUnit().ifPresent(this::baseUnit);
            neutralBuilder.scope().ifPresent(this::scope);
            neutralBuilder.tags().forEach((key, value) -> this.addTag(MTag.of(key, value)));
            return identity();
        }

        public HB tags(Iterable<Tag> tags) {
            this.tags.clear();
            tags.forEach(tag -> this.tags.put(tag.key(), tag.value()));
            return delegateTags(MTag.tags(tags));
        }

        public HB addTag(Tag tag) {
            tags.put(tag.key(), tag.value());
            return delegateTag(tag.key(), tag.value());
        }

        public HB description(String description) {
            this.description = description;
            if (description != null && !description.isBlank()) {
                delegateDescription(description);
            }
            return identity();
        }

        public HB baseUnit(String baseUnit) {
            this.baseUnit = baseUnit;
            if (baseUnit != null && !baseUnit.isBlank()) {
                delegateBaseUnit(baseUnit);
            }
            return identity();
        }

        public HB scope(String scope) {
            this.scope = scope;
            return identity();
        }

        public HB identity() {
            return (HB) this;
        }

        public String name() {
            return name;
        }

        public Map<String, String> tagsMap() {
            return new TreeMap<>(tags);
        }

        public Optional<String> scope() {
            return Optional.ofNullable(scope);
        }

        public Optional<String> description() {
            return Optional.ofNullable(description);
        }

        public Optional<String> baseUnit() {
            return Optional.ofNullable(baseUnit);
        }

        public Map<String, String> tags() {
            return new TreeMap<>(tags);
        }

        @Override
        public <R> R unwrap(Class<? extends R> c) {
            return c.cast(delegate);
        }

        protected B delegate() {
            return delegate;
        }

        protected Meter.Id id() {
            return PlainId.create(this);
        }

        protected abstract HB delegateTags(Iterable<io.micrometer.core.instrument.Tag> tags);

        protected abstract HB delegateTag(String key, String value);

        protected abstract HB delegateDescription(String description);

        protected abstract HB delegateBaseUnit(String baseUnit);

        protected abstract HM build(Meter.Id id, M meter);

        protected abstract Class<? extends Meter> meterType();

    }

    static class PlainId implements Meter.Id {

        private final String name;
        private final List<Tag> tags;

        private PlainId(String name, Iterable<Tag> neutralTags) {
            this.name = name;
            tags = new ArrayList<>();
            neutralTags.forEach(tags::add);
        }

        private PlainId(MMeter.Builder<?, ?, ?, ?> builder) {
            name = builder.name;
            tags = builder.tags.entrySet()
                    .stream()
                    .map(entry -> Tag.create(entry.getKey(), entry.getValue()))
                    .toList();
        }

        static PlainId create(MMeter.Builder<?, ?, ?, ?> builder) {
            return new PlainId(builder);
        }

        static PlainId create(String name, Iterable<Tag> promTags) {
            return new PlainId(name, promTags);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Iterable<Tag> tags() {
            return tags;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", PlainId.class.getSimpleName() + "[", "]")
                    .add("name='" + name + "'")
                    .add("tags=" + tags)
                    .toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PlainId plainId)) {
                return false;
            }
            return Objects.equals(name, plainId.name) && Objects.equals(tags, plainId.tags);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, tags);
        }

    }
}
