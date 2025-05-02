/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.util.stream.Stream;

/**
 * Result of DML statement execution.
 */
public interface DbResultDml extends AutoCloseable {

    /**
     * Retrieves any auto-generated keys created as a result of executing this DML statement.
     *
     * @return the auto-generated keys
     */
    Stream<DbRow> generatedKeys();

    /**
     * Retrieve statement execution result.
     *
     * @return row count for Data Manipulation Language (DML) statements or {@code 0}
     *         for statements that return nothing.
     */
    long result();

    /**
     * Create new instance of DML statement execution result.
     *
     * @param generatedKeys the auto-generated keys
     * @param result the statement execution result
     * @return new instance of DML statement execution result
     */
    static DbResultDml create(Stream<DbRow> generatedKeys, long result) {
        return new DbResultDmlImpl(generatedKeys, result);
    }

}
