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

// example of what would be code generated
@SuppressWarnings("unchecked")
public class Level1ManualImpl extends Level0ManualImpl implements Level1 {
    private final int level1intAttribute;
    private final Integer level1IntegerAttribute;
    private final boolean level1booleanAttribute;
    private final Boolean level1BooleanAttribute;

    protected Level1ManualImpl(Builder builder) {
        super(builder);
        this.level1intAttribute = builder.level1intAttribute;
        this.level1IntegerAttribute = builder.level1IntegerAttribute;
        this.level1booleanAttribute = builder.level1booleanAttribute;
        this.level1BooleanAttribute = builder.level1BooleanAttribute;
    }

    @Override
    protected CharSequence toStringInner() {
        CharSequence result = super.toStringInner();
        if (result.length() > 0) {
            result += ", ";
        }
        result += "level1intAttribute=" + getLevel1intAttribute() + ", ";
        result += "level1IntegerAttribute=" + getLevel1IntegerAttribute() + ", ";
        result += "level1booleanAttribute=" + getLevel1booleanAttribute() + ", ";
        result += "level1BooleanAttribute=" + getLevel1BooleanAttribute();
        return result;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) {
            return true;
        }
        if (!(another instanceof io.helidon.pico.builder.test.testsubjects.Level1)) {
            return false;
        }
        io.helidon.pico.builder.test.testsubjects.Level1 other = (io.helidon.pico.builder.test.testsubjects.Level1) another;
        boolean equals = super.equals(other);
        equals &= Objects.equals(getLevel1intAttribute(), other.getLevel1intAttribute());
        equals &= Objects.equals(getLevel1IntegerAttribute(), other.getLevel1IntegerAttribute());
        equals &= Objects.equals(getLevel1booleanAttribute(), other.getLevel1booleanAttribute());
        equals &= Objects.equals(getLevel1BooleanAttribute(), other.getLevel1BooleanAttribute());
        return equals;
    }

    @Override
    public int getLevel1intAttribute() {
        return level1intAttribute;
    }

    @Override
    public Integer getLevel1IntegerAttribute() {
        return level1IntegerAttribute;
    }

    @Override
    public boolean getLevel1booleanAttribute() {
        return level1booleanAttribute;
    }

    @Override
    public Boolean getLevel1BooleanAttribute() {
        return level1BooleanAttribute;
    }

    public static Builder<? extends Builder, ? extends Level1> builder() {
        return new Builder(null);
    }

    public static Builder<? extends Builder, ? extends Level1> toBuilder(Level1 val) {
        return new Builder(val);
    }


    public static class Builder<B extends Builder<B, T>, T extends Level1> extends Level0ManualImpl.Builder<B, T> {
        private int level1intAttribute = 1;
        private Integer level1IntegerAttribute = 1;
        private boolean level1booleanAttribute = true;
        private Boolean level1BooleanAttribute;

        protected Builder(T val) {
            super(val);
            acceptThis(val);
        }

        @Override
        public void accept(T val) {
            super.accept(val);
            acceptThis(val);
        }

        private void acceptThis(T val) {
            if (Objects.isNull(val)) {
                return;
            }

            this.level1intAttribute = val.getLevel1intAttribute();
            this.level1IntegerAttribute = val.getLevel1IntegerAttribute();
            this.level1booleanAttribute = val.getLevel1booleanAttribute();
            this.level1BooleanAttribute = val.getLevel1BooleanAttribute();
        }

        public B level1intAttribute(int val) {
            level1intAttribute = val;
            return identity();
        }

        public B level1IntegerAttribute(Integer val) {
            level1IntegerAttribute = val;
            return identity();
        }

        public B level1booleanAttribute(boolean val) {
            level1booleanAttribute = val;
            return identity();
        }

        public B level1BooleanAttribute(Boolean val) {
            level1BooleanAttribute = val;
            return identity();
        }

        public Level1ManualImpl build() {
            return new Level1ManualImpl(this);
        }
    }

    public static class Bldr extends Builder<Bldr, Level1> {
        protected Bldr(Level1 val) {
            super(val);
        }
    }
}
