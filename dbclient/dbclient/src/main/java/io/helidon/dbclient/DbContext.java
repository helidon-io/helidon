/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.dbclient;

import java.util.List;

import io.helidon.common.mapper.MapperManager;

/**
 * Database context.
 */
public interface DbContext {

    /**
     * Configured missing values in named parameters {@link java.util.Map} handling.
     *
     * @return when set to {@code true}, named parameters value missing in the {@code Map} is considered
     *         as {@code null}, when set to {@code false}, any parameter value missing in the {@code Map}
     *         will cause an exception.
     */
    boolean missingMapParametersAsNull();

    /**
     * Configured statements.
     *
     * @return statements
     */
    DbStatements statements();

    /**
     * Configured DB Mapper manager.
     *
     * @return DB mapper manager
     */
    DbMapperManager dbMapperManager();

    /**
     * Configured mapper manager.
     *
     * @return mapper manager
     */
    MapperManager mapperManager();

    /**
     * Configured client services (interceptors).
     *
     * @return client services
     */
    List<DbClientService> clientServices();

    /**
     * Type of this database provider (such as jdbc:mysql, mongoDB etc.).
     *
     * @return name of the database provider
     */
    String dbType();
}
