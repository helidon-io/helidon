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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;
import io.helidon.data.codegen.query.QueryParameters;

import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.COMMA;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.LEFT_BRACKET;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.RIGHT_BRACKET;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.SPACE;

class JakartaPersistenceQueryStringBuilder extends JakartaPersistenceBaseQueryBuilder {

    private final List<PersistenceGenerator.QueryBuilder.MethodParameter> methodParameters;
    private final QueryParameters queryParameters;
    private final Map<String, CharSequence> paramsAliases;
    private final Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter;
    private final Query.Builder queryBuilder;

    private JakartaPersistenceQueryStringBuilder(RepositoryInfo repositoryInfo,
                                                 QueryParameters queryParameters,
                                                 List<PersistenceGenerator.QueryBuilder.MethodParameter> methodParameters) {
        super(repositoryInfo);
        this.methodParameters = methodParameters;
        this.queryParameters = queryParameters;
        this.paramsAliases = methodParameters.stream()
                .collect(Collectors.toMap(JakartaPersistenceQueryStringBuilder::parameterAlias,
                                          PersistenceGenerator.QueryBuilder.MethodParameter::name));
        this.setParameter = new HashMap<>();
        this.queryBuilder = Query.builder();
    }

    static JakartaPersistenceQueryStringBuilder create(RepositoryInfo repositoryInfo,
                                                       QueryParameters queryParameters,
                                                       List<PersistenceGenerator.QueryBuilder.MethodParameter> methodParameters) {
        return new JakartaPersistenceQueryStringBuilder(repositoryInfo, queryParameters, methodParameters);
    }

    PersistenceGenerator.Query buildQuery(String query) {
        queryBuilder.query(query);
        // Prepare Map of PersistenceGenerator.QuerySettings
        if (!queryParameters.isEmpty()) {
            switch (queryParameters.type()) {
            case NAMED:
                queryParameters.parameters()
                        .forEach(this::namedParameterSettings);
                break;
            case ORDINAL:
                queryParameters.parameters()
                        .forEach(this::ordinalParameterSettings);
                break;
            default:
                throw new CodegenException("Unknown query parameter type " + queryParameters.type());
            }
        }
        setParameter.values().forEach(queryBuilder::setting);
        queryBuilder.isDml(false);
        queryBuilder.returnType(PersistenceGenerator.QueryReturnType.ENTITY);
        return queryBuilder.build();
    }

    // Extract method parameter alias as String
    private static String parameterAlias(PersistenceGenerator.QueryBuilder.MethodParameter param) {
        return param.alias().toString();
    }

    private void namedParameterSettings(QueryParameters.Parameter queryParameter) {
        CharSequence methodParameter = paramsAliases.get(queryParameter.name());
        if (methodParameter == null) {
            throw new CodegenException("Method parameter " + queryParameter.name() + " is missing");
        }
        setParameter.putIfAbsent(queryParameter.name(), new Param(queryParameter, methodParameter));
    }

    private void ordinalParameterSettings(QueryParameters.Parameter queryParameter) {
        // Query parameters indexing starts from 1, methodParameters List indexing starts from 0
        int queryParameterIndex = queryParameter.index() - 1;
        if (methodParameters.size() > queryParameterIndex) {
            setParameter.putIfAbsent(queryParameter.name(),
                                     new Param(queryParameter,
                                               methodParameters.get(queryParameterIndex)
                                                       .name()));
        } else {
            throw new CodegenException("Method parameter with index " + queryParameter.index() + "is missing");
        }
    }

    private record Param(QueryParameters.Parameter queryParameter,
                         CharSequence methodParameter)
            implements PersistenceGenerator.QuerySettings {

        private static final String SET = "setParameter";

        @Override
        public CharSequence code() {
            return new StringBuilder(SET.length()
                                             + queryParameter.code().length()
                                             + methodParameter.length()
                                             + 6)
                    .append(SET)
                    .append(LEFT_BRACKET)
                    .append(queryParameter.code())
                    .append(COMMA)
                    .append(SPACE)
                    .append(methodParameter)
                    .append(RIGHT_BRACKET);
        }

    }

}
