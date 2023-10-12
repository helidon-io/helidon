/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbColumn;
import io.helidon.dbclient.DbContext;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbRow;

import org.bson.Document;

/**
 * Mongo specific representation of a single row in a database.
 */
final class MongoDbRow implements DbRow {

    private static final System.Logger LOGGER = System.getLogger(MongoDbRow.class.getName());
    private final Map<String, DbColumn> columnsByName;
    private final List<DbColumn> columnsList;
    private final DbMapperManager dbMapperManager;

    /**
     * Create a new MongoDB row.
     *
     * @param doc     document
     * @param context context
     */
    MongoDbRow(Document doc, DbContext context) {
        this.dbMapperManager = context.dbMapperManager();
        int size = doc.size();
        this.columnsByName = new HashMap<>(size);
        this.columnsList = new ArrayList<>(size);
        doc.forEach((name, value) -> {
            LOGGER.log(System.Logger.Level.TRACE, () -> String.format(
                    "Column name = %s, value = %s",
                    name,
                    (value != null ? value.toString() : "N/A")));

            add(name, new MongoDbColumn(context.mapperManager(), name, value));
        });
    }

    /**
     * Add a column.
     *
     * @param name   column name
     * @param column column
     */
    void add(String name, DbColumn column) {
        columnsByName.put(name, column);
        columnsList.add(column);
    }

    @Override
    public DbColumn column(String name) {
        DbColumn column = columnsByName.get(name);
        if (column != null) {
            return column;
        }
        throw new DbClientException(String.format("Column with name %s does not exist", name));
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
            sb.append(col.get().toString());
        }
        sb.append('}');
        return sb.toString();
    }

}
