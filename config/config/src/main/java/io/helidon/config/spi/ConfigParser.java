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

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.config.ConfigException;
import io.helidon.config.ConfigParsers;
import io.helidon.config.spi.ConfigNode.ObjectNode;

/**
 * Transforms config {@link Content} into a {@link ConfigNode.ObjectNode} that
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
     * Parses a specified {@link Content} into a {@link ObjectNode hierarchical configuration representation}.
     * <p>
     * Never returns {@code null}.
     *
     * @param content a content to be parsed
     * @param <S>     a type of data stamp
     * @return parsed hierarchical configuration representation
     * @throws ConfigParserException in case of problem to parse configuration from the source
     */
    <S> ObjectNode parse(Content<S> content) throws ConfigParserException;

    /**
     * {@link ConfigSource} configuration Content to be {@link ConfigParser#parse(Content) parsed} into
     * {@link ObjectNode hierarchical configuration representation}.
     *
     * @param <S> a type of data stamp
     */
    interface Content<S> {

        default void close() throws ConfigException {
        }

        /**
         * A modification stamp of the content.
         * <p>
         * Default implementation returns {@link Instant#EPOCH}.
         *
         * @return a stamp of the content
         */
        default Optional<S> stamp() {
            return Optional.empty();
        }

        /**
         * Returns configuration content media type.
         *
         * @return content media type if known, {@code empty} otherwise
         */
        Optional<String> mediaType();

        /**
         * Returns a {@link Readable} that is use to read configuration content from.
         *
         * @param <T> return type that is {@link Readable} as well as {@link AutoCloseable}
         * @return a content as {@link Readable}
         */
        <T extends Readable & AutoCloseable> T asReadable();

        /**
         * Create a fluent API builder for content.
         *
         * @param readable readable to base this content builder on
         * @param <S> type of the stamp to use
         * @param <T> dual type of readable and autocloseable parameter
         * @return a new fluent API builder for content
         */
        static <S, T extends Readable & AutoCloseable> Builder<S> builder(T readable) {
            Objects.requireNonNull(readable, "Readable must not be null when creating content");
            return new Builder<>(readable);
        }

        /**
         * Creates {@link Content} from given {@link Readable readable content} and
         * with specified {@code mediaType} of configuration format.
         *
         * @param readable  a readable providing configuration.
         * @param mediaType a configuration mediaType
         * @param stamp     content stamp
         * @param <S>       a type of data stamp
         * @param <T>       dual type of readable and autocloseable parameter
         * @return a config content
         */
        static <S, T extends Readable & AutoCloseable> Content<S> create(T readable, String mediaType, S stamp) {
            Objects.requireNonNull(mediaType, "Media type must not be null when creating content using Content.create()");
            Objects.requireNonNull(stamp, "Stamp must not be null when creating content using Content.create()");

            Builder<S> builder = builder(readable);

            return builder
                    .mediaType(mediaType)
                    .stamp(stamp)
                    .build();
        }

        /**
         * Fluent API builder for {@link io.helidon.config.spi.ConfigParser.Content}.
         *
         * @param <S> type of the stamp of the built content
         */
        class Builder<S> implements io.helidon.common.Builder<Content<S>> {
            private final AutoCloseable readable;
            private String mediaType;
            private S stamp;

            private <T extends Readable & AutoCloseable> Builder(T readable) {
                this.readable = readable;
            }

            @Override
            public Content<S> build() {
                final Optional<String> mediaType = Optional.ofNullable(this.mediaType);
                final Optional<S> stamp = Optional.ofNullable(this.stamp);

                return new Content<>() {
                    @Override
                    public Optional<String> mediaType() {
                        return mediaType;
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public <T extends Readable & AutoCloseable> T asReadable() {
                        return (T) readable;
                    }

                    @Override
                    public void close() throws ConfigException {
                        try {
                            readable.close();
                        } catch (ConfigException ex) {
                            throw ex;
                        } catch (Exception ex) {
                            throw new ConfigException("Error while closing readable [" + readable + "].", ex);
                        }
                    }

                    @Override
                    public Optional<S> stamp() {
                        return stamp;
                    }
                };
            }

            /**
             * Content media type.
             * @param mediaType type of the content
             * @return builder
             */
            public Builder<S> mediaType(String mediaType) {
                this.mediaType = mediaType;
                return this;
            }

            /**
             * Content stamp.
             *
             * @param stamp stamp of the content
             * @return builder
             */
            public Builder<S> stamp(S stamp) {
                this.stamp = stamp;
                return this;
            }
        }
    }
}
