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
import java.util.function.Function;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;

/**
 * Adapter to Micrometer meter for Helidon metrics.
 */
class MMeter<M extends Meter> implements io.helidon.metrics.api.Meter {

    private final M delegate;
    private final io.helidon.metrics.api.Meter.Id id;

    protected MMeter(M delegate) {
        this.delegate = delegate;
        id = Id.of(delegate.getId());
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

    protected M delegate() {
        return delegate;
    }

    abstract static class Builder<B, HB extends Builder<B, HB, HM>, HM extends io.helidon.metrics.api.Meter> {

        private final B delegate;
        private Function<Iterable<Tag>, B> tagsSetter;
        private Function<String, B> descriptionSetter;
        private Function<String, B> baseUnitSetter;

        protected Builder(B delegate) {
            this.delegate = delegate;
        }

        protected void prep(Function<Iterable<Tag>, B> tagsSetter,
                            Function<String, B> descriptionSetter,
                            Function<String, B> baseUnitSetter) {
            this.tagsSetter = tagsSetter;
            this.descriptionSetter = descriptionSetter;
            this.baseUnitSetter = baseUnitSetter;
        }

        protected B delegate() {
            return delegate;
        }

        public HB tags(Iterable<io.helidon.metrics.api.Tag> tags) {
           tagsSetter.apply(MTag.tags(tags));
           return identity();
        }

        public HB description(String description) {
            descriptionSetter.apply(description);
            return identity();
        }

        public HB baseUnit(String baseUnit) {
            baseUnitSetter.apply(baseUnit);
            return identity();
        }

        public HB identity() {
            return (HB) this;
        }

//        abstract HM register(MMeterRegistry meterRegistry);

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
        public Iterable<? extends io.helidon.metrics.api.Tag> tags() {
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
    }
}
