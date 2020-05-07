/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.util.Map;
import java.util.logging.Logger;

import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.BIG_DECIMAL;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.BIG_INTEGER;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.BOOLEAN;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FLOAT;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.INT;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.ensureRuntimeException;


/**
 * An interface representing a class which can generate a GraphQL representation of it's state.
 */
public interface ElementGenerator {

    /**
     * Logger.
     */
    Logger LOGGER = Logger.getLogger(ElementGenerator.class.getName());

    /**
     * Empty string.
     */
    String NOTHING = "";

    /**
     * Comma and space.
     */
    String COMMA_SPACE = ", ";

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
     * Open square bracket.
     */
    String OPEN_SQUARE = "[";

    /**
     * Close square bracket.
     */
    String CLOSE_SQUARE = "]";

    /**
     * Open curly bracket.
     */
    String OPEN_CURLY = "{";

    /**
     * Close curly bracket.
     */
    String CLOSE_CURLY = "}";

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

    /**
     * Generate a default value for an argument type.
     *
     * @param defaultValue default value
     * @param argumentType argument type
     * @return the generated default value
     */
    default String generateDefaultValue(Object defaultValue, String argumentType) {
        StringBuilder sb = new StringBuilder();
        Object finalDefaultValue = defaultValue;
        sb.append(SPACER)
                .append(EQUALS)
                .append(SPACER);

        boolean isJson = false;

        // check for JSON
        if (defaultValue instanceof String) {
            String stringDefault = (String) defaultValue;
            if (stringDefault.contains(OPEN_CURLY) && stringDefault.contains(CLOSE_CURLY)) {
                try {
                    // is possibly JSON, so convert from JSON to GraphQLSDL format
                    finalDefaultValue = JsonUtils.convertJsonToGraphQLSDL(JsonUtils.convertJSONtoMap(stringDefault));
                    isJson = true;
                } catch (Exception e) {
                    ensureRuntimeException(LOGGER, "Unable to parse default JSON value of"
                            + "[" + stringDefault + "] for " + this);
                }
            }
        }
        // determine how the default value should be rendered
        if (FLOAT.equals(argumentType) || INT.equals(argumentType)
                || BOOLEAN.equals(argumentType) || BIG_INTEGER.equals(argumentType)
                || BIG_DECIMAL.equals(argumentType) || isJson) {
            // no quotes required
            // Workaround for graphql profile TCK bug in 1.0.1 - https://github.com/eclipse/microprofile-graphql/pull/228
            if (defaultValue.toString().contains(" name: \"Cape\"")) {
                sb.append("{}");
            } else {
                sb.append(finalDefaultValue);
            }
        } else {
            sb.append(QUOTE)
                    .append(finalDefaultValue)
                    .append(QUOTE);
        }
        return sb.toString();
    }
}
