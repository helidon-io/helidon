/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.common.uri;

import java.util.List;
import java.util.NoSuchElementException;

import io.helidon.common.parameters.Parameters;

/**
 * HTTP Query representation.
 * Query is the section separated by {@code ?} character from the path, where each query parameter is separated
 * by {@code &} character.
 */
public interface UriQuery extends Parameters {
    /**
     * Create a new HTTP query from the query string.
     *
     * @param query raw query string
     * @return HTTP query instance
     */
    static UriQuery create(String query) {
        return new UriQueryImpl(query);
    }

    /**
     * Create an empty HTTP query.
     *
     * @return HTTP query instance
     */
    static UriQuery empty() {
        return UriQueryEmpty.INSTANCE;
    }

    /**
     * Query fully encoded, without the leading {@code ?} character.
     *
     * @return raw query to be sent (or that was received) over the wire
     */
    String rawValue();

    /**
     * Query NOT encoded without the leading {@code ?} character.
     *
     * @return query to be sent (or that was received) over the wire but decoded
     */
    String value();

    /**
     * Get raw value (undecoded) of a query parameter by its name.
     *
     * @param name query parameter name
     * @return value of the parameter, raw
     * @throws NoSuchElementException in case the parameter does not exist
     */
    String getRaw(String name) throws NoSuchElementException;

    /**
     * Get all raw values (undecoded) of a query parameter by its name.
     *
     * @param name query parameter name
     * @return values of the parameter, raw
     * @throws NoSuchElementException in case the parameter does not exist
     */
    List<String> getAllRaw(String name) throws NoSuchElementException;
}
