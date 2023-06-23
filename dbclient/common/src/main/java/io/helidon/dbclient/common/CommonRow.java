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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperException;
import io.helidon.dbclient.DbColumn;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbRow;

/**
 * Representation of a single row in a database (in SQL this would be a row, in a Document DB, this would be a single document).
 * <p>Common {@link DbRow} implementations ancestor with {@link DbMapperManager} and an array of {@link DbColumn}s.
 */
public abstract class CommonRow implements DbRow {

    private final DbMapperManager dbMapperManager;
    private final CommonColumn[] columns;
    private final Map<String, CommonColumn> namesIndex;

    protected CommonRow(CommonColumn[] columns, DbMapperManager dbMapperManager) {
        this.columns = columns;
        this.dbMapperManager = dbMapperManager;
        this.namesIndex = new HashMap<>(columns.length);
        for (int i = 0; i < columns.length; i++) {
            namesIndex.put(columns[i].name(), columns[i]);
        }
    }

    @Override
    public DbColumn column(String name) {
        return namesIndex.get(name);
    }

    @Override
    public DbColumn column(int index) {
        if (index < 1 || index > columns.length) {
            throw new IndexOutOfBoundsException(String.format("Column with index %d does not exist", index));
        }
        return columns[index - 1];
    }

    @Override
    public void forEach(Consumer<? super DbColumn> columnAction) {
        namesIndex.values().forEach(columnAction);
    }

    @Override
    public <T> T as(Class<T> type) throws MapperException {
        return dbMapperManager.read(this, type);
    }

    @Override
    public <T> T as(GenericType<T> type) throws MapperException {
        return dbMapperManager.read(this, type);
    }

    @Override
    public <T> T as(Function<DbRow, T> mapper) {
        return mapper.apply(this);
    }

}
