/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.dbclient.blocking;

import java.util.Collection;

import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementQuery;

/**
 * Database query statement.
 */
public interface BlockingDbStatementQuery extends BlockingDbStatement<BlockingDbStatementQuery, Collection<DbRow>> {

    /**
     * Create Blocking DbStatementQuery wrapper.
     *
     * @param dbStatementQuery
     * @return Blocking DbStatementQuery
     */
    static BlockingDbStatementQuery create(DbStatementQuery dbStatementQuery) {
        return new BlockingDbStatementQueryImpl(dbStatementQuery);
    }
}
