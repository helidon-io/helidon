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
 *
 *
 */

package io.helidon.config;

import java.util.Optional;

import io.helidon.config.spi.ConfigSource;

public class ConfigSources {
    /**
     * Provides an empty config source.
     * @return empty config source
     */
    public static ConfigSource empty() {
        return EmptyConfigSourceHolder.EMPTY;
    }

    /**
     * Holder of singleton instance of {@link ConfigSource}.
     *
     * @see ConfigSources#empty()
     */
    static final class EmptyConfigSourceHolder {

        private EmptyConfigSourceHolder() {
            throw new AssertionError("Instantiation not allowed.");
        }

        /**
         * EMPTY singleton instance.
         */
        static final ConfigSource EMPTY = new EmptyConfigSource();
    }

    private final static class EmptyConfigSource implements EagerSource {

        @Override
        public Optional<ConfigNode.ObjectNode> load() throws ConfigException {
            return Optional.empty();
        }
    }
}
