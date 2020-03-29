/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.config.spi;

import java.util.List;

import io.helidon.config.Config;

/**
 * Java service loader service to provide a config source based on meta configuration.
 */
public interface ConfigSourceProvider extends MetaConfigurableProvider<ConfigSource> {
    /**
     * Create a list of configuration sources from a single configuration.
     * <p>
     * This method is called (only) when the meta configuration property {@code multi-source}
     *  is set to {@code true}.
     * <p>
     * Example: for classpath config source, we may want to read all instances of the resource
     * on classpath.
     *
     * @param type type of the config source
     * @param metaConfig meta configuration of the config source
     * @return a list of config sources, at least one MUST be returned, so we can correctly validate
     * optional/mandatory sources.
     */
    default List<ConfigSource> createMulti(String type, Config metaConfig) {
        return List.of();
    }
}
