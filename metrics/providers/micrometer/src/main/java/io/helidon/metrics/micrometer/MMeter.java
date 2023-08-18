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
package io.helidon.metrics.micrometer;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;

/**
 * Adapter to Micrometer meter for Helidon metrics.
 */
class MMeter<M extends Meter> implements io.helidon.metrics.api.Meter {

    private static final String DEFAULT_SCOPE = "application";

    private final M delegate;
    private final io.helidon.metrics.api.Meter.Id id;

    private Optional<String> scope;
    private boolean isDeleted = false;

    protected MMeter(M delegate, Builder<?, ?, ?, ?> builder) {
        this(delegate, builder.scope);
    }

    protected MMeter(M delegate) {
        this(delegate, (String) null);
    }

    private MMeter(M delegate, String scope) {
        this.delegate = delegate;
        id = Id.of(delegate.getId());
        this.scope = Optional.ofNullable(scope);
    }

    @Override
    public io.helidon.metrics.api.Meter.Id id() {
        return id;
    }

    @Override
    public String baseUnit() {
        return delegate.getId().getBaseUnit();
    }

    @Override
    public String description() {
        return delegate.getId().getDescription();
    }

    @Override
    public Type type() {
        return io.helidon.metrics.api.Meter.Type.valueOf(delegate.getId()
                                                                 .getType()
                                                                 .name());
    }

    @Override
    public Optional<String> scope() {
        return scope;
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
        this.scope = Optional.of(scope);
    }

    protected boolean isDeleted() {
        return isDeleted;
    }

    protected void markAsDeleted() {
        isDeleted = true;
    }

    /**
     * Builder for a wrapping meter around a Micrometer meter.
     *
     * @param <B> type of the Micrometer builder (there is no common supertype for all Micrometer meter builders)
     * @param <M> type of the Micrometer meter to build
     * @param <HB> type of the Helidon meter builder which wraps the Micrometer meter builder
     * @param <HM> type of the Helidon meter which wraps the Micrometer meter
     */
    abstract static class Builder<B, M extends Meter, HB extends Builder<B, M, HB, HM>, HM extends MMeter<M>> {

        private final B delegate;
        private String scope;

        protected Builder(B delegate) {
            this.delegate = delegate;
        }

        protected B delegate() {
            return delegate;
        }

        public HB tags(Iterable<io.helidon.metrics.api.Tag> tags) {
           return delegateTags(MTag.tags(tags));
        }

        public HB description(String description) {
            return delegateDescription(description);
        }

        public HB baseUnit(String baseUnit) {
            return delegateBaseUnit(baseUnit);
        }

        public HB scope(String scope) {
            this.scope = scope;
            return identity();
        }

        public HB identity() {
            return (HB) this;
        }

        protected String scope() {
            return scope;
        }

        protected abstract HB delegateTags(Iterable<Tag> tags);
        protected abstract HB delegateDescription(String description);
        protected abstract HB delegateBaseUnit(String baseUnit);


        protected abstract HM build(M meter);

    }

    static class Id implements io.helidon.metrics.api.Meter.Id {

        static Id of(Meter.Id id) {
            return new Id(id);
        }

        private final Meter.Id delegate;

        private Id(Meter.Id delegate) {
            this.delegate = delegate;
        }

        @Override
        public String name() {
            return delegate.getName();
        }

        @Override
        public Iterable<io.helidon.metrics.api.Tag> tags() {
            return new Iterable<>() {

                private final Iterator<Tag> iter = delegate.getTags().iterator();
                @Override
                public Iterator<io.helidon.metrics.api.Tag> iterator() {
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return iter.hasNext();
                        }

                        @Override
                        public io.helidon.metrics.api.Tag next() {
                            return MTag.create(iter.next());
                        }
                    };
                }
            };
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
}
