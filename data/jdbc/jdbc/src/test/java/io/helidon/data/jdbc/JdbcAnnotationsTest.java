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
package io.helidon.data.jdbc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcAnnotationsTest {

    @Test
    void exposesOnlyTheScopedRepositoryAnnotations() {
        Set<String> nestedTypes = Set.of(Jdbc.class.getDeclaredClasses())
                .stream()
                .map(Class::getSimpleName)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(nestedTypes,
                   is(Set.of("Statement", "Execution", "ExecutionType", "GeneratedKeys", "RowMapper")));
        assertThat(Set.of(Jdbc.ExecutionType.values()),
                   is(Set.of(Jdbc.ExecutionType.AUTO, Jdbc.ExecutionType.QUERY, Jdbc.ExecutionType.UPDATE)));
    }

    @Test
    void keepsRepositoryAnnotationsInSourceOnly() {
        assertThat(Jdbc.Statement.class.getAnnotation(Retention.class).value(), is(RetentionPolicy.SOURCE));
        assertThat(Jdbc.Execution.class.getAnnotation(Retention.class).value(), is(RetentionPolicy.SOURCE));
        assertThat(Jdbc.GeneratedKeys.class.getAnnotation(Retention.class).value(), is(RetentionPolicy.SOURCE));
        assertThat(Jdbc.RowMapper.class.getAnnotation(Retention.class).value(), is(RetentionPolicy.SOURCE));
        assertThat(Set.of(Jdbc.Statement.class.getAnnotation(Target.class).value()), is(Set.of(ElementType.METHOD)));
        assertThat(Set.of(Jdbc.Execution.class.getAnnotation(Target.class).value()), is(Set.of(ElementType.METHOD)));
        assertThat(Set.of(Jdbc.GeneratedKeys.class.getAnnotation(Target.class).value()), is(Set.of(ElementType.METHOD)));
        assertThat(Set.of(Jdbc.RowMapper.class.getAnnotation(Target.class).value()), is(Set.of(ElementType.METHOD)));
    }

    @Test
    void keepsAnnotationMembersAndDefaultsStable() throws Exception {
        assertThat(Jdbc.Statement.class.getDeclaredMethods().length, is(1));
        assertThat(Jdbc.Statement.class.getDeclaredMethod("value").getReturnType(), is((Object) String.class));
        assertThat(Jdbc.GeneratedKeys.class.getDeclaredMethods().length, is(1));
        assertThat(Jdbc.GeneratedKeys.class.getDeclaredMethod("value").getReturnType(), is((Object) String[].class));
        assertThat(Jdbc.GeneratedKeys.class.getDeclaredMethod("value").getDefaultValue(), is((Object) new String[0]));
        assertThat(Jdbc.Execution.class.getDeclaredMethods().length, is(1));
        assertThat(Jdbc.Execution.class.getDeclaredMethod("value").getDefaultValue(),
                   is(Jdbc.ExecutionType.AUTO));
        assertThat(Jdbc.RowMapper.class.getDeclaredMethods().length, is(1));
        assertThat(Jdbc.RowMapper.class.getDeclaredMethod("value").getDefaultValue(), is((Object) Void.class));
    }
}
