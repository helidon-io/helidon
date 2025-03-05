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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.helidon.codegen.CodegenException;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;
import io.helidon.data.codegen.common.spi.PersistenceGenerator.QueryReturnType;
import io.helidon.data.codegen.query.DataQuery;
import io.helidon.data.codegen.query.ProjectionExpression;

abstract class JakartaPersistenceBaseQueryBuilder extends JakartaPersistenceBaseBuilder {

    JakartaPersistenceBaseQueryBuilder(RepositoryInfo repositoryInfo) {
        super(repositoryInfo);
    }

    // Retrieve return type of the provided query
    static QueryReturnType queryReturntype(DataQuery query) {
        if (query.projection().expression().isPresent()) {
            ProjectionExpression expression = query.projection().expression().get();
            switch (expression.operator()) {
                case First:
                    return QueryReturnType.ENTITY;
                case Exists:
                    return QueryReturnType.BOOLEAN;
                case Count:
                case Min:
                case Max:
                case Sum:
                case Avg:
                    return QueryReturnType.NUMBER;
                default:
                    throw new CodegenException("Unknown projection expression operator " + expression.operator());
            }
        } else {
            return QueryReturnType.ENTITY;
        }
    }

    abstract static class BaseQuery {

        private final List<PersistenceGenerator.QuerySettings> settings;
        private final PersistenceGenerator.QueryReturnType returnType;
        private final boolean isDml;

        private BaseQuery(List<PersistenceGenerator.QuerySettings> settings,
                          PersistenceGenerator.QueryReturnType returnType,
                          boolean isDml) {
            Objects.requireNonNull(settings, "Query setting value is null");
            Objects.requireNonNull(returnType, "Query return type value is null");
            this.settings = settings;
            this.returnType = returnType;
            this.isDml = isDml;
        }

        public List<PersistenceGenerator.QuerySettings> settings() {
            return settings;
        }

        public PersistenceGenerator.QueryReturnType returnType() {
            return returnType;
        }

        public boolean isDml() {
            return isDml;
        }

        abstract static class BaseBuilder<B extends BaseBuilder<B, Q>, Q extends BaseQuery>
                implements io.helidon.common.Builder<B, Q> {

            private final List<PersistenceGenerator.QuerySettings> settings;
            private PersistenceGenerator.QueryReturnType returnType;
            private boolean isDml;

            private BaseBuilder() {
                this.settings = new ArrayList<>();
                this.returnType = null;
                this.isDml = false;
            }

            B setting(PersistenceGenerator.QuerySettings setting) {
                this.settings.add(setting);
                return identity();
            }

            B returnType(PersistenceGenerator.QueryReturnType returnType) {
                this.returnType = returnType;
                return identity();
            }

            B isDml(boolean isDml) {
                this.isDml = isDml;
                return identity();
            }

            List<PersistenceGenerator.QuerySettings> settings() {
                return settings;
            }

            PersistenceGenerator.QueryReturnType returnType() {
                return returnType;
            }

            boolean isDml() {
                return isDml;
            }

        }

    }

    static final class Query extends BaseQuery implements PersistenceGenerator.Query {

        private final String query;

        private Query(String query,
                      List<PersistenceGenerator.QuerySettings> settings,
                      PersistenceGenerator.QueryReturnType returnType,
                      boolean isDml) {
            super(settings, returnType, isDml);
            Objects.requireNonNull(query, "Query statement value is null");
            this.query = query;
        }

        @Override
        public String query() {
            return query;
        }

        static Builder builder() {
            return new Builder();
        }

        static class Builder extends BaseBuilder<Builder, Query>
                implements io.helidon.common.Builder<Builder, Query> {

            private String query;

            private Builder() {
                super();
                this.query = null;
            }

            Builder query(String query) {
                this.query = query;
                return this;
            }

            @Override
            public Query build() {
                return new Query(query, List.copyOf(settings()), returnType(), isDml());
            }

        }

    }

}
