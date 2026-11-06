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

import java.util.List;
import java.util.Optional;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;

/**
 * Compiles configured logical names independently of deployment SQL.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface ConfiguredBindingRepository extends ConfiguredBindingParent<String> {

    @Jdbc.Statement("${application.sql.find}")
    List<String> find(String name, String email);

    @Jdbc.Statement("""
            ${application.sql.defaulted:select :value /* } 'quote'
            retained lines */}""")
    String defaulted(String value);

    @Jdbc.Statement("${application.sql.keys}")
    @Jdbc.GeneratedKeys("ID")
    Optional<Long> insert(String value, String data__value);

    @Jdbc.Statement("${application.sql.collision}")
    String collision(String SQL_COLLISION, String jdbcClient, String statement, String result, String rows,
                     String config, String inputNames);
}

/**
 * Exercises inherited generic results with configured selection.
 *
 * @param <T> The mapped result type.
 */
interface ConfiguredBindingParent<T> {

    @Jdbc.Statement("${application.sql.inherited}")
    T inherited(String value);
}

/**
 * Exercises configured aliases independently of positional repository contracts.
 */
@Data.Repository
@Data.Provider("jdbc")
interface ConfiguredAliasBindingRepository extends ConfiguredAliasBindingParent<String> {

    @Jdbc.Statement("${application.aliases.find}")
    List<String> find(@Data.Param("lookup") String jdbcClient, @Data.Param("email") String SQL_FIND);

    @Jdbc.Statement("${application.aliases.update}")
    void update(@Data.Param("SQL_UPDATE") String statement, String tag);

    @Jdbc.Statement("${application.aliases.keys}")
    @Jdbc.GeneratedKeys("ID")
    Optional<Long> insert(@Data.Param("name") String rows);
}

/**
 * Supplies an inherited generic alias with a known named default.
 *
 * @param <T> The repository resolves the scalar input type.
 */
interface ConfiguredAliasBindingParent<T> {

    @Jdbc.Statement("${application.aliases.inherited:select :lookup}")
    T inherited(@Data.Param("lookup") T value);
}
