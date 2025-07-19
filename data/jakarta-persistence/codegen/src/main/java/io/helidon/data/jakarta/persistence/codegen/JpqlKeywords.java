/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.data.jakarta.persistence.codegen;

/**
 * JPQL keywords used to build query {@link String}.
 */
class JpqlKeywords {

    static final char SPACE = ' ';
    static final char DOT = '.';
    static final char COMMA = ',';
    static final char COLON = ':';
    static final char LEFT_BRACKET = '(';
    static final char RIGHT_BRACKET = ')';
    static final char PERCENT = '%';
    static final char APOSTROPHE = '\'';
    static final char DOUBLE_QUOTE = '"';
    static final String SELECT = "SELECT";
    static final String DELETE = "DELETE";
    static final String UPDATE = "UPDATE";
    static final String COUNT = "COUNT";
    static final String MAX = "MAX";
    static final String MIN = "MIN";
    static final String SUM = "SUM";
    static final String AVG = "AVG";
    static final String WHERE = "WHERE";
    static final String FROM = "FROM";
    static final String DISTINCT = "DISTINCT";
    static final String NOT = "NOT";
    static final String AND = "AND";
    static final String OR = "OR";
    static final char EQUAL = '=';
    static final String NOT_EQUAL = "<>";
    static final char LESS_THAN = '<';
    static final String LESS_THAN_EQUAL = "<=";
    static final char GREATER_THAN = '>';
    static final String GREATER_THAN_EQUAL = ">=";
    static final String LIKE = "LIKE";
    static final String CONCAT = "CONCAT";
    static final String BETWEEN = "BETWEEN";
    static final String IN = "IN";
    static final String IS = "IS";
    static final String EMPTY = "EMPTY";
    static final String UPPER = "UPPER";
    static final String NULL = "NULL";
    static final String TRUE = "TRUE";
    static final String FALSE = "FALSE";
    static final String ORDER_BY = "ORDER BY";
    static final String ASC = "ASC";
    static final String DESC = "DESC";

    private JpqlKeywords() {
        throw new UnsupportedOperationException("No instances of JpqlKeywords are allowed");
    }

    /**
     * Print {@code COUNT} function call.
     *
     * @param builder target JPQL builder
     * @param param   Count  {@code COUNT} function parameter
     */
    static void count(StringBuilder builder, CharSequence param) {
        function(builder, param, COUNT);
    }

    /**
     * Print {@code MAX} function call.
     *
     * @param builder target JPQL builder
     * @param param   Count  {@code MAX} function parameter
     */
    static void max(StringBuilder builder, CharSequence param) {
        function(builder, param, MAX);
    }

    /**
     * Print {@code MIN} function call.
     *
     * @param builder target JPQL builder
     * @param param   Count  {@code MIN} function parameter
     */
    static void min(StringBuilder builder, CharSequence param) {
        function(builder, param, MIN);
    }

    /**
     * Print {@code AVG} function call.
     *
     * @param builder target JPQL builder
     * @param param   Count  {@code AVG} function parameter
     */
    static void avg(StringBuilder builder, CharSequence param) {
        function(builder, param, AVG);
    }

    /**
     * Print {@code SUM} function call.
     *
     * @param builder target JPQL builder
     * @param param   Count  {@code SUM} function parameter
     */
    static void sum(StringBuilder builder, CharSequence param) {
        function(builder, param, SUM);
    }

    /**
     * Optionally print {@code NOT} keyword into JPQL builder.
     * Single space character is prepended.
     *
     * @param builder target JPQL builder
     * @param not     whether {@code NOT} keyword should be added
     */
    static void maybeNot(StringBuilder builder, boolean not) {
        if (not) {
            builder.append(SPACE)
                    .append(NOT);
        }
    }

    /**
     * Optionally print {@code ','} into JPQL builder.
     * Single space character is appended.
     *
     * @param builder target JPQL builder
     * @param comma   whether {@code ','} should be added
     */
    static void maybeComma(StringBuilder builder, boolean comma) {
        if (comma) {
            builder.append(COMMA)
                    .append(SPACE);
        }
    }

    /**
     * Print entity property into JPQL builder.
     * Single {@code '.'} character is added between entity alias and property.
     *
     * @param builder     target JPQL builder
     * @param entityAlias entity alias in the statement
     * @param property    entity property
     * @param ignoreCase  whether property content is case-insensitive
     */
    static void property(StringBuilder builder,
                         CharSequence entityAlias,
                         CharSequence property,
                         boolean ignoreCase) {
        if (ignoreCase) {
            builder.append(UPPER)
                    .append("(")
                    .append(entityAlias)
                    .append(DOT)
                    .append(property)
                    .append(")");
        } else {
            builder.append(entityAlias)
                    .append(DOT)
                    .append(property);
        }
    }

    /**
     * Print entity property into JPQL builder.
     * Single {@code '.'} character is added between entity alias and property.
     *
     * @param builder     target JPQL builder
     * @param entityAlias entity alias in the statement
     * @param property    entity property
     */
    static void property(StringBuilder builder,
                         CharSequence entityAlias,
                         CharSequence property) {
        property(builder, entityAlias, property, false);
    }

    /**
     * Print statement named parameter into JPQL builder.
     *
     * @param builder    target JPQL builder
     * @param param      name of the parameter
     * @param ignoreCase whether parameter content is case-insensitive
     */
    static void param(StringBuilder builder,
                      CharSequence param,
                      boolean ignoreCase) {
        if (ignoreCase) {
            builder.append(UPPER)
                    .append("(")
                    .append(COLON)
                    .append(param)
                    .append(")");
        } else {
            builder.append(COLON)
                    .append(param);
        }
    }

    /**
     * Print statement named parameter into JPQL builder.
     *
     * @param builder target JPQL builder
     * @param param   name of the parameter
     */
    static void param(StringBuilder builder,
                      CharSequence param) {
        param(builder, param, false);
    }

    private static void function(StringBuilder builder, CharSequence param, CharSequence functionName) {
        builder.append(functionName)
                .append(LEFT_BRACKET)
                .append(param)
                .append(RIGHT_BRACKET);
    }

}
