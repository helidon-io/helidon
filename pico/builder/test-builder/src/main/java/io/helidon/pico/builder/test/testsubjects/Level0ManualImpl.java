/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.builder.test.testsubjects;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

// example of what would be code generated
@SuppressWarnings("unchecked")
public class Level0ManualImpl<T extends Level0ManualImpl> implements Level0, Supplier<T> {
    private final String level0StringAttribute;

    protected Level0ManualImpl(Builder builder) {
        this.level0StringAttribute = builder.level0StringAttribute;
    }

    @Override
    public T get() {
        return (T) this;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + toStringInner() + ")";
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) {
            return true;
        }
        if (!(another instanceof io.helidon.pico.builder.test.testsubjects.Level0)) {
            return false;
        }
        io.helidon.pico.builder.test.testsubjects.Level0 other = (io.helidon.pico.builder.test.testsubjects.Level0) another;
        boolean equals = true;
        equals &= Objects.equals(getLevel0StringAttribute(), other.getLevel0StringAttribute());
        return equals;
    }

    protected CharSequence toStringInner() {
        return "level0StringAttribute=" + getLevel0StringAttribute();
    }

    @Override
    public String getLevel0StringAttribute() {
        return level0StringAttribute;
    }

    public static Builder<? extends Builder, ? extends Level0> builder() {
        return new Builder(null);
    }

    public static Builder<? extends Builder, ? extends Level0> toBuilder(Level0 val) {
        return new Builder(val);
    }


    public static class Builder<B extends Builder<B, T>, T extends Level0> implements Supplier<T>, Consumer<T> {
        private String level0StringAttribute = "1";

        protected Builder(T val) {
//            accept(val);
            acceptThis(val);
        }

        protected B identity() {
            return (B) this;
        }

        @Override
        public T get() {
            return (T) build();
        }

        @Override
        public void accept(T val) {
            // super.accept(val);
            acceptThis(val);
        }

        private void acceptThis(T val) {
            if (Objects.isNull(val)) {
                return;
            }

            this.level0StringAttribute = val.getLevel0StringAttribute();
        }

        public B update(Consumer<T> consumer) {
            consumer.accept(get());
            return identity();
        }

        public B level0StringAttribute(String val) {
            this.level0StringAttribute = val;
            return identity();
        }

        public Level0ManualImpl build() {
            return new Level0ManualImpl(this);
        }
    }

    public static class Bldr extends Builder<Bldr, Level0> {
        protected Bldr(Level0 val) {
            super(val);
        }
    }

}
