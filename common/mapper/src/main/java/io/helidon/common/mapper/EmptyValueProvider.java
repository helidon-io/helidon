/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.common.mapper;

import java.util.List;

import io.helidon.common.GenericType;

@SuppressWarnings({"unchecked", "rawtypes"})
class EmptyValueProvider implements ValueProvider {

    private final Value value;

    EmptyValueProvider(String name) {
        this.value = Value.empty(name);
    }

    @Override
    public String name() {
        return value.name();
    }

    @Override
    public <T> Value<T> as(Class<T> type) {
        return (Value<T>) value;
    }

    @Override
    public <T> Value<T> as(GenericType<T> genericType) {
        return (Value<T>) value;
    }

    @Override
    public <T> Value<List<T>> asList(Class<T> elementType) {
        return (Value<List<T>>) value;
    }
}
