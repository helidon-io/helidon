/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.jdbc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.context.Context;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbStatementType;

abstract class JdbcClientServiceContext implements DbClientServiceContext {

    private final StatementContext statementContext;
    private Context context;

    private JdbcClientServiceContext(StatementContext statementContext) {
        this.statementContext = statementContext;
    }

    @Override
    public String dbType() {
        return statementContext.dbType();
    }

    @Override
    public Context context() {
        return context;
    }

    @Override
    public String statement() {
        return statementContext.statement();
    }

    @Override
    public String statementName() {
        return statementContext.statementName();
    }

    @Override
    public DbStatementType statementType() {
        return statementContext.dbStatementType();
    }

    @Override
    public JdbcClientServiceContext context(Context context) {
        this.context = context;
        return this;
    }

    @Override
    public JdbcClientServiceContext statementName(String statementName) {
        statementContext.statementName(statementName);
        return this;
    }

    StatementContext statementContext() {
        return statementContext;
    }

    static final class Indexed extends JdbcClientServiceContext {

        private final StatementIndexedParams params;

        Indexed(StatementContext statementContext, StatementIndexedParams params) {
            super(statementContext);
            this.params = params;
        }

        @Override
        public  boolean isIndexed() {
            return true;
        }

        @Override
        public  boolean isNamed() {
            return false;
        }

        @Override
        public Optional<List<Object>> indexedParameters() {
            return Optional.of(params.parametersAsList());
        }

        @Override
        public Optional<Map<String, Object>> namedParameters() {
            throw new IllegalStateException("Named parameters are not available for statement with indexed parameters");
        }

        @Override
        public JdbcClientServiceContext statement(String statement, List<Object> indexedParams) {
            statementContext().statement(statement);
            params.parametersFromList(indexedParams);
            return this;
        }

        @Override
        public DbClientServiceContext parameters(List<Object> indexedParams) {
            params.parametersFromList(indexedParams);
            return this;
        }

        @Override
        public DbClientServiceContext statement(String statement, Map<String, Object> namedParams) {
            throw new IllegalStateException("Named parameters are not available for statement with indexed parameters");
        }

        @Override
        public DbClientServiceContext parameters(Map<String, Object> namedParameters) {
            throw new IllegalStateException("Named parameters are not available for statement with indexed parameters");
        }

    }

    static final class Named extends JdbcClientServiceContext {

        private final StatementNamedParams params;

        Named(StatementContext statementContext, StatementNamedParams params) {
            super(statementContext);
            this.params = params;
        }

        @Override
        public  boolean isNamed() {
            return true;
        }

        @Override
        public  boolean isIndexed() {
            return false;
        }


        @Override
        public Optional<Map<String, Object>> namedParameters() {
            return Optional.of(params.parametersAsMap());
        }

        @Override
        public Optional<List<Object>> indexedParameters() {
            throw new IllegalStateException("Indexed parameters are not available for statement with named parameters");
        }

        @Override
        public JdbcClientServiceContext statement(String statement, Map<String, Object> namedParams) {
            statementContext().statement(statement);
            params.parametersFromMap(namedParams);
            return this;
        }

        @Override
        public DbClientServiceContext parameters(Map<String, Object> namedParams) {
            params.parametersFromMap(namedParams);
            return this;
        }

        @Override
        public DbClientServiceContext statement(String statement, List<Object> indexedParams) {
            throw new IllegalStateException("Indexed parameters are not available for statement with named parameters");
        }

        @Override
        public DbClientServiceContext parameters(List<Object> indexedParams) {
            throw new IllegalStateException("Indexed parameters are not available for statement with named parameters");
        }

    }

    // No parameters were set in the statement so it's not known whether it's indexed or named
    // FIXME: It may be possible to allow setting parameters and choosing parameters type. But it's not implemented yet.
    static final class NoParams extends JdbcClientServiceContext {

        private final StatementParams params;

        NoParams(StatementContext statementContext, StatementParams params) {
            super(statementContext);
            this.params = params;
        }

        @Override
        public  boolean isNamed() {
            return false;
        }

        @Override
        public  boolean isIndexed() {
            return false;
        }


        @Override
        public Optional<Map<String, Object>> namedParameters() {
            return Optional.empty();
        }

        @Override
        public Optional<List<Object>> indexedParameters() {
            return Optional.empty();
        }

        @Override
        public JdbcClientServiceContext statement(String statement, Map<String, Object> namedParams) {
            throw new IllegalStateException("Named parameters are not available for statement with no parameters");
        }

        @Override
        public DbClientServiceContext parameters(Map<String, Object> namedParams) {
            throw new IllegalStateException("Named parameters are not available for statement with no parameters");
        }

        @Override
        public DbClientServiceContext statement(String statement, List<Object> indexedParams) {
            throw new IllegalStateException("Indexed parameters are not available for statement with no parameters");
        }

        @Override
        public DbClientServiceContext parameters(List<Object> indexedParams) {
            throw new IllegalStateException("Indexed parameters are not available for statement with no parameters");
        }

    }

}
