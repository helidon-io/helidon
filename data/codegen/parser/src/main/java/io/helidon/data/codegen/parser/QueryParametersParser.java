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
package io.helidon.data.codegen.parser;

import java.util.List;
import java.util.Objects;

import io.helidon.data.codegen.query.QueryParameters;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

/**
 * Query parameters parser.
 */
public class QueryParametersParser {

    private final QueryParams lexer;

    private QueryParametersParser() {
        lexer = new QueryParams(null);
        lexer.removeErrorListeners();
    }

    /**
     * Create an instance of Jakarta Data method name parser.
     *
     * @return new parser instance
     */
    public static QueryParametersParser create() {
        return new QueryParametersParser();
    }

    /**
     * Parse JDQL {@link String} and return query parameters.
     *
     * @param jdql JDQL {@link String}
     * @return query parameters
     */
    public QueryParameters parse(String jdql) {
        Objects.requireNonNull(jdql, "JDQL statement value is null");
        lexer.reset(CharStreams.fromString(jdql));
        List<? extends Token> tokens = lexer.getAllTokens();
        QueryParametersBuilder builder = QueryParametersBuilder.create(jdql);
        for (Token token : tokens) {
            switch (token.getType()) {
            case QueryParams.OrdinalParameter:
                String tokenString = token.getText();
                try {
                    builder.ordinal(Integer.parseInt(tokenString.substring(1)));
                } catch (NumberFormatException ex) {
                    throw new ParserException("Ordinal parameter " + tokenString + " does not contain numeric index", jdql);
                }
                break;
            case QueryParams.NamedParameter:
                builder.named(token.getText().substring(1));
                break;
            default:
                throw new ParserException("Unknown token type " + token.getType(), jdql);
            }
        }
        return builder.build();
    }

}
