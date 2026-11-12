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

import java.sql.JDBCType;
import java.util.List;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;

@Data.Repository
@Data.Provider("jdbc")
interface CallableMapperCollisionRepository {
    @Jdbc.Statement("SELECT :input")
    Other aggregate_OUTPUT_0(String input);

    @Jdbc.Statement("{call routine(:MAPPER_AGGREGATE_OUTPUT_0_2, :rows, :count)}")
    @Jdbc.OutParameter(value = "rows", type = JDBCType.REF_CURSOR)
    @Jdbc.OutParameter(value = "count", type = JDBCType.INTEGER)
    Outputs aggregate(String MAPPER_AGGREGATE_OUTPUT_0_2);

    @Jdbc.Statement("{call routine(:MAPPER_AGGREGATE_OUTPUT_0_2, :rows, :count)}")
    @Jdbc.OutParameter(value = "rows", type = JDBCType.REF_CURSOR)
    @Jdbc.OutParameter(value = "count", type = JDBCType.INTEGER)
    Outputs reused(String MAPPER_AGGREGATE_OUTPUT_0_2);

    record Outputs(List<Projection> rows, int count) {
    }

    record Projection(int id) {
    }

    record Other(String value) {
    }
}
