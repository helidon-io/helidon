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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.TreeMap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

/**
 * Adapter to Micrometer meter for Helidon metrics.
 */
class MMeter<M extends Meter> implements io.helidon.metrics.api.Meter {

    private static final String DEFAULT_SCOPE = "application";

    private final M delegate;
    private final io.helidon.metrics.api.Meter.Id id;

    private String scope;
    private boolean isDeleted = false;

    protected MMeter(M delegate, Builder<?, ?, ?, ?> builder) {
        this(delegate, builder.scope);
    }

    protected MMeter(M delegate, Optional<String> scope) {
        this(delegate, scope.orElse(null));
    }

    protected MMeter(M delegate) {
        this(delegate, (String) null);
    }

    private MMeter(M delegate, String scope) {
        this.delegate = delegate;
        id = Id.create(delegate.getId());
        this.scope = scope;
    }

    @Override
    public io.helidon.metrics.api.Meter.Id id() {
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
        return io.helidon.metrics.api.Meter.Type.valueOf(delegate.getId()
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

    static <M extends Meter, HM extends MMeter<M>> HM create(Meter meter, Optional<String> scope) {
        if (meter instanceof Counter counter) {
            return (HM) MCounter.create(counter, scope);
        }
        if (meter instanceof DistributionSummary summary) {
            return (HM) MDistributionSummary.create(summary, scope);
        }
        if (meter instanceof Gauge gauge) {
            return (HM) MGauge.create(gauge, scope);
        }
        if (meter instanceof FunctionCounter fCounter) {
            return (HM) MFunctionalCounter.create(fCounter, scope);
        }
        if (meter instanceof Timer timer) {
            return (HM) MTimer.create(timer, scope);
        }
        return null;
    }

    /**
     * Builder for a wrapping meter around a Micrometer meter.
     *
     * @param <B>  type of the Micrometer builder (there is no common supertype for all Micrometer meter builders)
     * @param <M>  type of the Micrometer meter to build
     * @param <HB> type of the Helidon meter builder which wraps the Micrometer meter builder
     * @param <HM> type of the Helidon meter which wraps the Micrometer meter
     */
    abstract static class Builder<B, M extends Meter, HB extends Builder<B, M, HB, HM>, HM extends MMeter<M>> {

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

        public HB tags(Iterable<io.helidon.metrics.api.Tag> tags) {
            this.tags.clear();
            tags.forEach(tag -> this.tags.put(tag.key(), tag.value()));
            return delegateTags(MTag.tags(tags));
        }

        public HB addTag(io.helidon.metrics.api.Tag tag) {
            tags.put(tag.key(), tag.value());
            return delegateTag(tag.key(), tag.value());
        }

        public HB description(String description) {
            this.description = description;
            return delegateDescription(description);
        }

        public HB baseUnit(String baseUnit) {
            this.baseUnit = baseUnit;
            return delegateBaseUnit(baseUnit);
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

        protected B delegate() {
            return delegate;
        }

        protected io.helidon.metrics.api.Meter.Id id() {
            return PlainId.create(this);
        }

        protected abstract HB delegateTags(Iterable<Tag> tags);

        protected abstract HB delegateTag(String key, String value);

        protected abstract HB delegateDescription(String description);

        protected abstract HB delegateBaseUnit(String baseUnit);

        protected abstract HM build(M meter);

    }

    static class Id implements io.helidon.metrics.api.Meter.Id {

        private final Meter.Id delegate;

        private Id(Meter.Id delegate) {
            this.delegate = delegate;
        }

        static Id create(Meter.Id id) {
            return new Id(id);
        }

        @Override
        public String name() {
            return delegate.getName();
        }

        @Override
        public String toString() {
            return String.format("ID[name=%s,tagsMap=[%s]]", delegate.getName(), delegate.getTags());
        }

        @Override
        public Iterable<io.helidon.metrics.api.Tag> tags() {
            return MTag.neutralTags(delegate.getTags());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Id id = (Id) o;
            return Objects.equals(delegate, id.delegate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(delegate);
        }
    }

    static class PlainId implements io.helidon.metrics.api.Meter.Id {

        private final String name;
        private final List<io.helidon.metrics.api.Tag> tags;

        static PlainId create(MMeter.Builder<?, ?, ?, ?> builder) {
            return new PlainId(builder);
        }

        static PlainId create(Meter meter) {
            return new PlainId(meter);
        }

        private static io.helidon.metrics.api.Tag tag(io.micrometer.core.instrument.Tag tag) {
            return io.helidon.metrics.api.Tag.create(tag.getKey(), tag.getValue());
        }

        private PlainId(Meter meter) {
            name = meter.getId().getName();
            tags = meter.getId()
                    .getTags()
                    .stream()
                    .map(PlainId::tag)
                    .toList();
        }

        private PlainId(MMeter.Builder<?, ?, ?, ?> builder) {
            name = builder.name;
            tags = builder.tags.entrySet()
                    .stream()
                    .map(entry -> io.helidon.metrics.api.Tag.create(entry.getKey(), entry.getValue()))
                    .toList();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Iterable<io.helidon.metrics.api.Tag> tags() {
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
