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

import java.util.Collections;
import java.util.List;

import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;
import io.helidon.data.codegen.query.DataQuery;
import io.helidon.data.codegen.query.QueryParameters;

final class JakartaQueryBuilder
        implements PersistenceGenerator.QueryBuilder {

    private final RepositoryInfo repositoryInfo;

    JakartaQueryBuilder(RepositoryInfo repositoryInfo) {
        this.repositoryInfo = repositoryInfo;
    }

    @Override
    public String buildSimpleQuery(DataQuery query) {
        return JakartaPersistenceDataQueryBuilder.create(repositoryInfo)
                .buildQuery(query, Collections.emptyList())
                .query();
    }

    @Override
    public PersistenceGenerator.Query buildQuery(DataQuery query) {
        return JakartaPersistenceDataQueryBuilder.create(repositoryInfo)
                .buildQuery(query, Collections.emptyList());
    }

    @Override
    public PersistenceGenerator.Query buildQuery(DataQuery query, List<CharSequence> params) {
        return JakartaPersistenceDataQueryBuilder.create(repositoryInfo)
                .buildQuery(query, params);
    }

    @Override
    public PersistenceGenerator.Query buildQuery(String query,
                                                 QueryParameters queryParameters,
                                                 List<MethodParameter> methodParameters) {
        return JakartaPersistenceQueryStringBuilder.create(repositoryInfo, queryParameters, methodParameters)
                .buildQuery(query);
    }

    @Override
    public PersistenceGenerator.Query buildCountQuery(DataQuery query, List<CharSequence> params) {
        return JakartaPersistenceDataQueryBuilder.create(repositoryInfo)
                .buildCountQuery(query, params);
    }

    @Override
    public PersistenceGenerator.QueryReturnType queryReturntype(DataQuery query) {
        return JakartaPersistenceBaseQueryBuilder.queryReturntype(query);
    }

}
