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
package io.helidon.dbclient.common;

import java.util.List;
import java.util.Map;

import io.helidon.dbclient.DbStatement;

/**
 * Common statement methods and fields.
 *
 * @param <S> type of the descendant of this class
 */
public abstract class CommonStatement<S extends DbStatement<S>> implements DbStatement<S> {

    private final CommonClientContext context;

    protected CommonStatement(CommonClientContext context) {
        this.context = context;
    }

    @Override
    public S namedParam(Object parameters) {
        @SuppressWarnings("unchecked")
        Class<Object> theClass = (Class<Object>) parameters.getClass();
        params(context.dbMapperManager().toNamedParameters(parameters, theClass));
        return identity();
    }

    @Override
    public S indexedParam(Object parameters) {
        @SuppressWarnings("unchecked")
        Class<Object> theClass = (Class<Object>) parameters.getClass();
        params(context.dbMapperManager().toIndexedParameters(parameters, theClass));
        return identity();
    }

    @Override
    public S params(List<?> parameters) {
        parameters.forEach(this::addParam);
        return identity();
    }

    @Override
    public S params(Map<String, ?> parameters) {
        parameters.forEach(this::addParam);
        return identity();
    }

    @SuppressWarnings("unchecked")
    protected S identity() {
        return (S) this;
    }

}
