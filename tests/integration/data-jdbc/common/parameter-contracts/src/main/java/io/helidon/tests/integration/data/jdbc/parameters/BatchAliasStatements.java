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
package io.helidon.tests.integration.data.jdbc.parameters;

import java.util.Iterator;
import java.util.List;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.data.jdbc.JdbcClient;

/**
 * Supplies batch aliases from a generic library compiled without Java parameter names.
 *
 * @param <T> The repository supplies the concrete scalar input type.
 */
public interface BatchAliasStatements<T> {
    /**
     * Repeats a nullable scalar constant for every source element.
     *
     * @param original The source supplies contact names.
     * @param constant The nullable email repeats for every item.
     * @return The result contains detached execution evidence.
     */
    @Jdbc.Statement("INSERT INTO CONTACT (NAME, EMAIL) VALUES (:rows, :email)")
    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
    JdbcClient.BatchResult insertAliased(@Data.Param("rows") List<T> original,
                                        @Data.Param("email") T constant);

    /**
     * Aligns scalar sources while reusing each identifier at two physical positions.
     *
     * @param original The source supplies replacement contact names.
     * @param constant The nullable email repeats for every item.
     * @param identifiers The source supplies contact identifiers.
     * @return The result contains detached execution evidence.
     */
    @Jdbc.Statement("UPDATE CONTACT SET NAME = :rows, EMAIL = :email WHERE ID = :ids OR ID = :ids")
    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
    JdbcClient.BatchResult alignedAliases(@Data.Param("rows") Iterator<T> original,
                                         @Data.Param("email") T constant,
                                         @Data.Param("ids") long... identifiers);
}
