/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.app.tools;

import java.util.HashMap;

/**
 * Query parameters map with builder.
 */
public class QueryParams extends HashMap<String, String> {

    /**
     * Query parameter {@code name}.
     */
    public static final String NAME = "name";
    /**
     * Query parameter {@code id}
     */
    public static final String ID = "id";
    /**
     * Query parameter {@code fromid}.
     */
    public static final String FROM_ID = "fromid";
    /**
     * Query parameter {@code toid}.
     */
    public static final String TO_ID = "toid";

    /**
     * Shortcut for single parameter in a query.
     *
     * @param name  name of parameter
     * @param value value of parameter
     * @return query parameters {@code Map} with provided single value
     */
    public static QueryParams single(String name, String value) {
        return QueryParams.builder().add(name, value).build();
    }

    /**
     * Create query parameters builder.
     *
     * @return new query parameters builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Query parameter builder.
     */
    public static final class Builder {

        private final QueryParams params;

        private Builder() {
            params = new QueryParams();
        }

        /**
         * Add query parameter.
         *
         * @param name  name of parameter
         * @param value value of parameter
         * @return updated query parameter builder
         */
        public Builder add(String name, String value) {
            params.put(name, value);
            return this;
        }

        /**
         * Build query parameters with parameters currently stored in builder.
         *
         * @return query parameters {@code Map}.
         */
        public QueryParams build() {
            return params;
        }
    }
}
