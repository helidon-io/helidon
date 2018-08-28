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

package io.helidon.config.spi;

import java.nio.file.Path;
import java.time.Instant;
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
    Set<String> getSupportedMediaTypes();

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
        default Optional<S> getStamp() {
            return Optional.empty();
        }

        /**
         * Returns configuration content media type.
         *
         * @return content media type
         */
        String getMediaType();

        /**
         * Returns a {@link Readable} that is use to read configuration content from.
         *
         * @param <T> return type that is {@link Readable} as well as {@link AutoCloseable}
         * @return a content as {@link Readable}
         */
        <T extends Readable & AutoCloseable> T asReadable();

        /**
         * Creates {@link Content} from given {@link Readable readable content} and
         * with specified {@code mediaType} of configuration format.
         *
         * @param readable  a readable providing configuration.
         *                  If it implements {@link AutoCloseable} it is automatically closed whenever parsed.
         * @param mediaType a configuration mediaType
         * @param stamp     content stamp
         * @param <S>       a type of data stamp
         * @return a config content
         */
        static <S> Content<S> from(Readable readable, String mediaType, Optional<S> stamp) {
            return new Content<S>() {
                @Override
                public void close() throws ConfigException {
                    if (readable instanceof AutoCloseable) {
                        try {
                            ((AutoCloseable) readable).close();
                        } catch (ConfigException ex) {
                            throw ex;
                        } catch (Exception ex) {
                            throw new ConfigException("Error while closing readable [" + readable + "].", ex);
                        }
                    }
                }

                @Override
                public <T extends Readable & AutoCloseable> T asReadable() {
                    return (T) readable;
                }

                @Override
                public String getMediaType() {
                    return mediaType;
                }

                @Override
                public Optional<S> getStamp() {
                    return stamp;
                }
            };

        }
    }
}
