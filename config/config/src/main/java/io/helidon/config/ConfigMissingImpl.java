/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.helidon.config.internal.ConfigKeyImpl;

/**
 * Implementation of {@link Config} that represents {@link Config.Type#MISSING missing} node.
 */
class ConfigMissingImpl extends AbstractConfigImpl {

    ConfigMissingImpl(ConfigKeyImpl prefix,
                      ConfigKeyImpl key,
                      ConfigFactory factory) {
        super(Type.MISSING, prefix, key, factory);
    }

    @Override
    public boolean hasValue() {
        return false;
    }

    @Override
    public Optional<String> value() {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> asOptional(Class<? extends T> type) throws ConfigMappingException {
        return Optional.empty();
    }

    @Override
    public Optional<List<Config>> nodeList() throws ConfigMappingException {
        return Optional.empty();
    }

    @Override
    public <T> Optional<List<T>> asOptionalList(Class<? extends T> type) throws ConfigMappingException {
        return Optional.empty();
    }

    @Override
    public Optional<Map<String, String>> asOptionalMap() {
        return Optional.empty();
    }

    @Override
    public Stream<Config> traverse(Predicate<Config> predicate) {
        return Stream.empty();
    }

    @Override
    public String toString() {
        return "[" + realKey() + "] MISSING";
    }

}
