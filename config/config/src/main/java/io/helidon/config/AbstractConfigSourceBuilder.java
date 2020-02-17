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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.Source;

public abstract class AbstractConfigSourceBuilder<B extends AbstractConfigSourceBuilder<B, U>, U>
        extends AbstractSourceBuilder<B, U>
        implements Source.Builder<B> {

    private ConfigParser parser;
    private String mediaType;
    private Function<Config.Key, Optional<String>> mediaTypeMapping;
    private Function<Config.Key, Optional<ConfigParser>> parserMapping;

    @SuppressWarnings("unchecked")
    private B me = (B) this;

    protected B config(Config metaConfig) {
        super.config(metaConfig);

        metaConfig.get("media-type").asString().ifPresent(this::mediaType);
        metaConfig.get("media-type-mapping").detach().asMap()
                .ifPresent(this::mediaTypeMappingConfig);
        return me;
    }

    private void mediaTypeMappingConfig(Map<String, String> mappingMap) {
        mediaTypeMapping(key -> Optional.ofNullable(mappingMap.get(key.toString())));
    }

    /**
     * Sets a function that maps keys to media type.
     * This supports parsing of values using a {@link io.helidon.config.spi.ConfigParser} to expand an inlined
     * configuration.
     *
     * @param mediaTypeMapping a mapping function
     * @return a modified builder
     */
    public B mediaTypeMapping(Function<Config.Key, Optional<String>> mediaTypeMapping) {
        Objects.requireNonNull(mediaTypeMapping, "mediaTypeMapping cannot be null");

        this.mediaTypeMapping = mediaTypeMapping;
        return me;
    }

    /**
     * Sets a function that maps keys to a parser.
     * This supports parsing of specific values using a custom parser to expand an inlined configuration.
     *
     * @param parserMapping a mapping function
     * @return a modified builder
     */
    public B parserMapping(Function<Config.Key, Optional<ConfigParser>> parserMapping) {
        Objects.requireNonNull(parserMapping, "parserMapping cannot be null");

        this.parserMapping = parserMapping;
        return me;
    }


    protected B parser(ConfigParser parser) {
        this.parser = parser;
        return me;
    }

    Optional<Function<Config.Key, Optional<String>>> mediaTypeMapping() {
        return Optional.ofNullable(mediaTypeMapping);
    }

    /**
     * Parser mapping function.
     * @return parser mapping
     */
    Optional<Function<Config.Key, Optional<ConfigParser>>> parserMapping() {
        return Optional.ofNullable(parserMapping);
    }

    protected B mediaType(String mediaType) {
        this.mediaType = mediaType;
        return me;
    }

    Optional<ConfigParser> parser() {
        return Optional.ofNullable(parser);
    }

    Optional<String> mediaType() {
        return Optional.ofNullable(mediaType);
    }
}
