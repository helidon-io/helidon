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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.config.ConfigException;
import io.helidon.config.ConfigNode.ObjectNode;

/**
 * Transforms config {@link Content} into a {@link io.helidon.config.spi.ConfigNode.ObjectNode} that
 * represents the original structure and values from the content.
 * <p>
 * The application can register parsers on a {@code Builder} using the
 * {@link io.helidon.config.Config.Builder#addParser(io.helidon.config.spi.ConfigParser)} method. The
 * config system also locates parsers using the Java
 * {@link java.util.ServiceLoader} mechanism and automatically adds them to
 * every {@code Builder} unless the application disables this feature for a
 * given {@code Builder} by invoking
 * {@link io.helidon.config.Config.Builder#disableParserServices()}.
 * <p>
 * A parser can specify a {@link javax.annotation.Priority}. If no priority is
 * explicitly assigned, the value of {@value PRIORITY} is assumed.
 *
 * @see io.helidon.config.Config.Builder#addParser(io.helidon.config.spi.ConfigParser)
 * @see io.helidon.config.spi.ConfigSource#load()
 * @see io.helidon.config.spi.AbstractParsableConfigSource
 * @see ConfigParsers ConfigParsers - access built-in implementations.
 */
public interface ConfigParser {
    /**
     * Returns set of supported media types by the parser.
     * <p>
     * Set of supported media types is used while {@link io.helidon.config.spi.ConfigContext#findParser(String) looking for
     * appropriate parser}
     * by {@link io.helidon.config.spi.ConfigSource} implementations.
     * <p>
     * {@link io.helidon.config.spi.ConfigSource} implementations usually use
     * {@link java.nio.file.Files#probeContentType(java.nio.file.Path)} method
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
     * @return parsed hierarchical configuration representation
     * @throws io.helidon.config.spi.ConfigParserException in case of problem to parse configuration from the source
     */
    ObjectNode parse(Content content) throws ConfigParserException;

    /**
     * {@link io.helidon.config.spi.ConfigSource} configuration Content to be
     * {@link io.helidon.config.spi.ConfigParser#parse(Content) parsed} into
     * {@link ObjectNode hierarchical configuration representation}.
     */
    interface Content {

        default void close() throws ConfigException {
        }

        /**
         * Media type of the content. This method is only called if
         * the source {@link ConfigSource#exists()} and the {@link ConfigSource.ParsableSource#parser}
         * is empty.
         * @return content media type if known, {@code empty} otherwise
         */
        Optional<String> mediaType();

        Optional<ConfigParser> parser();

        /**
         * Returns an {@link java.io.InputStream} that is used to read configuration content from.
         *
         * @return a content as {@link Readable}
         */
        InputStream data();

        ObjectNode node();

        <T> Optional<T> stamp();

        <T> Optional<T> target();

        /**
         * Create a fluent API builder for content.
         *
         * @return a new fluent API builder for content
         */
        static Builder builder() {
            return new Builder();
        }

        boolean exists();

        /**
         * Fluent API builder for {@link io.helidon.config.spi.ConfigParser.Content}.
         */
        class Builder implements io.helidon.common.Builder<Content> {
            private boolean exists = true;

            // parsable config source data
            private InputStream data;
            private String mediaType;
            private ConfigParser parser;

            // polling related information
            private Object stamp;
            private Object target;

            // node based config source data
            private ObjectNode rootNode;

            private Builder() {
            }

            @Override
            public Content build() {
                final Optional<String> mediaType = Optional.ofNullable(this.mediaType);
                final Optional<ConfigParser> finalParser = Optional.ofNullable(parser);

                return new Content() {
                    @Override
                    public Optional<String> mediaType() {
                        return mediaType;
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public InputStream data() {
                        return data;
                    }

                    @Override
                    public void close() throws ConfigException {
                        try {
                            data.close();
                        } catch (ConfigException ex) {
                            throw ex;
                        } catch (Exception ex) {
                            throw new ConfigException("Error while closing readable [" + data + "].", ex);
                        }
                    }

                    @Override
                    public Optional<ConfigParser> parser() {
                        return finalParser;
                    }

                    @Override
                    public boolean exists() {
                        return exists;
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> Optional<T> stamp() {
                        return Optional.ofNullable((T)stamp);
                    }


                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> Optional<T> target() {
                        return Optional.ofNullable((T)target);
                    }

                    @Override
                    public ObjectNode node() {
                        return rootNode;
                    }
                };
            }

            public Builder exists(boolean exists) {
                this.exists = exists;
                return this;
            }

            public Builder parsable(ParsableContentBuilder parsableContent) {
                parsableContent.validate();

                this.data = parsableContent.data();
                this.mediaType = parsableContent.mediaType();
                this.parser = parsableContent.parser();

                return this;
            }

            public Builder pollingStamp(Object stamp) {
                this.stamp = stamp;

                return this;
            }

            public Builder pollingTarget(Object target) {
                this.target = target;

                return this;
            }

            public Builder node(ObjectNode rootNode) {
                this.rootNode = rootNode;

                return this;
            }
        }
    }

    class ParsableContentBuilder {
        private InputStream data;
        private String dataDescription;
        private ConfigParser explicitParser;
        private String mediaType;

        private ParsableContentBuilder(InputStream data, String dataDescription) {
            Objects.requireNonNull(data, "Parsable input stream must be provided");
            Objects.requireNonNull(dataDescription, "Parsable stream description must be provided");

            this.data = data;
            this.dataDescription = dataDescription;
        }

        public static ParsableContentBuilder create(InputStream data, String dataDescription) {
            return new ParsableContentBuilder(data, dataDescription);
        }

        public ParsableContentBuilder mediaType(String mediaType) {
            Objects.requireNonNull(mediaType, "Media type must be provided, or this method should not be called");
            this.mediaType = mediaType;
            return this;
        }

        public ParsableContentBuilder parser(ConfigParser parser) {
            Objects.requireNonNull(mediaType, "Parser must be provided, or this method should not be called");
            this.explicitParser = parser;
            return this;
        }

        void validate() {
            if (null == explicitParser && null == mediaType) {
                throw new ConfigException("Config source based on " + dataDescription + " does not have an explicit parser, "
                                                  + "and media type was not configured.");
            }
        }

        InputStream data() {
            return data;
        }

        ConfigParser parser() {
            return explicitParser;
        }

        String mediaType() {
            return mediaType;
        }
    }
}
