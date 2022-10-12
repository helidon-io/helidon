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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// example of what would be code generated
@SuppressWarnings("unchecked")
public class Level2ManualImpl extends Level1ManualImpl implements Level2 {

    private final List<Level0> level2Level0Info;
    private final List<Level0> level2ListOfLevel0s;
    private Map<String, Level1> level2MapOfStringToLevel1s;

    protected Level2ManualImpl(Builder builder) {
        super(builder);
        this.level2Level0Info = Objects.isNull(builder.level2Level0Info)
                ? Collections.emptyList() : Collections.unmodifiableList(new LinkedList<>(builder.level2Level0Info));
        this.level2ListOfLevel0s = Objects.isNull(builder.level2ListOfLevel0s)
                ? Collections.emptyList() : Collections.unmodifiableList(new LinkedList<>(builder.level2ListOfLevel0s));
        this.level2MapOfStringToLevel1s = Objects.isNull(builder.level2MapOfStringToLevel1s)
                ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(builder.level2MapOfStringToLevel1s));
    }

    @Override
    protected CharSequence toStringInner() {
        CharSequence result = super.toStringInner();
        if (result.length() > 0) {
            result += ", ";
        }
        result += "level2Level0Info=" + getLevel2Level0Info() + ", ";
        result += "level2ListOfLevel0s=" + getLevel2ListOfLevel0s() + ", ";
        result += "level2MapOfStringToLevel1s=" + getLevel2MapOfStringToLevel1s();
        return result;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) {
            return true;
        }
        if (!(another instanceof io.helidon.pico.builder.test.testsubjects.Level2)) {
            return false;
        }
        io.helidon.pico.builder.test.testsubjects.Level2 other = (io.helidon.pico.builder.test.testsubjects.Level2) another;
        boolean equals = super.equals(other);
        equals &= Objects.equals(getLevel2Level0Info(), other.getLevel2Level0Info());
        equals &= Objects.equals(getLevel2ListOfLevel0s(), other.getLevel2ListOfLevel0s());
        equals &= Objects.equals(getLevel2MapOfStringToLevel1s(), other.getLevel2MapOfStringToLevel1s());
        return equals;
    }

    @Override
    public List<Level0> getLevel2Level0Info() {
        return level2Level0Info;
    }

    @Override
    public List<Level0> getLevel2ListOfLevel0s() {
        return level2ListOfLevel0s;
    }

    @Override
    public Map<String, Level1> getLevel2MapOfStringToLevel1s() {
        return level2MapOfStringToLevel1s;
    }

    public static Builder<? extends Builder, ? extends Level2> builder() {
        return new Builder(null);
    }

    public static Builder<? extends Builder, ? extends Level2> toBuilder(Level2 val) {
        return new Builder(val);
    }

    public static Bldr bldr() {
        return new Bldr(null);
    }

    public static Bldr toBldr(Level2 val) {
        return new Bldr(val);
    }


    public static class Builder<B extends Builder<B, T>, T extends Level2> extends Level1ManualImpl.Builder<B, T> {
        private List<Level0> level2Level0Info;
        private List<Level0> level2ListOfLevel0s;
        private Map<String, Level1> level2MapOfStringToLevel1s;

        protected Builder(T val) {
            super(val);
            // accept(val);
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

            {
                Collection<Level0> v = val.getLevel2Level0Info();
                this.level2Level0Info = Objects.isNull(v) ? null : new LinkedList<>(v);
            }
            {
                Collection<Level0> v = val.getLevel2ListOfLevel0s();
                this.level2ListOfLevel0s = Objects.isNull(v) ? null : new LinkedList<>(v);
            }
            {
                Map<String, Level1> v = val.getLevel2MapOfStringToLevel1s();
                this.level2MapOfStringToLevel1s = Objects.isNull(v) ? null : new LinkedHashMap<>(v);
            }
        }

        public B level2Level0Info(Collection<Level0> val) {
            this.level2Level0Info = Objects.isNull(val) ? null : new LinkedList<>(val);
            return identity();
        }

        public B addlevel2Level0Info(Level0 val) {
            if (Objects.isNull(level2Level0Info)) {
                level2Level0Info = new LinkedList<>();
            }
            level2Level0Info.add(val);
            return identity();
        }

        public B level2ListOfLevel0s(Collection<Level0> val) {
            this.level2ListOfLevel0s = Objects.isNull(val) ? null : new LinkedList<>(val);
            return identity();
        }

        public B addLevel0(Level0 val) {
            if (Objects.isNull(level2ListOfLevel0s)) {
                level2ListOfLevel0s = new LinkedList<>();
            }
            level2ListOfLevel0s.add(val);
            return identity();
        }

        public B level2MapOfStringToLevel1s(Map<String, Level1> val) {
            this.level2MapOfStringToLevel1s = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return identity();
        }

        public B addStringToLevel1(String key, Level1 val) {
            if (Objects.isNull(level2MapOfStringToLevel1s)) {
                level2MapOfStringToLevel1s = new LinkedHashMap<>();
            }
            level2MapOfStringToLevel1s.put(key, val);
            return identity();
        }

        public Level2ManualImpl build() {
            return new Level2ManualImpl(this);
        }
    }

    public static class Bldr extends Builder<Bldr, Level2> {
        protected Bldr(Level2 val) {
            super(val);
        }
    }

}
