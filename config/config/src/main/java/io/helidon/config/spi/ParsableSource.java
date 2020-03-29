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

package io.helidon.config.spi;

import java.util.Optional;

import io.helidon.config.ConfigException;

/**
 * An eager source that can read all data from the underlying origin as a stream that can be
 * parsed based on its media type (or using an explicit {@link io.helidon.config.spi.ConfigParser}.
 */
public interface ParsableSource extends Source {
    /**
     * Loads the underlying source data. This method is only called when the source {@link #exists()}.
     * <p>
     * The method can be invoked repeatedly, for example during retries.
     * In case the underlying data is gone or does not exist, return an empty optional.
     *
     * @return An instance of {@code T} as read from the underlying origin of the data (if it exists)
     * @throws io.helidon.config.ConfigException in case of errors loading from the underlying origin
     */
    Optional<ConfigParser.Content> load() throws ConfigException;

    /**
     * If a parser is configured with this source, return it.
     * The source implementation does not need to handle config parser.
     *
     * @return content parser if one is configured on this source
     */
    Optional<ConfigParser> parser();

    /**
     * If media type is configured on this source, or can be guessed from the underlying origin, return it.
     * The media type may be used to locate a {@link io.helidon.config.spi.ConfigParser} if one is not explicitly
     * configured.
     *
     * @return media type if configured or detected from content
     */
    Optional<String> mediaType();

    /**
     * A builder for a parsable source.
     *
     * @param <B> type of the builder, used when extending this builder ({@code MyBuilder implements Builder<MyBuilder>}
     * @see io.helidon.config.AbstractConfigSourceBuilder
     * @see io.helidon.config.AbstractConfigSource
     */
    interface Builder<B extends Builder<B>> extends ConfigSource.Builder<B> {
        /**
         * Configure an explicit parser to be used with the source.
         *
         * @param parser parser to use
         * @return updated builder instance
         */
        B parser(ConfigParser parser);

        /**
         * Configure an explicit media type to be used with this source.
         * This method is ignored if a parser was configured.
         *
         * @param mediaType media type to use
         * @return updated builder instance
         */
        B mediaType(String mediaType);
    }
}
