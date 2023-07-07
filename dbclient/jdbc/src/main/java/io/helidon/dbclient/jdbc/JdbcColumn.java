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

import java.lang.System.Logger.Level;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbColumnBase;

/**
 * JDBC implementation of {@link io.helidon.dbclient.DbColumn}.
 */
class JdbcColumn extends DbColumnBase {

    private static final System.Logger LOGGER = System.getLogger(JdbcColumn.class.getName());

    private final MetaData metaData;

    private JdbcColumn(Object value, MetaData metaData, MapperManager mapperManager) {
        super(value, mapperManager, DbClient.MAPPING_QUALIFIER);
        this.metaData = metaData;
    }

    @Override
    public Class<?> javaType() {
        if (null == metaData.javaType()) {
            if (null == rawValue()) {
                return null;
            }
            return rawValue().getClass();
        } else {
            return metaData.javaType();
        }
    }

    @Override
    public String dbType() {
        return metaData.sqlType();
    }

    @Override
    public String name() {
        return metaData.name();
    }

    /**
     * Create JDBC database column.
     *
     * @param rs       {@link ResultSet} with cursor set to row to be processed
     * @param metaData column metadata
     * @param index    {@link java.sql.ResultSet} column index (starting from 1)
     * @return column metadata
     * @throws SQLException if a database access error occurs or this method is called on a closed result set
     */
    static JdbcColumn create(ResultSet rs, MetaData metaData, MapperManager mapperManager, int index) throws SQLException {
        return new JdbcColumn(
                rs.getObject(index),
                metaData,
                mapperManager);
    }

    /**
     * JDBC column metadata.
     */
    static final class MetaData {

        private final String name;
        private final String sqlType;
        private final Class<?> javaType;

        private MetaData(String name, String sqlType, Class<?> javaType) {
            this.name = name;
            this.sqlType = sqlType;
            this.javaType = javaType;
        }

        String name() {
            return name;
        }

        String sqlType() {
            return sqlType;
        }

        Class<?> javaType() {
            return javaType;
        }

        /**
         * Create JDBC database column metadata.
         *
         * @param metaData meta data from {@link java.sql.ResultSet}
         * @param index    {@link java.sql.ResultSet} column index (starting from 1)
         * @return column metadata
         * @throws SQLException if a database access error occurs or this method is called on a closed result set
         */
        static MetaData create(ResultSetMetaData metaData, int index) throws SQLException {
            return new MetaData(metaData.getColumnLabel(index),
                    metaData.getColumnTypeName(index),
                    classByName(metaData.getColumnClassName(index)));
        }


        private static Class<?> classByName(String className) {
            if (className == null) {
                return null;
            }
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ex) {
                LOGGER.log(Level.DEBUG, "Unable to find column class: " + className, ex);
                return null;
            }
        }

    }

}
