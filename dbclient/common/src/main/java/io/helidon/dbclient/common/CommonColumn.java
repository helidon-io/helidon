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

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperException;
import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbColumn;

/**
 * Column data and metadata.
 * <p>Common {@link DbColumn} implementations ancestor with {@link MapperManager} and database column value.
 */
public abstract class CommonColumn implements DbColumn {

    private final Object value;
    private final  MapperManager mapperManager;

    /**
     * Creates an instance of {@link CommonColumn}.
     *
     * @param value value of this column
     * @param mapperManager mapper manager
     */
    protected CommonColumn(Object value, MapperManager mapperManager) {
        this.value = value;
        this.mapperManager = mapperManager;
    }

    /**
     * Get raw value of the database column.
     *
     * @return raw value of the column
     */
    protected Object rawValue() {
        return value;
    }

    @Override
    public <T> T as(Class<T> type) throws MapperException {
        if (null == value) {
            return null;
        }
        if (type.isAssignableFrom(value.getClass())) {
            return type.cast(value);
        }
        return map(value, type);
    }

    @Override
    public <T> T as(GenericType<T> type) throws MapperException {
        if (null == value) {
            return null;
        }
        if (type.isClass()) {
            Class<?> theClass = type.rawType();
            if (theClass.isAssignableFrom(value.getClass())) {
                return type.cast(value);
            }
        }
        return map(value, type);
    }

    /**
     * Map value to target type using {@link io.helidon.common.mapper.Mapper}.
     *
     * @param value source value
     * @param type target type
     * @param <SRC> type of the source value
     * @param <T> type of the target value
     * @return result of the mapping
     * @throws MapperException in case the mapper was not found or failed
     */
    @SuppressWarnings("unchecked")
    protected <SRC, T> T map(SRC value, GenericType<T> type) {
        Class<SRC> theClass = (Class<SRC>) value.getClass();
        return mapperManager.map(value, GenericType.create(theClass), type, DbClient.MAPPING_QUALIFIER);
    }

    /**
     * Map value to target type using {@link io.helidon.common.mapper.Mapper}.
     * {@link String#valueOf(Object)} is used as fallback option when {@code Mapper} fails.
     *
     * @param value source value
     * @param type target type
     * @param <SRC> type of the source value
     * @param <T> type of the target value
     * @return result of the mapping
     */
    @SuppressWarnings("unchecked")
    protected <SRC, T> T map(SRC value, Class<T> type) {
        Class<SRC> theClass = (Class<SRC>) value.getClass();
        try {
            return mapperManager.map(value, theClass, type, DbClient.MAPPING_QUALIFIER);
        } catch (MapperException e) {
            if (type.equals(String.class)) {
                return (T) String.valueOf(value);
            }
            throw e;
        }
    }

}
