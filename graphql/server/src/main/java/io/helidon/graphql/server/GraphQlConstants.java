/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.graphql.server;

/**
 * Constants used across GraphQL implementation.
 */
public final class GraphQlConstants {
    /**
     * Key for errors.
     */
    public static final String ERRORS = "errors";

    /**
     * Key for extensions.
     */
    public static final String EXTENSIONS = "extensions";

    /**
     * Key for locations.
     */
    public static final String LOCATIONS = "locations";

    /**
     * Key for message.
     */
    public static final String MESSAGE = "message";

    /**
     * Key for data.
     */
    public static final String DATA = "data";

    /**
     * Key for line.
     */
    public static final String LINE = "line";

    /**
     * Key for column.
     */
    public static final String COLUMN = "column";

    /**
     * Key for path.
     */
    public static final String PATH = "path";
    /**
     * Default web context of GraphQl endpoint.
     */
    public static final String GRAPHQL_WEB_CONTEXT = "/graphql";
    /**
     * Default URI of GraphQl schema under the {@link #GRAPHQL_WEB_CONTEXT}.
     */
    public static final String GRAPHQL_SCHEMA_URI = "/schema.graphql";
    /**
     * Default error message to return for unchecked exceptions and errors.
     */
    public static final String DEFAULT_ERROR_MESSAGE = "Server Error";

    // forbid instantiation
    private GraphQlConstants() {
    }
}
