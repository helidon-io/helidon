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
package io.helidon.dbclient;

import java.lang.reflect.Method;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DbStatementsBuilderCompatibilityTest {

    @Test
    void testDbStatementsBuilderPublicApiShape() throws Exception {
        Method addStatement = DbStatements.Builder.class.getMethod("addStatement", String.class, String.class);
        Method config = DbStatements.Builder.class.getMethod("config", Config.class);
        Method build = DbStatements.Builder.class.getMethod("build");

        assertThat(addStatement.getReturnType().getName(), is(DbStatements.Builder.class.getName()));
        assertThat(config.getReturnType().getName(), is(DbStatements.Builder.class.getName()));
        assertThat(build.getReturnType().getName(), is(DbStatements.class.getName()));
    }

    @Test
    void testDbStatementsBuilderBehavior() {
        DbStatements statements = DbStatements.builder()
                .addStatement("select-one", "SELECT 1")
                .config(Config.builder()
                                .addSource(ConfigSources.create(Map.of("select-two", "SELECT 2")))
                                .build())
                .build();

        assertThat(statements.statement("select-one"), is("SELECT 1"));
        assertThat(statements.statement("select-two"), is("SELECT 2"));
        assertThrows(DbClientException.class, () -> statements.statement("missing"));
    }
}
