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

package io.helidon.microprofile.graphql.server.model;

/**
 * An interface to represent a element that can generate a schema.
 */
public interface SchemaGenerator {
    String NOTHING = "";
    String COMMA_NEWLINE = ",\n";
    String COMMA_SPACE = ", ";
    String COMMA = ",";
    String COMMENT = "#";
    char SPACER = ' ';
    char NEWLINE = '\n';
    char COLON = ':';
    char EQUALS = '=';
    char MANDATORY = '!';
    char QUOTE = '"';
    char OPEN_CURLY = '{';
    char CLOSE_CURLY = '}';
    char OPEN_SQUARE = '[';
    char CLOSE_SQUARE = ']';
    char OPEN_PARENTHESES = '(';
    char CLOSE_PARENTHESES = ')';

    /**
     * Return the GraphQL schema representation of the element.
     *
     * @return the GraphQL schema representation of the element.
     */
    String getSchemaAsString();
}
