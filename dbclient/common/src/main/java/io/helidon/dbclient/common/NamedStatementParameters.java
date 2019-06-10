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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.dbclient.DbMapperManager;

/**
 * Statement with indexed parameters.
 */
class NamedStatementParameters implements StatementParameters {
    private final Map<String, Object> parameters = new HashMap<>();
    private final DbMapperManager dbMapperManager;

    NamedStatementParameters(DbMapperManager dbMapperManager) {
        this.dbMapperManager = dbMapperManager;
    }

    @Override
    public StatementParameters params(Map<String, ?> parameters) {
        this.parameters.clear();
        this.parameters.putAll(parameters);
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> StatementParameters namedParam(T parameters) {
        Class<T> theClass = (Class<T>) parameters.getClass();

        return params(dbMapperManager.toNamedParameters(parameters, theClass));
    }

    @Override
    public StatementParameters addParam(String name, Object parameter) {
        this.parameters.put(name, parameter);
        return this;
    }

    @Override
    public Map<String, Object> namedParams() {
        return this.parameters;
    }

    @Override
    public StatementParameters params(List<?> parameters) {
        throw new IllegalStateException("This is a statement with named parameters, cannot use indexed parameters.");
    }

    @Override
    public <T> StatementParameters indexedParam(T parameters) {
        throw new IllegalStateException("This is a statement with named parameters, cannot use indexed parameters.");
    }

    @Override
    public StatementParameters addParam(Object parameter) {
        throw new IllegalStateException("This is a statement with named parameters, cannot use indexed parameters.");
    }

    @Override
    public List<Object> indexedParams() {
        throw new IllegalStateException("This is a statement with named parameters, cannot use indexed parameters.");
    }
}
