/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.config.ConfigParsers;
import io.helidon.config.spi.ConfigNode.ObjectNode;

/**
 * Transforms config {@link ConfigContent} into a {@link ConfigNode.ObjectNode} that
 * represents the original structure and values from the content.
 * <p>
 * The application can register parsers on a {@code Builder} using the
 * {@link io.helidon.config.Config.Builder#addParser(ConfigParser)} method. The
 * config system also locates parsers using the Java
 * {@link java.util.ServiceLoader} mechanism and automatically adds them to
 * every {@code Builder} unless the application disables this feature for a
 * given {@code Builder} by invoking
 * {@link io.helidon.config.Config.Builder#disableParserServices()}.
 * <p>
 * A parser can specify a {@link javax.annotation.Priority}. If no priority is
 * explicitly assigned, the value of {@value PRIORITY} is assumed.
 *
 * @see io.helidon.config.Config.Builder#addParser(ConfigParser)
 * @see ConfigSource#load()
 * @see AbstractParsableConfigSource
 * @see ConfigParsers ConfigParsers - access built-in implementations.
 */
public interface ConfigParser {

    /**
     * Default priority of the parser if registered by {@link io.helidon.config.Config.Builder} automatically.
     */
    int PRIORITY = 100;

    /**
     * Returns set of supported media types by the parser.
     * <p>
     * Set of supported media types is used while {@link ConfigContext#findParser(String) looking for appropriate parser}
     * by {@link ConfigSource} implementations.
     * <p>
     * {@link ConfigSource} implementations usually use {@link java.nio.file.Files#probeContentType(Path)} method
     * to guess source media type, if not explicitly set.
     *
     * @return supported media types by the parser
     */
    Set<String> supportedMediaTypes();

    /**
     * Parses a specified {@link ConfigContent} into a {@link ObjectNode hierarchical configuration representation}.
     * <p>
     * Never returns {@code null}.
     *
     * @param content a content to be parsed
     * @return parsed hierarchical configuration representation
     * @throws ConfigParserException in case of problem to parse configuration from the source
     */
    ObjectNode parse(Content content) throws ConfigParserException;

    /**
     * Config content to be parsed by a {@link ConfigParser}.
     */
    interface Content extends ConfigContent {
        /**
         * Media type of the content. This method is only called if
         * there is no parser configured.
         *
         * @return content media type if known, {@code empty} otherwise
         */
        Optional<String> mediaType();

        /**
         * Data of this config source.
         *
         * @return the data of the underlying source to be parsed by a {@link ConfigParser}
         */
        InputStream data();

        /**
         * Charset configured by the config source or {@code UTF-8} if none configured.
         *
         * @return charset to use when reading {@link #data()} if needed by the parser
         */
        Charset charset();

        /**
         * A fluent API builder for {@link io.helidon.config.spi.ConfigParser.Content}.
         *
         * @return a new builder instance
         */
        static Builder builder() {
            return new Builder();
        }

        /**
         * Create content from data, media type and a stamp.
         * If not all are available, construct content using {@link #builder()}
         *
         * @param data input stream to underlying data
         * @param mediaType content media type
         * @param stamp stamp of the content
         * @return content built from provided information
         */
        static Content create(InputStream data, String mediaType, Object stamp) {
            return builder().data(data)
                    .mediaType(mediaType)
                    .stamp(stamp)
                    .build();
        }

        /**
         * Fluent API builder for {@link Content}.
         */
        class Builder extends ConfigContent.Builder<Builder> implements io.helidon.common.Builder<Content> {
            private InputStream data;
            private String mediaType;
            private Charset charset = StandardCharsets.UTF_8;

            private Builder() {
            }

            /**
             * Data of the config source as loaded from underlying storage.
             *
             * @param data to be parsed
             * @return updated builder instance
             */
            public Builder data(InputStream data) {
                Objects.requireNonNull(data, "Parsable input stream must be provided");
                this.data = data;
                return this;
            }

            /**
             * Media type of the content if known by the config source.
             * Media type is configured on content, as sometimes you need the actual file to exist to be able to
             * "guess" its media type, and this is the place we are sure it exists.
             *
             * @param mediaType media type of the content as understood by the config source
             * @return updated builder instance
             */
            public Builder mediaType(String mediaType) {
                Objects.requireNonNull(mediaType, "Media type must be provided, or this method should not be called");
                this.mediaType = mediaType;
                return this;
            }

            /**
             * A shortcut method to invoke with result of {@link io.helidon.common.media.type.MediaTypes#detectType(String)}
             *  and similar methods. Only sets media type if the parameter is present.
             *
             * @param mediaType optional of media type
             * @return updated builder instance
             */
            public Builder mediaType(Optional<String> mediaType) {
                mediaType.ifPresent(this::mediaType);
                return this;
            }

            /**
             * Configure charset if known by the config source.
             *
             * @param charset charset to use if the content should be read using a reader
             * @return updated builder instance
             */
            public Builder charset(Charset charset) {
                Objects.requireNonNull(charset, "Charset must be provided, or this method should not be called");
                this.charset = charset;
                return this;
            }

            InputStream data() {
                return data;
            }

            String mediaType() {
                return mediaType;
            }

            Charset charset() {
                return charset;
            }

            @Override
            public Content build() {
                if (null == data) {
                    throw new ConfigParserException("Parsable content exists, yet input stream was not configured.");
                }
                return new ContentImpl.ParsableContentImpl(this);
            }
        }
    }
}
