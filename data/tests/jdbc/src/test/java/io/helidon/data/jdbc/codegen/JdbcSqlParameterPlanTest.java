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
package io.helidon.data.jdbc.codegen;

import java.util.ArrayList;
import java.util.List;

import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcSqlParameterPlanTest {

    /**
     * Verifies construction snapshots the ordered bindings and exposes an unmodifiable list.
     */
    @Test
    void snapshotsBindingsAtConstruction() {
        TypedElementInfo parameter = TypedElementInfo.builder()
                .kind(ElementKind.PARAMETER)
                .elementName("value")
                .typeName(TypeNames.STRING)
                .build();
        JdbcSqlParameterPlan.Bind bind = new JdbcSqlParameterPlan.Bind(1, parameter, true, "VARCHAR");
        List<JdbcSqlParameterPlan.Bind> source = new ArrayList<>();
        source.add(bind);

        JdbcSqlParameterPlan plan = new JdbcSqlParameterPlan("SELECT ?", source);
        source.clear();

        assertThat(plan.binds(), is(List.of(bind)));
        assertThrows(UnsupportedOperationException.class, () -> plan.binds().clear());
    }

    /**
     * Verifies that a supplementary Java parameter name matches its named SQL
     * marker and produces one positional JDBC binding.
     */
    @Test
    void matchesSupplementaryJavaParameterName() {
        String parameterName = Character.toString(0x10400) + "name";
        TypedElementInfo parameter = TypedElementInfo.builder()
                .kind(ElementKind.PARAMETER)
                .elementName(parameterName)
                .typeName(TypeNames.STRING)
                .build();

        JdbcSqlParameterPlan plan = JdbcSqlParameterPlan.create(
                "UPDATE TEST_VALUE SET VALUE = :" + parameterName,
                List.of(parameter),
                parameter);

        assertThat(plan.sql(), is("UPDATE TEST_VALUE SET VALUE = ?"));
        assertThat(plan.parameterCount(), is(1));
        assertThat(plan.binds().getFirst().parameter(), is(parameter));
    }
}
