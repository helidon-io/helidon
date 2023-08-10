/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.util.Objects;

import io.helidon.common.mapper.MapperManager;
import io.helidon.http.Http.HeaderName;

abstract class HeaderValueBase implements Http.HeaderValueWriteable {
    private static final MapperManager MAPPER_MANAGER = MapperManager.create();

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
    public abstract Http.HeaderValueWriteable addValue(String value);

    @Override
    public String name() {
        return actualName;
    }

    @Override
    public HeaderName headerName() {
        return name;
    }

    @Override
    public String value() {
        return firstValue;
    }

    @Override
    public <T> T value(Class<T> type) {
        return MAPPER_MANAGER.map(value(), String.class, type, "http-header");
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
        return changing == that.changing
                && sensitive == that.sensitive
                && actualName.equals(that.actualName)
                && valueCount() == that.valueCount()
                && allValues().equals(that.allValues());
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
