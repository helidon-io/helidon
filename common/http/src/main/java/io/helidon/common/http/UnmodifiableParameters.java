/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.common.http;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Immutable delegate for {@link Parameters}.
 */
class UnmodifiableParameters implements Parameters {

    private final Parameters parameters;

    /**
     * Creates new instance.
     *
     * @param parameters To delegate on.
     */
    UnmodifiableParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    @Override
    public Optional<String> first(String name) {
        return parameters.first(name);
    }

    @Override
    public List<String> all(String name) {
        return parameters.all(name);
    }

    @Override
    public Map<String, List<String>> toMap() {
        return parameters.toMap();
    }

    @Override
    public List<String> computeSingleIfAbsent(String key, Function<String, String> value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> put(String key, String... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> put(String key, Iterable<String> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> putIfAbsent(String key, String... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> putIfAbsent(String key, Iterable<String> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> computeIfAbsent(String key, Function<String, Iterable<String>> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UnmodifiableParameters putAll(Parameters parameters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UnmodifiableParameters add(String key, String... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UnmodifiableParameters add(String key, Iterable<String> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UnmodifiableParameters addAll(Parameters parameters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> remove(String key) {
        throw new UnsupportedOperationException();
    }

}
