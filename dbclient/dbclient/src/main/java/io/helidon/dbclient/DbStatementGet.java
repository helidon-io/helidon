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
package io.helidon.dbclient;

import java.util.Optional;

/**
 * Database statement that queries the database and returns an optional row.
 */
public interface DbStatementGet extends DbStatement<DbStatementGet> {

    /**
     * Execute this statement using the parameters configured with {@code params} and {@code addParams} methods.
     *
     * @return The result of this statement.
     */
    Optional<DbRow> execute();
}
