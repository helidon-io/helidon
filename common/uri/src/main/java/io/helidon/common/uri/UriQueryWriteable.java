/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
import java.util.function.Consumer;

/**
 * Mutable HTTP query.
 */
public interface UriQueryWriteable extends UriQuery {
    /**
     * Create a new HTTP Query to write parameter into.
     *
     * @return new mutable HTTP query
     */
    static UriQueryWriteable create() {
        return new UriQueryWriteableImpl();
    }

    /**
     * Update this query by copying all names and their value(s) from the provided query.
     *
     * @param uriQuery uri query to read
     * @return updated instance
     */
    UriQueryWriteable from(UriQuery uriQuery);

    /**
     * Set a query parameter with values.
     *
     * @param name  name of the parameter
     * @param value value(s) of the parameter
     * @return this instance
     */
    UriQueryWriteable set(String name, String... value);

    /**
     * Add a new query parameter or add a value to existing.
     *
     * @param name  name of the parameter
     * @param value additional value of the parameter
     * @return this instance
     */
    UriQueryWriteable add(String name, String value);

    /**
     * Set a query parameter with values, if not already defined.
     *
     * @param name  name of the parameter
     * @param value value(s) of the parameter
     * @return this instance
     */
    UriQueryWriteable setIfAbsent(String name, String... value);

    /**
     * Remove a query parameter.
     *
     * @param name name of the parameter
     * @return this instance
     */
    UriQueryWriteable remove(String name);

    /**
     * Remove a query parameter.
     *
     * @param name            name of the parameter
     * @param removedConsumer consumer of existing values, only called if there was an existing value
     * @return this instance
     */
    UriQueryWriteable remove(String name, Consumer<List<String>> removedConsumer);

    /**
     * Update from a query string (with <b>encoded</b> values).
     * <p>
     * This documentation (and behavior) has been changed, as we cannot create a proper query from {@code decoded} values,
     *  as these may contain characters used to split the query.
     * @param queryString encoded query string to update this instance
     */
    void fromQueryString(String queryString);

    /**
     * Clear all query parameters.
     */
    void clear();
}
