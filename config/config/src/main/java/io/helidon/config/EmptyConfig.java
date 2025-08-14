/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.helidon.common.GenericType;
import io.helidon.config.spi.ConfigMapper;

final class EmptyConfig {
    static final Config.Key ROOT_KEY = new KeyImpl(null, "");
    static final Config EMPTY = new EmptyNode(ROOT_KEY);

    private EmptyConfig() {
    }

    @SuppressWarnings("removal")
    private static final class KeyImpl implements Config.Key {
        private final Config.Key parent;
        private final String name;

        private KeyImpl(Config.Key parent, String name) {
            this.parent = parent;
            this.name = name;
        }

        @Override
        public Config.Key parent() throws ConfigException {
            if (isRoot()) {
                throw new IllegalStateException("Attempting to get parent of a root node. Guard by isRoot instead");
            }

            return parent;
        }

        @Override
        public boolean isRoot() {
            return parent == null;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Config.Key child(io.helidon.common.config.Config.Key key) {
            if (isRoot()) {
                return new ConfigProvider.CommonKeyWrapper(key);
            }
            List<String> path = path(new ConfigProvider.CommonKeyWrapper(key));
            Config.Key node = this;
            for (String name : path) {
                node = new KeyImpl(node, name);
            }
            // the last one is the correct one
            return node;
        }

        @Override
        public int compareTo(io.helidon.common.config.Config.Key o) {
            return this.toString().compareTo(o.toString());
        }

        @Override
        public String toString() {
            return String.join(".", path(this));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            KeyImpl key = (KeyImpl) o;
            return Objects.equals(parent, key.parent) && Objects.equals(name, key.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parent, name);
        }

        private static List<String> path(Config.Key key) {
            List<String> names = new ArrayList<>();
            Config.Key node = key;
            while (!node.isRoot()) {
                names.add(node.name());
                node = node.parent();
            }
            // names are now from lowest to highest, inverse
            Collections.reverse(names);
            return names;
        }
    }

    @SuppressWarnings("removal")
    private static final class EmptyNode implements Config {
        private static final Config DELEGATE = new BuilderImpl()
                // the empty config source is needed, so we do not look for meta config or default
                // config sources
                .sources(ConfigSources.empty())
                .overrides(OverrideSources.empty())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableParserServices()
                .disableFilterServices()
                .changesExecutor(command -> {})
                .build();

        private final Key key;

        private EmptyNode(Key key) {
            this.key = key;
        }

        @Override
        public Key key() {
            return key;
        }

        @Override
        public Config root() {
            // empty config can just return the root empty config
            return EMPTY;
        }

        @Override
        public Config get(Key key) throws ConfigException {
            return new EmptyNode(this.key.child(key));
        }

        @Override
        public Config get(String key) throws ConfigException {
            String[] split = key.split("\\.");
            Key node = this.key;

            for (String s : split) {
                node = new KeyImpl(node, s);
            }

            return new EmptyNode(node);
        }

        @Override
        public Config detach() throws ConfigException {
            return EMPTY;
        }

        @Override
        public boolean exists() {
            return false;
        }

        @Override
        public boolean isLeaf() {
            return false;
        }

        @Override
        public boolean isObject() {
            return false;
        }

        @Override
        public boolean isList() {
            return false;
        }

        @Override
        public boolean hasValue() {
            return false;
        }

        @Override
        public <T> ConfigValue<T> as(Class<T> type) {
            return ConfigValues.empty();
        }

        @Override
        public <T> ConfigValue<T> map(Function<io.helidon.common.config.Config, T> mapper) {
            return ConfigValues.empty();
        }

        @Override
        public <T> ConfigValue<List<T>> asList(Class<T> type) throws
                ConfigException {
            return ConfigValues.empty();
        }

        @Override
        public <T> ConfigValue<List<T>> mapList(Function<io.helidon.common.config.Config, T> mapper) throws
                ConfigException {
            return ConfigValues.empty();
        }

        @Override
        public ConfigValue<List<Config>> asNodeList() throws
                ConfigException {
            return ConfigValues.empty();
        }

        @Override
        public ConfigValue<Map<String, String>> asMap() throws ConfigException {
            return ConfigValues.empty();
        }

        @Override
        public Instant timestamp() {
            return DELEGATE.timestamp();
        }

        @Override
        public Type type() {
            return Type.MISSING;
        }

        @Override
        public Stream<Config> traverse(Predicate<Config> predicate) {
            return Stream.empty();
        }

        @Override
        public <T> T convert(Class<T> type, String value) throws ConfigMappingException {
            return DELEGATE.convert(type, value);
        }

        @Override
        public ConfigMapper mapper() {
            return DELEGATE.mapper();
        }

        @Override
        public <T> ConfigValue<T> as(GenericType<T> genericType) {
            return ConfigValues.empty();
        }

        @Override
        public <T> ConfigValue<T> as(Function<Config, T> mapper) {
            return ConfigValues.empty();
        }

        @Override
        public <T> ConfigValue<List<T>> asList(Function<Config, T> mapper) throws ConfigMappingException {
            return ConfigValues.empty();
        }
    }
}
