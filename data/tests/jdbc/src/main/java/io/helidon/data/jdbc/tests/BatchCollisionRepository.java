/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc.tests;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.data.jdbc.JdbcClient;

/**
 * Exercises generated bindings with application names that also occur in generated code.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface BatchCollisionRepository {
    @Jdbc.Statement("INSERT INTO T VALUES (:SQL_INSERT, :jdbcClient, :SQL_INSERT, :statement, :rows, :statement)")
    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
    JdbcClient.BatchResult insert(List<String> SQL_INSERT, String jdbcClient, Iterator<Item> iterator);

    @Jdbc.Statement("INSERT INTO T VALUES (?, ?)")
    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
    JdbcClient.BatchResult array(int[] index, byte... element);

    record Item(String statement, Integer rows) {
        static final AtomicInteger ACCESSES = new AtomicInteger();

        @Override
        public String statement() {
            ACCESSES.incrementAndGet();
            if ("fail".equals(statement)) {
                throw new IllegalStateException("The record accessor failed.");
            }
            return statement;
        }
    }
}
