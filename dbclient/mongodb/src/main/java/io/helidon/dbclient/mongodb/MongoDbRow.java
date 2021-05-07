/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperException;
import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbColumn;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbRow;

/**
 * Mongo specific representation of a single row in a database.
 */
final class MongoDbRow implements DbRow {

    private final Map<String, DbColumn> columnsByName;
    private final List<DbColumn> columnsList;

    private final DbMapperManager dbMapperManager;
    private final MapperManager mapperManager;

    MongoDbRow(DbMapperManager dbMapperManager, MapperManager mapperManager, int size) {
        this.dbMapperManager = dbMapperManager;
        this.mapperManager = mapperManager;
        this.columnsByName = new HashMap<>(size);
        this.columnsList = new ArrayList<>(size);
    }

    MongoDbRow(DbMapperManager dbMapperManager, MapperManager mapperManager) {
        this.dbMapperManager = dbMapperManager;
        this.mapperManager = mapperManager;
        this.columnsByName = new HashMap<>();
        this.columnsList = new ArrayList<>();
    }

    void add(String name, DbColumn column) {
        columnsByName.put(name, column);
        columnsList.add(column);
    }

    @Override
    public DbColumn column(String name) {
        return columnsByName.get(name);
    }

    @Override
    public DbColumn column(int index) {
        return columnsList.get(index - 1);
    }

    @Override
    public void forEach(Consumer<? super DbColumn> columnAction) {
        columnsByName.values().forEach(columnAction);
    }

    @Override
    public <T> T as(Class<T> type) {
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        sb.append('{');
        for (DbColumn col : columnsList) {
            if (first) {
                first = false;
            } else {
                sb.append(',');
            }
            sb.append(col.name());
            sb.append(':');
            sb.append(col.value().toString());
        }
        sb.append('}');
        return sb.toString();
    }

}
