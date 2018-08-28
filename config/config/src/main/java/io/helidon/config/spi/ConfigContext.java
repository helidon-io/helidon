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

import java.util.Optional;

/**
 * Context created by a {@link io.helidon.config.Config.Builder} as it constructs a
 * {@link io.helidon.config.Config}.
 * <p>
 * The context is typically used in implementations of {@link io.helidon.config.spi}
 * interfaces to share common information.
 */
public interface ConfigContext {

    /**
     * Returns the first appropriate {@link ConfigParser} instance that supports
     * the specified
     * {@link ConfigParser.Content#getMediaType() content media type}.
     * <p>
     * Note that the application can explicitly register parsers with a builder
     * by invoking the
     * {@link io.helidon.config.Config.Builder#addParser(ConfigParser)} method. The
     * config system also loads parsers using the Java
     * {@link java.util.ServiceLoader} mechanism and automatically registers
     * such loaded parsers with each {@code Builder} unless the application has
     * invoked the {@link io.helidon.config.Config.Builder#disableParserServices()}
     * method.
     *
     * @param mediaType a media type for which a parser is needed
     * @return {@code Optional<ConfigParser>} ({@link Optional#empty()} if no
     * appropriate parser exists)
     */
    Optional<ConfigParser> findParser(String mediaType);

}
