/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
package io.helidon.data.sql.common;

import java.util.Objects;

import javax.sql.DataSource;

import io.helidon.builder.api.Prototype;
import io.helidon.data.DataException;

/**
 * Generated builder support for shared SQL configuration.
 */
final class SqlConfigSupport {

    private SqlConfigSupport() {
        throw new UnsupportedOperationException("No instances of SqlConfigSupport are allowed");
    }

    /**
     * Custom methods exposed by shared SQL configuration builders.
     */
    static final class CustomMethods {

        private CustomMethods() {
        }

        /**
         * Uses an existing application-owned data source as the connection
         * source.
         * <p>
         * The application retains ownership of the data source lifecycle.
         *
         * @param builder generated SQL configuration builder
         * @param dataSource existing data source
         * @throws NullPointerException if the data source is {@code null}
         */
        @Prototype.BuilderMethod
        static void dataSource(SqlConfig.BuilderBase<?, ?> builder, DataSource dataSource) {
            Objects.requireNonNull(dataSource, "The data source must not be null.");
            builder.dataSourceInstance(dataSource);
        }
    }

    /**
     * Validates the shared connection-source invariant.
     */
    static final class Decorator implements Prototype.BuilderDecorator<SqlConfig.BuilderBase<?, ?>> {

        /**
         * Requires exactly one configured connection source.
         *
         * @param target generated SQL configuration builder
         * @throws NullPointerException if the builder is {@code null}
         * @throws DataException if no source or multiple sources are configured
         */
        @Override
        public void decorate(SqlConfig.BuilderBase<?, ?> target) {
            Objects.requireNonNull(target, "The SQL configuration builder must not be null.");
            int sourceCount = target.connection().isPresent() ? 1 : 0;
            sourceCount += target.dataSource().isPresent() ? 1 : 0;
            sourceCount += target.dataSourceInstance().isPresent() ? 1 : 0;
            if (sourceCount == 0) {
                throw new DataException("SQL configuration does not define a connection source. Configure exactly one using "
                                                + "connection properties, a data source name, or a DataSource instance.");
            }
            if (sourceCount > 1) {
                throw new DataException("SQL configuration defines multiple connection sources. Configure exactly one using "
                                                + "connection properties, a data source name, or a DataSource instance.");
            }
        }
    }
}
