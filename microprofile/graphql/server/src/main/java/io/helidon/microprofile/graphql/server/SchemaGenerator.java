/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.graphql.server;

/**
 * An interface to represent a element that can generate a schema.
 */
public interface SchemaGenerator {
    /**
     * Empty string.
     */
    String NOTHING = "";

    /**
     * Comma and space.
     */
    String COMMA_SPACE = ", ";

    /**
     * Comma.
     */
    String COMMA = ",";

    /**
     * Triple quote.
     */
    String TRIPLE_QUOTE = "\"\"\"";

    /**
     * Double quote.
     */
    String QUOTE = "\"";

    /**
     * Spacer.
     */
    String SPACER = " ";

    /**
     * Newline.
     */
    char NEWLINE = '\n';

    /**
     * Colon.
     */
    char COLON = ':';

    /**
     * Equals.
     */
    char EQUALS = '=';

    /**
     * Mandatory indicator.
     */
    char MANDATORY = '!';

    /**
     * Open curly bracket.
     */
    char OPEN_CURLY = '{';

    /**
     * Close curly bracket.
     */
    char CLOSE_CURLY = '}';

    /**
     * Open square bracket.
     */
    char OPEN_SQUARE = '[';

    /**
     * Close square bracket.
     */
    char CLOSE_SQUARE = ']';

    /**
     * Open parenthesis.
     */
    char OPEN_PARENTHESES = '(';

    /**
     * Close parenthesis.
     */
    char CLOSE_PARENTHESES = ')';

    /**
     * Return the GraphQL schema representation of the element.
     *
     * @return the GraphQL schema representation of the element.
     */
    String getSchemaAsString();
}
