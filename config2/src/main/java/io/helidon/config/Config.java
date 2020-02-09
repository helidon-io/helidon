/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.helidon.config.spi.ConfigSource;

public interface Config {
    static Config create(Collection<? extends ConfigSource> configSources) {
        return builder()
                .sources(configSources)
                .build();
    }

    static Builder builder() {
        return new Builder();
    }

    Config get(Key key);
    Config get(String key);

    Optional<String> getValue();

    class Builder implements io.helidon.common.Builder<Config> {
        private List<ConfigSource> sources = new LinkedList<>();

        @Override
        public Config build() {
            return new ConfigRootImpl(this);
        }

        public Builder sources(Collection<? extends ConfigSource> configSources) {
            this.sources.addAll(configSources);
            return this;
        }

        List<ConfigSource> sources() {
            return sources;
        }
    }

    /**
     * Configuration node types.
     */
    enum Type {
        /**
         * Config node is an object of named members
         * ({@link #VALUE values}, {@link #LIST lists} or other {@link #OBJECT objects}).
         */
        OBJECT(true, false),
        /**
         * Config node is a list of indexed elements
         * ({@link #VALUE values}, {@link #OBJECT objects} or other {@link #LIST lists}).
         */
        LIST(true, false),
        /**
         * Config node is a leaf {@code String}-based single value,
         * member of {@link #OBJECT object} or {@link #LIST list} element.
         */
        VALUE(true, true),
        /**
         * Config node does not exists.
         */
        MISSING(false, false);

        private boolean exists;
        private boolean isLeaf;

        Type(boolean exists, boolean isLeaf) {
            this.exists = exists;
            this.isLeaf = isLeaf;
        }

        /**
         * Returns {@code true} if the node exists, either as an object, a list or as a value node.
         *
         * @return {@code true} if the node exists
         */
        public boolean exists() {
            return exists;
        }

        /**
         * Returns {@code true} if this configuration node is existing a value node.
         * <p>
         * Leaf configuration node does not contain any nested configuration sub-trees,
         * but only a single associated value.
         *
         * @return {@code true} if the node is existing leaf node, {@code false} otherwise.
         */
        public boolean isLeaf() {
            return isLeaf;
        }
    }
}
