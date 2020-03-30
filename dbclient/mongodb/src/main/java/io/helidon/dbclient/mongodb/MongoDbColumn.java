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
package io.helidon.dbclient.mongodb;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperException;
import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbColumn;
import io.helidon.dbclient.DbMapperManager;

/**
 * Mongo specific column data and metadata.
 */
public final class MongoDbColumn implements DbColumn {

    private final MapperManager mapperManager;
    private final String name;
    private final Object value;

    MongoDbColumn(DbMapperManager dbMapperManager, MapperManager mapperManager, String name, Object value) {
        this.mapperManager = mapperManager;
        this.name = name;
        this.value = value;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T as(Class<T> type) throws MapperException {
        if (type.equals(javaType())) {
            return (T) value;
        }

        return map(value, type);
    }

    @Override
    public <T> T as(GenericType<T> type) throws MapperException {
        return map(value, type);
    }

    @SuppressWarnings("unchecked")
    private <S, T> T map(S value, Class<T> targetType) {
        Class<S> sourceType = (Class<S>) javaType();

        try {
            return mapperManager.map(value, sourceType, targetType);
        } catch (MapperException e) {
            if (targetType.equals(String.class)) {
                return (T) String.valueOf(value);
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private <S, T> T map(S value, GenericType<T> targetType) {
        Class<S> sourceClass = (Class<S>) javaType();
        GenericType<S> sourceType = GenericType.create(sourceClass);

        return mapperManager.map(value, sourceType, targetType);
    }

    @Override
    public Class<?> javaType() {
        return (null == value) ? String.class : value.getClass();
    }

    @Override
    public String dbType() {
        throw new UnsupportedOperationException("dbType() is not supported yet.");
    }

    @Override
    public String name() {
        return name;
    }

}
