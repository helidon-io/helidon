/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.security.util.TokenHandler;

/**
 * Definition of a map to bind a query param to a header.
 * Uses the {@link TokenHandler#header(Map, String)} to create a new header for
 * the extracted parameter.
 */
public final class QueryParamMapping {
    private final String queryParamName;
    private final TokenHandler tokenHandler;

    private QueryParamMapping(String queryParamName, TokenHandler tokenHandler) {
        this.queryParamName = queryParamName;
        this.tokenHandler = tokenHandler;
    }

    /**
     * Create a new mapping for a query parameter and {@link TokenHandler} to extract
     * the parameter and store it as a new header with possible transformation.
     *
     * @param queryParamName name of parameter
     * @param tokenHandler   handler to extract and store the header value
     * @return a new mapping
     */
    public static QueryParamMapping create(String queryParamName, TokenHandler tokenHandler) {
        return new QueryParamMapping(queryParamName, tokenHandler);
    }

    /**
     * Create a new mapping for a query parameter and a header name.
     *
     * @param queryParamName name of parameter
     * @param headerName     name of a header to store the value of the parameter in
     * @return a new mapping
     */
    public static QueryParamMapping create(String queryParamName, String headerName) {
        return new QueryParamMapping(queryParamName, TokenHandler.forHeader(headerName));
    }

    /**
     * Read a new instance from configuration.
     * The current node should contain a {@code "name"} and configuration for {@link TokenHandler}
     *
     * @param config configuration instance
     * @return new query parameter handler instance
     */
    public static QueryParamMapping create(Config config) {
        String name = config.get("name").asString().get();
        TokenHandler handler = config.as(TokenHandler::create).get();
        return create(name, handler);
    }

    /**
     * Name of the query parameter to map.
     * @return parameter name
     */
    public String queryParamName() {
        return queryParamName;
    }

    /**
     * Token handler used to create a header from the parameter.
     * @return header token handler
     */
    public TokenHandler tokenHandler() {
        return tokenHandler;
    }
}
