/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.common.uri;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperManager;
import io.helidon.common.mapper.OptionalValue;
import io.helidon.common.mapper.Value;

final class UriQueryEmpty implements UriQuery {
    static final UriQuery INSTANCE = new UriQueryEmpty();

    @Override
    public String rawValue() {
        return "";
    }

    @Override
    public String value() {
        return "";
    }

    @Override
    public String getRaw(String name) throws NoSuchElementException {
        throw new NoSuchElementException("Empty query");
    }

    @Override
    public List<String> getAllRaw(String name) {
        throw new NoSuchElementException("Empty query");
    }

    @Override
    public List<String> all(String name) {
        throw new NoSuchElementException("Empty query");
    }

    @Override
    public List<Value<String>> allValues(String name) throws NoSuchElementException {
        throw new NoSuchElementException("Empty query");
    }

    @Override
    public String get(String name) {
        throw new NoSuchElementException("Empty query");
    }

    @Override
    public OptionalValue<String> first(String name) {
        return OptionalValue.create(MapperManager.global(), name, GenericType.STRING, "uri", "query");
    }

    @Override
    public boolean contains(String name) {
        return false;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public Set<String> names() {
        return Set.of();
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public String component() {
        return "uri-query";
    }
}
