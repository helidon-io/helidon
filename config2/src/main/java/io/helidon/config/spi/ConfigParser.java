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

import java.util.Set;

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
    ObjectNode parse(Content.ParsableContent content) throws ConfigParserException;
}
