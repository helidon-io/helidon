/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.dbclient.common;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.helidon.dbclient.DbMapperManager;

/**
 * Statement with indexed parameters.
 */
class IndexedStatementParameters implements StatementParameters {
    private final List<Object> parameters = new LinkedList<>();
    private final DbMapperManager dbMapperManager;

    IndexedStatementParameters(DbMapperManager dbMapperManager) {
        this.dbMapperManager = dbMapperManager;
    }

    @Override
    public StatementParameters params(List<?> parameters) {
        this.parameters.clear();
        this.parameters.addAll(parameters);
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> StatementParameters indexedParam(T parameters) {
        Class<T> theClass = (Class<T>) parameters.getClass();

        params(dbMapperManager.toIndexedParameters(parameters, theClass));
        return this;
    }

    @Override
    public List<Object> indexedParams() {
        return this.parameters;
    }

    @Override
    public StatementParameters addParam(Object parameter) {
        parameters.add(parameter);
        return this;
    }

    @Override
    public StatementParameters params(Map<String, ?> parameters) {
        throw new IllegalStateException("This is a statement with indexed parameters, cannot use named parameters.");
    }

    @Override
    public <T> StatementParameters namedParam(T parameters) {
        throw new IllegalStateException("This is a statement with indexed parameters, cannot use named parameters.");
    }

    @Override
    public StatementParameters addParam(String name, Object parameter) {
        throw new IllegalStateException("This is a statement with indexed parameters, cannot use named parameters.");
    }

    @Override
    public Map<String, Object> namedParams() {
        throw new IllegalStateException("This is a statement with indexed parameters, cannot use named parameters.");
    }
}
