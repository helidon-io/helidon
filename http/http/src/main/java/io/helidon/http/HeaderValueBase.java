/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperException;
import io.helidon.common.mapper.MapperManager;
import io.helidon.common.mapper.Value;

abstract class HeaderValueBase implements Header {
    private static final String[] QUALIFIER = new String[] {"http", "header"};
    private final HeaderName name;
    private final String actualName;
    private final String firstValue;
    private final boolean changing;
    private final boolean sensitive;

    HeaderValueBase(HeaderName name, boolean changing, boolean sensitive, String value) {
        this.name = name;
        this.actualName = name.defaultCase();
        this.changing = changing;
        this.sensitive = sensitive;
        this.firstValue = value;
    }

    @Override
    public String name() {
        return actualName;
    }

    @Override
    public HeaderName headerName() {
        return name;
    }

    @Override
    public String get() {
        return firstValue;
    }

    @Override
    public <T> T get(Class<T> type) {
        return MapperManager.global().map(get(), String.class, type, QUALIFIER);
    }

    @Override
    public <N> Value<N> as(Function<? super String, ? extends N> mapper) {
        return Value.create(MapperManager.global(), name(), mapper.apply(get()), QUALIFIER);
    }

    @Override
    public <N> Value<N> as(Class<N> type) throws MapperException {
        return asString().as(type);
    }

    @Override
    public Value<String> asString() {
        return Value.create(MapperManager.global(), name(), get(), GenericType.STRING, QUALIFIER);
    }

    @Override
    public <N> Value<N> as(GenericType<N> type) throws MapperException {
        return asString().as(type);
    }

    @Override
    public Optional<String> asOptional() throws MapperException {
        return asString().asOptional();
    }

    @Override
    public Value<Boolean> asBoolean() {
        return asString().asBoolean();
    }

    @Override
    public Value<Integer> asInt() {
        return asString().asInt();
    }

    @Override
    public Value<Long> asLong() {
        return asString().asLong();
    }

    @Override
    public Value<Double> asDouble() {
        return asString().asDouble();
    }

    @Override
    public abstract int valueCount();

    @Override
    public boolean sensitive() {
        return sensitive;
    }

    @Override
    public boolean changing() {
        return changing;
    }

    @Override
    public int hashCode() {
        return Objects.hash(changing, sensitive, actualName, allValues());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HeaderValueBase that)) {
            return false;
        }
        int valueCount = valueCount();
        if (changing != that.changing
                || sensitive != that.sensitive
                || !actualName.equalsIgnoreCase(that.actualName)
                || valueCount != that.valueCount()) {
            return false;
        }
        List<String> thatValues = that.allValues();
        for (String thisValue : allValues()) {
            boolean found = false;
            for (String thatValue : thatValues) {
                if (thisValue.equalsIgnoreCase(thatValue)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "HttpHeaderImpl["
                + "name=" + name + ", "
                + "values=" + allValues() + ", "
                + "changing=" + changing + ", "
                + "sensitive=" + sensitive + ']';
    }
}
