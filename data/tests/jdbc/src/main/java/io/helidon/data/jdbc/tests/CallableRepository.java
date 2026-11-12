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
import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.data.jdbc.JdbcClient;

@Data.Repository
@Data.Provider("jdbc")
interface CallableRepository extends CallableParent<Integer> {
    @Jdbc.Statement("{? = call routine(:jdbcClient)}")
    @Jdbc.OutParameter(type = JDBCType.INTEGER)
    int scalar(Integer jdbcClient);

    @Jdbc.Statement("{? = call routine(?)}")
    @Jdbc.OutParameter(type = JDBCType.VARCHAR)
    String positional(int value);

    @Jdbc.Statement("{call routine(:SQL_SNAPSHOT, :value)}")
    @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER)
    JdbcClient.OutParameters snapshot(Integer SQL_SNAPSHOT);

    @Jdbc.Statement("{call routine(:statement, :value)}")
    @Jdbc.OutParameter(value = "value", type = JDBCType.REF_CURSOR)
    Optional<List<String>> cursor(String statement);

    @Jdbc.Statement("{call routine(:result)}")
    @Jdbc.Execution(Jdbc.ExecutionType.CALL)
    void consume(String result, Consumer<JdbcClient.CallResult> rows);

    @Jdbc.Statement("{call routine(:value)}")
    @Jdbc.Execution(Jdbc.ExecutionType.CALL)
    String mapped(String value, JdbcClient.CallMapper<String> result);

    @Jdbc.Statement("{call routine(:rows)}")
    @Jdbc.Execution(Jdbc.ExecutionType.CALL)
    Optional<String> direct(String rows);

    @Jdbc.Statement("{call routine(:value, :rows, :result)}")
    @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER)
    @Jdbc.OutParameter(value = "rows", type = JDBCType.REF_CURSOR)
    @Jdbc.OutParameter(value = "result", type = JDBCType.VARCHAR)
    Outputs aggregate(Integer value);

    record Outputs(int value,
                    List<Projection> rows,
                    Optional<String> result) {
    }

    record Projection(int id) {
    }
}

interface CallableParent<T> {
    @Jdbc.Statement("{call routine(:value)}")
    @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER)
    T inherited(T value);
}
