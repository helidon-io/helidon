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

package io.helidon.config.yaml;

import io.helidon.config.spi.ConfigParser;
import io.helidon.config.yaml.internal.YamlConfigParser;

/**
 * YAML ConfigParser Builder.
 */
public final class YamlConfigParserBuilder {

    private YamlConfigParserBuilder() {
    }

    /**
     * Creates new instance of YAML ConfigParser with default behaviour,
     * i.e. with same behaviour as in case the parser is loaded automatically by {@link java.util.ServiceLoader}
     * (see {@link io.helidon.config.spi package description}).
     *
     * @return new instance YAML ConfigParser
     * @see io.helidon.config.spi
     */
    public static ConfigParser buildDefault() {
        return create().build();
    }

    /**
     * Creates new instance of Builder.
     *
     * @return new instance of Builder.
     */
    public static YamlConfigParserBuilder create() {
        return new YamlConfigParserBuilder();
    }

    /**
     * Builds new instance of YAML ConfigParser.
     *
     * @return new instance of YAML ConfigParser.
     */
    public ConfigParser build() {
        return new YamlConfigParser();
    }

}
