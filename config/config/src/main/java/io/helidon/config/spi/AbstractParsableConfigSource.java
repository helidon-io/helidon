/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.spi.ConfigNode.ObjectNode;

/**
 * Abstract implementation of {@link ConfigSource} that uses a
 * {@link ConfigParser} to parse
 * {@link ConfigParser.Content configuration content} accessible as a
 * {@link ConfigParser.Content#asReadable() Readable}.
 * <p>
 * Typically concrete implementations will extend this class in order to
 * delegate to {@link ConfigParser} the loading of configuration content into an
 * {@link ObjectNode} representing the hierarchical structure of the configuration.
 *
 * @param <S> a type of data stamp
 * @see Builder
 */
public abstract class AbstractParsableConfigSource<S> extends AbstractConfigSource<S> {

    private final String mediaType;
    private final ConfigParser parser;

    /**
     * Initializes config source from builder.
     *
     * @param builder builder to be initialized from
     */
    protected AbstractParsableConfigSource(AbstractParsableConfigSource.Builder builder) {
        super(builder);

        mediaType = builder.mediaType();
        parser = builder.parser();
    }

    @Override
    protected Data<ObjectNode, S> loadData() {
        ConfigParser.Content<S> content = content();
        ObjectNode objectNode = parse(configContext(), content);
        return new Data<>(Optional.of(objectNode), content.stamp());
    }

    /**
     * Returns source associated media type or {@code null} if unknown.
     *
     * @return source associated media type or {@code null} if unknown.
     */
    protected String mediaType() {
        return mediaType;
    }

    /**
     * Returns source associated parser or {@code null} if unknown.
     *
     * @return source associated parser or {@code null} if unknown.
     */
    protected ConfigParser parser() {
        return parser;
    }

    /**
     * Returns config source content.
     *
     * @return config source content. Never returns {@code null}.
     * @throws ConfigException in case of loading of configuration from config source failed.
     */
    protected abstract ConfigParser.Content<S> content() throws ConfigException;

    /**
     * Parser config source content into internal config structure.
     *
     * @param context config context built by {@link io.helidon.config.Config.Builder}
     * @param content content to be parsed
     * @return parsed configuration into internal structure. Never returns {@code null}.
     * @throws ConfigParserException in case of problem to parse configuration from the source
     */
    private ObjectNode parse(ConfigContext context, ConfigParser.Content<S> content) throws ConfigParserException {
        return Optional.ofNullable(parser())
                .or(() -> context.findParser(Optional.ofNullable(content.mediaType())
                                                     .orElseThrow(() -> new ConfigException("Unknown media type."))))
                .map(parser -> parser.parse(content))
                .orElseThrow(() -> new ConfigException("Cannot find suitable parser for '"
                                                               + content.mediaType() + "' media type."));
    }

    /**
     * Common {@link AbstractParsableConfigSource} Builder, suitable for
     * concrete implementations of Builder that are related to
     * {@code ConfigSource}s which extend {@link AbstractParsableConfigSource}
     * <p>
     * The application can control the following behavior:
     * <ul>
     * <li>{@code mandatory} - whether the configuration source must exist (default: {@code true})
     * <li>{@code media-type} - configuration content media type to be used to look for appropriate {@link ConfigParser};</li>
     * <li>{@code parser} - the {@link ConfigParser} to be used to parse the source</li>
     * <li>changes {@code executor} and subscriber's {@code buffer size} - behavior related to
     * {@link AbstractParsableConfigSource#changes()} support</li>
     * </ul>
     * <p>
     * If the {@link ConfigSource} is {@code mandatory} and a source does not exist
     * then {@link ConfigSource#load} throws a {@link ConfigException}.
     * <p>
     * If the application does not explicit set {@code media-type} the
     * {@code Builder} tries to infer it from the source, for example from the
     * source URI.
     *
     * @param <B> type of Builder implementation
     * @param <T> type of key source attributes (target) used to construct polling strategy from
     * @param <S> type of the config source to be built
     */
    public abstract static class Builder<B extends Builder<B, T, S>, T, S extends AbstractMpSource<?>>
            extends AbstractConfigSource.Builder<B, T, S> {
        private static final String MEDIA_TYPE_KEY = "media-type";
        private String mediaType;
        private ConfigParser parser;

        /**
         * Initialize builder.
         *
         * @param targetType target type
         */
        protected Builder(Class<T> targetType) {
            super(targetType);
        }

        /**
         * {@inheritDoc}
         * <ul>
         * <li>{@code media-type} - type {@code String}, see {@link #mediaType(String)}</li>
         * </ul>
         *
         * @param metaConfig configuration properties used to configure a builder instance.
         * @return modified builder instance
         */
        @Override
        public B config(Config metaConfig) {
            //media-type
            metaConfig.get(MEDIA_TYPE_KEY)
                    .asString()
                    .ifPresent(this::mediaType);

            return super.config(metaConfig);
        }

        /**
         * Sets configuration content media type.
         *
         * @param mediaType a configuration content media type
         * @return modified builder instance
         */
        public B mediaType(String mediaType) {
            this.mediaType = mediaType;

            return thisBuilder();
        }

        /**
         * Sets a {@link ConfigParser} instance to be used to parse configuration content.
         * <p>
         * If the parser is set, the {@link #mediaType(String) media type} property is ignored.
         *
         * @param parser parsed used to parse configuration content
         * @return modified builder instance
         */
        public B parser(ConfigParser parser) {
            this.parser = parser;

            return thisBuilder();
        }

        /**
         * Returns media type property.
         *
         * @return media type property.
         */
        protected String mediaType() {
            return mediaType;
        }

        /**
         * Returns parser property.
         *
         * @return parser property.
         */
        protected ConfigParser parser() {
            return parser;
        }

    }

}
