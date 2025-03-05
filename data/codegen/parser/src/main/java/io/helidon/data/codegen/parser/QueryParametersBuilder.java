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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import io.helidon.data.codegen.query.QueryParameters;

// This is pkg only visible, Builder is implemented just to follow common pattern

/**
 * {@link QueryParameters} builder.
 */
class QueryParametersBuilder implements io.helidon.common.Builder<QueryParametersBuilder, QueryParameters> {

    // Used in ParserException
    private final String statement;
    private final Set<QueryParameters.Parameter> parameters;
    private QueryParameters.Type type;

    private QueryParametersBuilder(String statement) {
        Objects.requireNonNull(statement);
        this.statement = statement;
        this.type = null;
        this.parameters = new HashSet<>();
    }

    /**
     * Create new {@link QueryParametersBuilder} instance.
     *
     * @param statement statement being parsed (used in exceptions)
     * @return new instance of {@link QueryParametersBuilder}
     */
    static QueryParametersBuilder create(String statement) {
        return new QueryParametersBuilder(statement);
    }

    @Override
    public QueryParameters build() {
        return QueryParameters.create(type, Set.copyOf(parameters));
    }

    /**
     * Add ordinal parameter.
     * All parameters must be of the same type. Adding both ordinal and named parameters will cause an exception.
     *
     * @param index index of the parameter
     * @throws ParserException when named parameter was added before
     */
    void ordinal(int index) {
        if (type == null) {
            type = QueryParameters.Type.ORDINAL;
        } else {
            if (type == QueryParameters.Type.NAMED) {
                throw new ParserException("Cannot add ordinal parameter into named parameters list", statement);
            }
        }
        parameters.add(QueryParameters.ordinalParameter(index));
    }

    /**
     * Add named parameter.
     * All parameters must be of the same type. Adding both named and ordinal parameters will cause an exception.
     *
     * @param name name of the parameter
     * @throws ParserException when ordinal parameter was added before
     */
    void named(String name) {
        if (type == null) {
            type = QueryParameters.Type.NAMED;
        } else {
            if (type == QueryParameters.Type.ORDINAL) {
                throw new ParserException("Cannot add named parameter into ordinal parameters list", statement);
            }
        }
        parameters.add(QueryParameters.namedParameter(name));
    }

}
